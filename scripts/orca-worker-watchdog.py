#!/usr/bin/env python3
"""Orca 워커와 OpenCode 하위 에이전트 감시, 코디네이터 알림을 단일 프로세스로 수행한다.

코디네이터(Claude)가 점검마다 워커 터미널 원문을 읽으면 회당 수천 토큰이 든다.
이 스크립트가 대신 읽고 한 줄로 줄여주면 코디네이터는 상태 파일만 보면 된다.

재배정은 하지 않는다: 에러 문구가 떠도 OpenCode가 스스로 회복하는 경우가 있어,
오진으로 작업 중인 워커를 죽이는 비용이 자동 재배정의 이득보다 크다.

사용:
  scripts/orca-worker-watchdog.py --start --loop 30 --run <run_id> --coordinator-terminal <handle>
  scripts/orca-worker-watchdog.py --status
  scripts/orca-worker-watchdog.py --stop
  scripts/orca-worker-watchdog.py --selftest

종료 코드: 0=모두 정상, 1=문제 있는 워커 있음
"""

from __future__ import annotations

import argparse
import fcntl
import hashlib
import json
import os
import re
import signal
import sqlite3
import subprocess
import sys
import tempfile
import time
from urllib.parse import quote

# 디스패치별 "esc interrupt" 부재 추적: dispatch_id -> 처음 관측된 타임스탬프(초)
_esc_interrupt_missing_since: dict[str, float] = {}
ALERT_STATUSES = {"ERROR", "STALL", "PERM", "UNKNOWN", "REPORT_MISSING", "REVIEW", "FAILED"}
ACTIVE_STATES = {"starting", "dispatched", "ready", "running", "in_progress"}
EVENT_SOURCE = "orca-worker-watchdog"

# 2026-09-28 실제로 워커를 멈춰 세운 문구들에서 뽑았다.
ERROR_PATTERNS = re.compile(
    r"rate[- ]limited|Upstream idle timeout|Request rejected|\b429\b"
    r"|quota exceeded|insufficient credits|API Error"
    r"|Endpoint is unavailable|Upstream request failed"
    r"|Free usage exceeded|free-models-per-day-high-balance"
    r"|free usage limit|usage limit (?:reached|exceeded)|no upstreams available"
    r"|content items of type 'thinking' must be followed"
    r'|"code":\s*50[0-4]|\[50[0-4]\]|Timeout Occurred|Streaming response failed|server_error'
    r"|OpenCode stopped while this response was in progress"
    r"|stopped while this response was in progress",
    re.IGNORECASE,
)
# OpenCode 권한 질문: "Permission required" + "Allow once"/"Allow always"/"Reject" 선택 줄
PERM_PATTERN = re.compile(
    r"Permission required.*?(Allow once|Allow always|Reject)",
    re.IGNORECASE | re.DOTALL,
)
# OpenCode 상태줄: "Build · Laguna S 2.1 (free) OpenRouter" — 지정 모델과 실제 모델 대조용.
MODEL_LINE = re.compile(r"Build\s*·\s*(.+?)(?:\s*\(free\))?(?:\s+OpenRouter)?\s*$", re.MULTILINE)
# NVIDIA 도구 오류: "Function ... Not found for account ..." (대소문자/줄 포장 견딤)
# 실제 function/account ID는 절대 상태 출력에 포함하지 않도록 별도 탐지 사용.
NVIDIA_FUNC_ERROR = re.compile(
    r"Function\b[\s\S]*?\bNot\s+found\s+for\s+account",
    re.IGNORECASE,
)


def orca_call(*args: str) -> tuple[dict | None, str | None]:
    """orca CLI를 JSON으로 호출하고 실패 원인을 보존한다."""
    try:
        proc = subprocess.run(["orca", *args], capture_output=True, text=True, timeout=30)
    except (OSError, subprocess.TimeoutExpired) as exc:
        return None, str(exc)
    try:
        payload = json.loads(proc.stdout)
    except json.JSONDecodeError as exc:
        return None, f"invalid JSON: {exc}"
    if not payload.get("ok"):
        error = payload.get("error") or {}
        if not isinstance(error, dict):
            return None, str(error)
        return None, str(error.get("message") or error.get("code") or "orca command failed")
    return payload.get("result") or {}, None


def orca(*args: str) -> dict | None:
    """기존 호출을 위한 얇은 래퍼."""
    result, _ = orca_call(*args)
    return result


def get_repo_id() -> str | None:
    """현재 git 저장소의 repoId를 얻는다. 실패하면 None."""
    script_dir = os.path.dirname(os.path.abspath(__file__))
    repo_root = subprocess.run(
        ["git", "-C", script_dir, "rev-parse", "--show-toplevel"],
        capture_output=True, text=True, timeout=10
    )
    if repo_root.returncode != 0:
        return None
    toplevel = repo_root.stdout.strip()
    res = orca("worktree", "show", "--worktree", f"path:{toplevel}", "--json")
    if not res:
        return None
    worktree = res.get("worktree") or {}
    return worktree.get("repoId")


def filter_workers_by_repo(workers: list[dict], repo_id: str | None, tree_filter: str | None) -> list[tuple[str, str]]:
    """워커 목록을 repoId 접두사 및/또는 수동 필터로 거른다."""
    rows = []
    for worker in workers:
        tree = worker.get("workspace") or (worker.get("resource") or {}).get("worktreeId", "")
        # Orca worker-list의 현재 projection은 dispatchStatus 대신 state/outcome을
        # 반환한다. state=ready + outcome=in_progress인 OpenCode 워커도 실제 실행 중이다.
        state = str(worker.get("state") or "").lower()
        outcome = str(worker.get("outcome") or "").lower()
        terminal_state = str(worker.get("terminalState") or "").lower()
        active = (
            worker.get("dispatchStatus") == "dispatched"  # 구버전 호환
            or state in ACTIVE_STATES
            or outcome == "in_progress"
        )
        if state in {"succeeded", "failed", "abandoned", "released"}:
            active = False
        if not active or terminal_state in {"released", "closed"}:
            continue
        if repo_id:
            # 기본: 이 repo의 모든 worktree (repoId:: 접두사)
            if not tree.startswith(f"{repo_id}::"):
                continue
            # 수동 필터도 주어지면 추가로 적용 (AND)
            if tree_filter and tree_filter not in tree:
                continue
        elif tree_filter:
            # 폴백: repoId 없을 때만 문자열 포함 필터
            if tree_filter not in tree:
                continue
        else:
            continue
        rows.append((worker["dispatchId"], tree.rsplit("/", 1)[-1]))
    return rows


def classify(tail: str, idle_min: float, stall_min: float, dispatch_id: str | None = None) -> tuple[str, str]:
    """터미널 꼬리 텍스트와 무출력 시간으로 상태를 판정한다."""
    # 권한 질문 우선 (사람이 처리해야 하므로 ERROR보다 높게)
    perm_hit = PERM_PATTERN.search(tail)
    if perm_hit:
        if dispatch_id and dispatch_id in _esc_interrupt_missing_since:
            del _esc_interrupt_missing_since[dispatch_id]
        # 질문 대상 한 줄 추출 (Permission required 앞뒤)
        lines = tail.splitlines()
        perm_line = ""
        for i, line in enumerate(lines):
            if "Permission required" in line:
                # 현재 줄과 다음 줄(선택지) 포함
                perm_line = line.strip()
                if i + 1 < len(lines):
                    perm_line += " ← " + lines[i + 1].strip()
                break
        return "PERM", f"권한 질문: {perm_line}" if perm_line else "권한 질문"

    # NVIDIA Function/Account 오류 우선 탐지 (식별자 포함하지 않는 고정 분류 코드 사용)
    nvidia_hit = NVIDIA_FUNC_ERROR.search(tail)
    if nvidia_hit:
        if dispatch_id and dispatch_id in _esc_interrupt_missing_since:
            del _esc_interrupt_missing_since[dispatch_id]
        return "ERROR", "분류=NVIDIA_FUNC_NOT_FOUND"

    hit = ERROR_PATTERNS.search(tail)
    if hit:
        # 에러가 감지되면 추적 상태 초기화
        if dispatch_id and dispatch_id in _esc_interrupt_missing_since:
            del _esc_interrupt_missing_since[dispatch_id]
        return "ERROR", f"문구={hit.group(0)!r}"

    has_esc_interrupt = "esc interrupt" in tail.lower()

    if dispatch_id is not None:
        if not has_esc_interrupt:
            # 처음으로 "esc interrupt"가 없는 상태를 관측했다면 timestamps 기록
            if dispatch_id not in _esc_interrupt_missing_since:
                _esc_interrupt_missing_since[dispatch_id] = time.time()
            # 지속 시간을 확인
            missing_since = _esc_interrupt_missing_since[dispatch_id]
            missing_min = (time.time() - missing_since) / 60
            # OpenCode의 응답 종료 표시는 완료 보고와 다르다. 상태를 자동
            # 정산하지 않고, 2회 관측 후 코디네이터에게 보고 누락 확인을 요청한다.
            if re.search(r"▣\s*Build\s*·", tail) and missing_min >= 0.5:
                return "REPORT_MISSING", "응답 종료 화면 관측, worker_done/검증 확인 필요"
            if missing_min >= stall_min and idle_min < stall_min:
                return "UNKNOWN", "출력은 있으나 실행 표시 없음; 실제 작업 상태 확인 필요"
        else:
            # "esc interrupt"가 다시 보이면 추적 상태 초기화
            if dispatch_id in _esc_interrupt_missing_since:
                del _esc_interrupt_missing_since[dispatch_id]

    if idle_min >= stall_min:
        return "STALL", f"{idle_min:.0f}분 무출력"
    return "OK", ""


def active_dispatches(
    repo_id: str | None, tree_filter: str | None, run_id: str | None = None
) -> list[tuple[str, str]]:
    # watchdog는 독립 터미널에서 실행되므로 그 터미널에 묶인 과거 Run을
    # 암묵적으로 조회하면 현재 코디네이터 Run의 워커를 놓친다. 실행 인자로
    # 받은 Run을 명시해 조회 범위를 코디네이터와 일치시킨다.
    args = ["orchestration", "worker-list", "--json", "--limit", "100"]
    if run_id:
        args.extend(["--run", run_id])
    res = orca(*args)
    if not res:
        return []
    workers = res.get("workers", [])
    return filter_workers_by_repo(workers, repo_id, tree_filter)


def opencode_terminals(repo_id: str | None, tree_filter: str | None) -> tuple[list[dict], str | None]:
    """현재 저장소에서 Orca dispatch에 묶이지 않은 OpenCode 터미널도 찾는다."""
    if not repo_id and not tree_filter:
        return [], "저장소 범위 미확인"
    result, error = orca_call("terminal", "list", "--limit", "100", "--json")
    if error:
        return [], "터미널 목록 조회 실패"
    terminals = []
    for terminal in (result or {}).get("terminals", []):
        if str(terminal.get("agentIdentity") or "").lower() != "opencode":
            continue
        worktree_id = str(terminal.get("worktreeId") or "")
        if not terminal.get("worktreePath"):
            continue
        if repo_id and not worktree_id.startswith(f"{repo_id}::"):
            continue
        if tree_filter and tree_filter not in worktree_id:
            continue
        terminals.append(terminal)
    return terminals, None


def _json_text(value: object) -> str:
    """JSON/text payload를 에러 분류용 문자열로 바꾼다. 원문은 상태 파일에 쓰지 않는다."""
    if isinstance(value, str):
        try:
            decoded = json.loads(value)
        except json.JSONDecodeError:
            return value
        return _json_text(decoded)
    if isinstance(value, dict):
        return " ".join(_json_text(item) for item in value.values())
    if isinstance(value, list):
        return " ".join(_json_text(item) for item in value)
    return str(value) if value is not None else ""


def inspect_opencode_children(
    workspaces: set[str], stall_min: float, db_path: str | None = None,
    now_ms: int | None = None,
) -> tuple[list[tuple[str, str]], str | None]:
    """OpenCode DB의 parent_id 세션 트리를 읽기 전용으로 검사한다.

    Orca worker-list는 OpenCode 내부 하위 세션을 반환하지 않는다. OpenCode의
    session.parent_id 관계를 따라가 자식 세션의 최근 메시지/도구 결과를 살핀다.
    최근 1시간 내 변경된 하위 세션만 검사해 과거 기록의 오탐을 제한한다.
    """
    if not workspaces:
        return [], None
    if db_path is None:
        try:
            path_result = subprocess.run(
                ["opencode", "db", "path"], capture_output=True, text=True, timeout=10
            )
        except (OSError, subprocess.TimeoutExpired) as exc:
            return [], f"OpenCode DB 경로 조회 실패: {exc}"
        if path_result.returncode != 0 or not path_result.stdout.strip():
            return [], "OpenCode DB 경로 조회 실패"
        db_path = path_result.stdout.strip().splitlines()[-1]

    uri = f"file:{quote(os.path.abspath(db_path), safe='/')}?mode=ro"
    try:
        connection = sqlite3.connect(uri, uri=True, timeout=2)
        connection.row_factory = sqlite3.Row
        try:
            table_names = {
                row[0] for row in connection.execute(
                    "SELECT name FROM sqlite_master WHERE type='table'"
                )
            }
            if not {"session", "message", "part"}.issubset(table_names):
                return [], "OpenCode DB 스키마에서 session/message/part 테이블을 찾지 못함"
            sessions = connection.execute(
                "SELECT id, parent_id, title, directory, time_updated, agent, model "
                "FROM session"
            ).fetchall()
            by_id = {row["id"]: row for row in sessions}
            children: dict[str, list[sqlite3.Row]] = {}
            for row in sessions:
                if row["parent_id"]:
                    children.setdefault(row["parent_id"], []).append(row)

            roots = {
                row["id"] for row in sessions
                if row["parent_id"] is None
                and os.path.realpath(row["directory"] or "") in workspaces
            }
            descendants: dict[str, str] = {}
            queue = [(root_id, root_id) for root_id in roots]
            while queue:
                parent_id, root_id = queue.pop()
                for child in children.get(parent_id, []):
                    if child["id"] in descendants:
                        continue
                    descendants[child["id"]] = root_id
                    queue.append((child["id"], root_id))

            now_ms = now_ms if now_ms is not None else int(time.time() * 1000)
            lookback_ms = 60 * 60 * 1000
            rows: list[tuple[str, str]] = []
            for child_id, root_id in descendants.items():
                child = by_id[child_id]
                updated_ms = int(child["time_updated"] or 0)
                if updated_ms < now_ms - lookback_ms:
                    continue
                idle_min = max(0, (now_ms - updated_ms) / 60000)
                messages = connection.execute(
                    "SELECT id, data FROM message WHERE session_id=? ORDER BY time_created DESC LIMIT 12",
                    (child_id,),
                ).fetchall()
                last_assistant = {}
                latest_id = None
                for message_row in messages:
                    try:
                        message_data = json.loads(message_row["data"])
                    except (TypeError, json.JSONDecodeError):
                        continue
                    if isinstance(message_data, dict) and message_data.get("role") == "assistant":
                        last_assistant, latest_id = message_data, message_row["id"]
                        break
                finish = str(last_assistant.get("finish") or "").lower()
                completed = (last_assistant.get("time") or {}).get("completed")
                if finish in {"stop", "end_turn"} and completed and not last_assistant.get("error"):
                    continue
                parts = connection.execute(
                    "SELECT data FROM part WHERE message_id=? ORDER BY time_created DESC LIMIT 40",
                    (latest_id,),
                ).fetchall() if latest_id else []
                payloads = [row["data"] for row in parts] + [json.dumps(last_assistant)]
                text = "\n".join(_json_text(payload) for payload in payloads)

                tool_failure = False
                for payload in payloads:
                    try:
                        record = json.loads(payload)
                    except (TypeError, json.JSONDecodeError):
                        continue
                    state = record.get("state") if isinstance(record, dict) else None
                    if isinstance(record, dict) and record.get("type") == "tool" and isinstance(state, dict):
                        if str(state.get("status") or "").lower() in {"error", "failed"}:
                            tool_failure = True
                            break
                    if isinstance(record, dict) and record.get("error"):
                        tool_failure = True
                        break

                child_label = f"{child_id}"
                parent_label = f"parent={root_id}"
                model = "?"
                try:
                    model_data = json.loads(child["model"] or "{}")
                    model = str(model_data.get("id") or "?")
                except (TypeError, json.JSONDecodeError):
                    pass
                if tool_failure:
                    status, reason = "ERROR", "하위 에이전트 도구 실행 실패"
                elif ERROR_PATTERNS.search(text) or NVIDIA_FUNC_ERROR.search(text):
                    status, reason = "ERROR", "하위 에이전트 응답 중단/업스트림 오류"
                else:
                    if idle_min >= stall_min:
                        status, reason = "STALL", f"하위 세션 {idle_min:.0f}분 무출력, 완료 표시 없음"
                    else:
                        status, reason = "OK", "하위 세션 실행 중"
                rows.append((status, f"CHILD {child_label} {parent_label} model={model} idle={idle_min:.0f}m {reason}"))
            return rows, None
        finally:
            connection.close()
    except (OSError, sqlite3.Error) as exc:
        return [], f"OpenCode DB 읽기 실패: {exc}"


def inspect_unassigned_opencode_terminals(
    terminals: list[dict], active_handles: set[str], now_ms: int | None = None,
) -> list[tuple[str, str]]:
    """worker-list에 안 잡힌 OpenCode 하위 터미널을 오류 문구 중심으로 검사한다."""
    now_ms = now_ms if now_ms is not None else int(time.time() * 1000)
    rows: list[tuple[str, str]] = []
    for terminal in terminals:
        handle = str(terminal.get("handle") or "")
        if not handle or handle in active_handles:
            continue
        last_ms = int(terminal.get("lastOutputAt") or 0)
        # live terminal inventory는 retained 터미널도 포함한다. 오래된 화면의 과거 에러는 제외한다.
        if not last_ms or now_ms - last_ms > 60 * 60 * 1000:
            continue
        result = orca("terminal", "read", "--terminal", handle, "--screen", "--limit", "80", "--json") or {}
        tail = "\n".join(((result.get("terminal") or {}).get("tail") or []))
        if PERM_PATTERN.search(tail):
            status, reason = "PERM", "OpenCode 하위 터미널 권한 확인 대기"
        elif NVIDIA_FUNC_ERROR.search(tail) or ERROR_PATTERNS.search(tail):
            status, reason = "ERROR", "OpenCode 하위 터미널 오류/응답 중단"
        else:
            status, reason = "OK", "OpenCode 하위 터미널 확인됨"
        rows.append((status, f"OPENCODE_TERM {handle} {reason}"))
    return rows


def inspect(dispatch: str, stall_min: float) -> tuple[str, str, str | None]:
    res = orca("orchestration", "worker-show", "--dispatch", dispatch, "--json")
    terminal_value = (res or {}).get("terminal")
    if isinstance(terminal_value, str):
        # worker-show now returns the terminal handle as a string. Resolve the
        # terminal metadata separately to get lastOutputAt.
        handle = terminal_value
        terminal_meta = orca("terminal", "show", "--terminal", handle, "--json") or {}
        terminal_meta = terminal_meta.get("terminal") or {}
    else:
        terminal_meta = terminal_value or {}
        handle = terminal_meta.get("handle")
    if not handle:
        return "UNKNOWN", "터미널 핸들 없음 (구조화 워커/실행 정보 확인 필요)", None
    last_ms = terminal_meta.get("lastOutputAt") or 0
    idle_min = (time.time() * 1000 - last_ms) / 60000 if last_ms else 0.0

    read, read_error = orca_call("terminal", "read", "--terminal", handle, "--screen", "--limit", "80", "--json")
    frame = ((read or {}).get("terminal") or {})
    if read_error or frame.get("source") == "screen-unavailable":
        return "UNKNOWN", f"handle={handle} 현재 화면 조회 실패", handle
    tail = "\n".join(frame.get("tail", []))
    if not tail.strip():
        return "UNKNOWN", f"handle={handle} 현재 화면이 비어 있음", handle
    models = MODEL_LINE.findall(tail)
    # 상태줄은 한 줄에 모델명과 브랜치명이 공백 여러 칸으로 나뉘어 같이 찍힌다.
    model = re.split(r"\s{2,}", models[-1].strip())[0] if models else "?"

    status, why = classify(tail, idle_min, stall_min, dispatch)
    detail = f"model={model} idle={idle_min:.0f}m handle={handle}"
    return status, f"{detail} {why}".strip(), handle


def lifecycle_rows(workers: list[dict], state: dict) -> list[str]:
    """이미 본 활성 Dispatch가 정산되면 검증 필요 이벤트를 한 번 만든다."""
    previous = state.setdefault("dispatches", {})
    initialized = state.get("dispatches_initialized", False)
    rows = []
    for worker in workers:
        dispatch = worker.get("dispatchId")
        if not dispatch:
            continue
        current = str(worker.get("state") or "")
        before = previous.get(dispatch)
        if current in {"succeeded", "failed"} and current != before:
            # 첫 기동 때의 과거 완료 목록은 알림 폭주를 만들지 않는다.
            # 재기동 때 저장돼 있던 active -> settled 전이는 그대로 복구한다.
            if initialized or before in ACTIVE_STATES:
                tree = str(worker.get("workspace") or "").rsplit("/", 1)[-1]
                status = "REVIEW" if current == "succeeded" else "FAILED"
                rows.append(f"{status:7} {tree} {dispatch} task={worker.get('taskId', '?')} Orca 정산; 코디네이터 검증/처리 필요")
        previous[dispatch] = current
    state["dispatches_initialized"] = True
    return rows


def atomic_write(path: str, body: str) -> None:
    directory = os.path.dirname(os.path.abspath(path))
    with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", dir=directory, delete=False) as handle:
        temporary = handle.name
        handle.write(body)
    os.replace(temporary, path)


def run_once(
    repo_id: str | None,
    tree_filter: str | None,
    stall_min: float,
    status_path: str,
    warn_msg: str | None = None,
    run_id: str | None = None,
    observation_state: dict | None = None,
) -> int:
    stamp = time.strftime("%Y-%m-%d %H:%M:%S")
    lines = [f"# {stamp} 점검"]
    if warn_msg:
        lines[0] += f" ⚠ {warn_msg}"
    problems = 0
    active_handles: set[str] = set()
    args = ["orchestration", "worker-list", "--json", "--limit", "100"]
    if run_id:
        args.extend(["--run", run_id])
    snapshot, snapshot_error = orca_call(*args)
    workers = (snapshot or {}).get("workers", [])
    scoped = [worker for worker in workers if (
        (repo_id and str(worker.get("workspace") or "").startswith(f"{repo_id}::"))
        or (not repo_id and tree_filter and tree_filter in str(worker.get("workspace") or ""))
    ) and (not tree_filter or tree_filter in str(worker.get("workspace") or ""))]
    if snapshot_error:
        lines.append("UNKNOWN ORCA_INVENTORY worker-list 워커 목록 조회 실패")
        problems += 1
    elif observation_state is not None:
        lines.extend(lifecycle_rows(scoped, observation_state))
    for dispatch, tree in filter_workers_by_repo(scoped, repo_id, tree_filter):
        status, detail, handle = inspect(dispatch, stall_min)
        if handle:
            active_handles.add(handle)
        if status != "OK":
            problems += 1
        lines.append(f"{status:7} {tree:12} {dispatch} {detail}")
    terminals, terminal_error = opencode_terminals(repo_id, tree_filter)
    if terminal_error:
        lines.append(f"UNKNOWN TERMINAL_INVENTORY terminals {terminal_error}")
        problems += 1
    workspaces = {
        os.path.realpath(terminal["worktreePath"])
        for terminal in terminals if terminal.get("worktreePath")
    }
    child_rows, child_error = inspect_opencode_children(
        workspaces, stall_min
    )
    if child_error:
        lines.append(f"UNKNOWN CHILD_SCAN opencode {child_error}")
        problems += 1
    for status, detail in child_rows:
        if status != "OK":
            problems += 1
        lines.append(f"{status:7} {detail}")
    for status, detail in inspect_unassigned_opencode_terminals(terminals, active_handles):
        if status != "OK":
            problems += 1
        lines.append(f"{status:7} {detail}")
    if len(lines) == 1:
        lines.append("활성 워커 없음")
    body = "\n".join(lines)
    atomic_write(status_path, body + "\n")
    print(body, flush=True)
    return 1 if problems else 0


def read_last_lines(path: str, count: int = 5) -> list[str]:
    """상태 파일의 마지막 count줄만 읽는다."""
    try:
        with open(path, encoding="utf-8") as handle:
            return handle.read().splitlines()[-count:]
    except OSError:
        return []


def collect_status_events(lines: list[str], previous: dict[str, str]) -> list[str]:
    """모든 행의 상태 변화를 읽는다. 요약 표시 행 수로 알림 범위를 제한하지 않는다."""
    events: list[str] = []
    present = set()
    for line in lines:
        fields = line.split()
        if len(fields) < 3:
            continue
        status, dispatch = fields[0], fields[2]
        present.add(dispatch)
        if status == "OK":
            previous.pop(dispatch, None)
        elif status in ALERT_STATUSES:
            if previous.get(dispatch) != status:
                events.append(line)
            previous[dispatch] = status
    for dispatch in set(previous) - present:
        previous.pop(dispatch, None)
    return events


def collect_new_messages(messages: list[dict], seen_ids: list[str]) -> list[dict]:
    """이미 알린 Orca message id는 건너뛴다."""
    seen = set(seen_ids)
    fresh = [message for message in messages if message.get("id") and message["id"] not in seen]
    seen_ids.extend(message["id"] for message in fresh)
    del seen_ids[:-1000]
    return fresh


def load_notify_state(path: str) -> dict:
    try:
        with open(path, encoding="utf-8") as handle:
            state = json.load(handle)
        if isinstance(state, dict):
            state.setdefault("watchdog", {})
            state.setdefault("messages", [])
            return state
    except (OSError, json.JSONDecodeError):
        pass
    return {"watchdog": {}, "messages": []}


def save_notify_state(path: str, state: dict) -> None:
    atomic_write(path, json.dumps(state, ensure_ascii=False))


def notify_user(title: str, message: str) -> None:
    """macOS 알림을 띄우고, 알림을 지원하지 않는 환경에서는 stdout만 사용한다."""
    print(f"[알림] {title}: {message}", flush=True)
    script = f"display notification {json.dumps(message, ensure_ascii=False)} with title {json.dumps(title, ensure_ascii=False)}"
    try:
        subprocess.run(
            ["osascript", "-e", script],
            capture_output=True,
            text=True,
            timeout=5,
            check=False,
        )
    except (OSError, subprocess.TimeoutExpired):
        pass


def relay_events(events: list[str], state: dict, run_id: str | None) -> None:
    """Orca Run으로 전달하고 성공한 사건만 큐에서 제거한다. worker_done은 만들지 않는다."""
    outbox = state.setdefault("outbox", {})
    for event in events:
        event_id = hashlib.sha256(event.encode()).hexdigest()[:20]
        outbox.setdefault(event_id, {"body": event, "attempts": 0, "next_attempt": 0})
    if not run_id:
        return
    now = time.time()
    due = {key: value for key, value in outbox.items() if value.get("next_attempt", 0) <= now}
    if not due:
        return
    # 상태 전문/워커 원문 대신 한 줄 사건만 보낸다. 여러 사건도 메시지 한 개로 묶는다.
    result, error = orca_call(
        "orchestration", "send", "--run", run_id, "--to", f"run:{run_id}",
        "--type", "status", "--priority", "high", "--subject", f"Watchdog: 확인 필요 {len(due)}건",
        "--body", "\n".join(value["body"] for value in due.values()),
        "--payload", json.dumps({"source": EVENT_SOURCE, "eventIds": list(due)}), "--json",
    )
    if result is not None and not error:
        for key in due:
            outbox.pop(key, None)
        state["last_delivery"] = {"at": now, "messageId": result.get("messageId")}
        state.pop("delivery_error", None)
    else:
        state["delivery_error"] = error or "Orca message delivery failed"
        for value in due.values():
            value["attempts"] += 1
            value["next_attempt"] = now + min(300, 30 * 2 ** min(value["attempts"] - 1, 4))
        print("[전달 실패] Orca 재전송 대기; outbox 보존", flush=True)


def notify_cycle(status_path: str, state_path: str, run_id: str | None, coordinator_terminal: str | None,
                 state: dict | None = None, check_inbox: bool = True) -> None:
    """상태 변화는 매 스캔 즉시 relay, inbox는 peek만 한다. 소비/ACK/작업 정산은 하지 않는다."""
    state = state if state is not None else load_notify_state(state_path)
    watchdog_state = state["watchdog"]
    status_events = collect_status_events(read_last_lines(status_path, sys.maxsize), watchdog_state)
    if status_events:
        for event in status_events:
            print(f"[워커 상태] {event}")
        notify_user("Orca 워커 알림", f"오류·보고 누락·검토 필요 {len(status_events)}건")

    if check_inbox and run_id and coordinator_terminal:
        result, error = orca_call(
            "orchestration",
            "check",
            "--run",
            run_id,
            "--terminal",
            coordinator_terminal,
            "--peek",
            "--types",
            "worker_done,question,escalation",
            "--json",
        )
        if error:
            if state.get("inbox_error") != error:
                notify_user("Orca inbox 연결 오류", "Orca inbox를 확인하지 못했습니다. 상태 로그를 확인하세요.")
                status_events.append("UNKNOWN ORCA_INBOX inbox 코디네이터 inbox 조회 실패")
            print(f"[inbox 오류] {error}")
            state["inbox_error"] = error
        else:
            state.pop("inbox_error", None)
            # watchdog 자신이 보낸 상태 알림은 재알림하지 않는다.
            messages = [message for message in (result or {}).get("messages", [])
                        if (message.get("payload") or {}).get("source") != EVENT_SOURCE]
            fresh = collect_new_messages(messages, state["messages"])
            if fresh:
                for message in fresh:
                    print(f"[Orca 메시지] {message.get('type', '?')}: {message.get('subject', '(제목 없음)')}")
                notify_user("Orca 작업 알림", f"새 완료·질문·오류 메시지 {len(fresh)}건이 있습니다.")

    relay_events(status_events, state, run_id)
    state["last_cycle_at"] = time.time()
    save_notify_state(state_path, state)


def selftest() -> int:
    # 기존 ERROR 패턴 테스트
    assert classify("[Poolside] ... temporarily rate-limited upstream", 1, 8, None)[0] == "ERROR"
    assert classify("Upstream idle timeout", 1, 8, None)[0] == "ERROR"
    assert classify("Endpoint is unavailable", 1, 8, None)[0] == "ERROR"
    assert classify("Upstream request failed", 1, 8, None)[0] == "ERROR"
    assert classify("Error from provider (Console): Upstream request failed: Endpoint is unavailable.", 1, 8, None)[0] == "ERROR"
    # 새로운 ERROR 패턴 테스트
    assert classify('{"code":504,"message":"A Timeout Occurred"...}', 1, 8, None)[0] == "ERROR"
    assert classify('Streaming response failed: [504] A Timeout Occurred', 1, 8, None)[0] == "ERROR"
    assert classify('"type":"server_error"', 1, 8, None)[0] == "ERROR"
    assert classify("OpenCode stopped while this response was in progress. You can continue in this conversation. (code 0)", 1, 8, None)[0] == "ERROR"
    assert classify("  ┃   500 foo", 1, 8, None)[0] == "OK"

    # 기존 STALL 테스트 (무출력 기반)
    assert classify("정상 작업 중", 9, 8, None)[0] == "STALL"
    assert classify("정상 작업 중", 2, 8, None)[0] == "OK"

    # 새로운 STALL 테스트 ('esc interrupt' 기반)
    global _esc_interrupt_missing_since
    _esc_interrupt_missing_since.clear()  # 테스트 전 상태 초기화

    # 처음에는 'esc interrupt'가 없어도 바로 STALL이 되지 않아야 함 (시간이 부족)
    assert classify("일하는 중", 1, 2, "test_dispatch")[0] == "OK"
    # 'esc interrupt'가 없는 상태가 지속되면 STALL이 되어야 함
    # 시간 조작을 위해 내부 상태를 직접 설정
    _esc_interrupt_missing_since["test_dispatch"] = time.time() - (2 * 60)  # 2분 전
    assert classify("일하는 중", 1, 2, "test_dispatch")[0] == "UNKNOWN"
    # 'esc interrupt'가 다시 보이면 추적이 초기화되어야 함
    assert classify("일하는 중 esc interrupt", 1, 2, "test_dispatch")[0] == "OK"
    # 다시 'esc interrupt'가 사라지면 추적이 시작되어야 함
    _esc_interrupt_missing_since["test_dispatch"] = time.time() - (1 * 60)  # 1분 전
    assert classify("일하는 중", 1, 2, "test_dispatch")[0] == "OK"  # 1분 밖에 지나지 않아서 OK
    _esc_interrupt_missing_since["test_dispatch"] = time.time() - (3 * 60)  # 3분 전
    assert classify("일하는 중", 1, 2, "test_dispatch")[0] == "UNKNOWN"  # UI 표시 부재만으로 중단 단정 금지

    _esc_interrupt_missing_since["finished"] = time.time() - 31
    assert classify("▣ Build · free model · 2m", 1, 8, "finished")[0] == "REPORT_MISSING"
    assert classify("▣ Build · free model · 2m\nesc interrupt", 1, 8, "finished")[0] == "OK"
    assert classify("Free usage limit reached", 1, 8)[0] == "ERROR"

    # 모델 라인 테스트
    assert MODEL_LINE.findall("Build · Laguna S 2.1 (free) OpenRouter")[-1] == "Laguna S 2.1"

    # PERM 테스트 (권한 질문 우선)
    perm_screen = """Permission required
The agent wants to access an external directory.
Allow once
Allow always
Reject"""
    assert classify(perm_screen, 1, 8, "d_perm")[0] == "PERM"
    # 권한 질문이 있으면 ERROR보다 우선
    perm_with_error = perm_screen + "\nrate-limited"
    assert classify(perm_with_error, 1, 8, "d_perm")[0] == "PERM"
    # NVIDIA Function/Account 오류 탐지와 redaction 검증 (익명화된 더미 fixture)
    dummy_nvidia = "[Nvidia] Function DUMMY_FUNC_001: Not found for account DUMMY_ACCOUNT_001."
    assert classify(dummy_nvidia, 1, 8, None)[0] == "ERROR"
    result_why = classify(dummy_nvidia, 1, 8, None)[1]
    assert "분류=NVIDIA_FUNC_NOT_FOUND" in result_why
    assert "DUMMY_FUNC_001" not in result_why
    assert "DUMMY_ACCOUNT_001" not in result_why
    # 줄 포장 견디는지 확인
    dummy_wrapped = "[Nvidia] Function DUMMY_FUNC_002:\nNot found for account DUMMY_ACCOUNT_002."
    assert classify(dummy_wrapped, 1, 8, None)[0] == "ERROR"
    result_why_wrapped = classify(dummy_wrapped, 1, 8, None)[1]
    assert "분류=NVIDIA_FUNC_NOT_FOUND" in result_why_wrapped
    assert "DUMMY_FUNC_002" not in result_why_wrapped
    assert "DUMMY_ACCOUNT_002" not in result_why_wrapped

    # 일반 화면은 OK
    assert classify("정상 작업 중", 1, 8, None)[0] == "OK"

    # 필터 함수 테스트
    test_workers = [
        {"dispatchId": "d1", "workspace": "repo1::/path/w1", "state": "ready", "outcome": "in_progress", "terminalState": "active"},
        {"dispatchId": "d2", "workspace": "repo1::/path/orca/workspaces/ipo-calc/w2", "state": "ready", "outcome": "in_progress", "terminalState": "retained"},
        {"dispatchId": "d3", "workspace": "repo2::/path/other", "state": "dispatched", "terminalState": "active"},
        {"dispatchId": "d4", "workspace": "repo1::/path/w3", "state": "succeeded", "terminalState": "released"},  # settled
        {"dispatchId": "d5", "workspace": "repo1::/path/w4", "state": "succeeded", "outcome": "in_progress", "terminalState": "retained"},
    ]
    # repoId 접두사로 필터링
    result = filter_workers_by_repo(test_workers, "repo1", None)
    assert len(result) == 2
    assert set(d for d, _ in result) == {"d1", "d2"}
    # 다른 repo는 제외
    result = filter_workers_by_repo(test_workers, "repo2", None)
    assert len(result) == 1
    assert result[0][0] == "d3"
    # 수동 문자열 필터 (repoId 없을 때)
    result = filter_workers_by_repo(test_workers, None, "ipo-calc")
    assert len(result) == 1
    assert result[0][0] == "d2"

    # 최근 요약 알림은 반복 상태를 dedupe하고 OK 이후 재발을 다시 알린다.
    seen_status: dict[str, str] = {}
    error_line = "ERROR   frontend    ctx_1 model=x"
    assert collect_status_events([error_line], seen_status) == [error_line]

    # 마지막 5줄 밖의 첫 워커 오류도 빠짐없이 전달 대상에 들어간다.
    many_rows = [error_line] + [f"OK worker ctx_{i + 2}" for i in range(8)]
    assert collect_status_events(many_rows, {}) == [error_line]

    lifecycle = {}
    observed = [{"dispatchId": "active", "taskId": "t1", "state": "ready", "workspace": "r::/w"},
                {"dispatchId": "old", "state": "succeeded", "workspace": "r::/old"}]
    assert lifecycle_rows(observed, lifecycle) == []  # 과거 완료 알림 폭주 방지
    observed[0]["state"] = "succeeded"
    assert lifecycle_rows(observed, lifecycle)[0].startswith("REVIEW")
    assert lifecycle_rows(observed, lifecycle) == []
    observed.append({"dispatchId": "new-failure", "state": "failed", "workspace": "r::/w"})
    assert lifecycle_rows(observed, lifecycle)[0].startswith("FAILED")

    original_call = orca_call
    try:
        relay_state = {}
        globals()["orca_call"] = lambda *args: (None, "offline")
        relay_events([error_line], relay_state, "run-test")
        assert len(relay_state["outbox"]) == 1 and relay_state["delivery_error"] == "offline"
        for event in relay_state["outbox"].values():
            event["next_attempt"] = 0
        sent = []
        def delivered(*args):
            sent.append(args)
            return {"messageId": "m-ok"}, None
        globals()["orca_call"] = delivered
        relay_events([], relay_state, "run-test")
        assert relay_state["outbox"] == {} and len(sent) == 1
        assert "run:run-test" in sent[0] and "status" in sent[0] and "high" in sent[0]
        relay_events([], relay_state, "run-test")
        assert len(sent) == 1
    finally:
        globals()["orca_call"] = original_call
    assert collect_status_events([error_line], seen_status) == []
    assert collect_status_events(["OK      frontend    ctx_1 model=x"], seen_status) == []
    assert collect_status_events([error_line], seen_status) == [error_line]

    # inbox message id는 peek 결과가 반복되어도 한 번만 알린다.
    seen_messages: list[str] = []
    assert len(collect_new_messages([{"id": "m1"}], seen_messages)) == 1
    assert collect_new_messages([{"id": "m1"}], seen_messages) == []
    # repoId와 수동 필터 모두 주면 둘 다 적용 (AND)
    result = filter_workers_by_repo(test_workers, "repo1", "ipo-calc")
    assert len(result) == 1
    assert result[0][0] == "d2"

    # 독립 watchdog은 자신에게 묶인 Run이 아니라 전달된 coordinator Run을 조회한다.
    original_orca = orca
    calls: list[tuple[str, ...]] = []
    try:
        def fake_worker_list(*args: str) -> dict:
            calls.append(args)
            return {"workers": test_workers}
        globals()["orca"] = fake_worker_list
        result = active_dispatches("repo1", None, "run-current")
        assert {dispatch for dispatch, _ in result} == {"d1", "d2"}
        assert calls == [("orchestration", "worker-list", "--json", "--limit", "100", "--run", "run-current")]
    finally:
        globals()["orca"] = original_orca

    # OpenCode parent_id 트리에서 하위 세션을 찾아 최근 중단 문구를 감지한다.
    with tempfile.TemporaryDirectory() as temp_dir:
        db_path = os.path.join(temp_dir, "opencode.db")
        connection = sqlite3.connect(db_path)
        connection.executescript("""
            CREATE TABLE session (id TEXT PRIMARY KEY, parent_id TEXT, title TEXT,
                directory TEXT, time_updated INTEGER, agent TEXT, model TEXT);
            CREATE TABLE message (id TEXT PRIMARY KEY, session_id TEXT, time_created INTEGER,
                data TEXT);
            CREATE TABLE part (id TEXT PRIMARY KEY, message_id TEXT, session_id TEXT,
                time_created INTEGER, data TEXT);
        """)
        now_ms = int(time.time() * 1000)
        connection.execute(
            "INSERT INTO session VALUES (?,?,?,?,?,?,?)",
            ("root", None, "parent", "/repo/frontend", now_ms, "build", "{}"),
        )
        connection.execute(
            "INSERT INTO session VALUES (?,?,?,?,?,?,?)",
            ("child", "root", "child agent", "/repo/frontend", now_ms, "explore", '{"id":"free-model"}'),
        )
        connection.execute(
            "INSERT INTO message VALUES (?,?,?,?)",
            ("m1", "child", now_ms, json.dumps({"role": "assistant", "text": "OpenCode stopped while this response was in progress (code 0)"})),
        )
        connection.execute(
            "INSERT INTO part VALUES (?,?,?,?,?)",
            ("p1", "m1", "child", now_ms, json.dumps({"type": "text", "text": "OpenCode stopped while this response was in progress (code 0)"})),
        )
        connection.commit()
        connection.close()
        child_rows, child_error = inspect_opencode_children({"/repo/frontend"}, 8, db_path, now_ms)
        assert child_error is None
        assert len(child_rows) == 1 and child_rows[0][0] == "ERROR"
        assert "child" in child_rows[0][1] and "model=free-model" in child_rows[0][1]

        # 정상 종료한 하위 세션은 이전 오류 part 때문에 재차 ERROR가 되지 않는다.
        connection = sqlite3.connect(db_path)
        connection.execute("UPDATE message SET data=? WHERE id='m1'", (json.dumps({"role": "assistant", "finish": "stop", "time": {"completed": now_ms}}),))
        connection.commit()
        connection.close()
        assert inspect_opencode_children({"/repo/frontend"}, 8, db_path, now_ms)[0] == []

        original_orca = orca
        try:
            def fake_orca(*args: str) -> dict:
                return {"terminal": {"tail": [
                    "OpenCode stopped while this response was in progress. (code 0)"
                ]}}
            globals()["orca"] = fake_orca
            terminal_rows = inspect_unassigned_opencode_terminals(
                [{"handle": "term_child", "lastOutputAt": now_ms, "title": "OpenCode subagent"}],
                set(), now_ms,
            )
            assert len(terminal_rows) == 1 and terminal_rows[0][0] == "ERROR"
            assert "term_child" in terminal_rows[0][1]
        finally:
            globals()["orca"] = original_orca

    print("selftest OK")
    return 0


def monitor_lock_path() -> str:
    script_dir = os.path.dirname(os.path.abspath(__file__))
    result = subprocess.run(["git", "-C", script_dir, "rev-parse", "--git-common-dir"],
                            capture_output=True, text=True, timeout=10)
    identity = os.path.realpath(os.path.join(script_dir, result.stdout.strip())) if result.returncode == 0 else script_dir
    key = hashlib.sha256(identity.encode()).hexdigest()[:12]
    return f"/tmp/orca-worker-watchdog-{key}.lock"


def monitor_info(lock_path: str) -> dict:
    with open(lock_path, "a+", encoding="utf-8") as handle:
        handle.seek(0)
        try:
            info = json.load(handle)
        except (ValueError, OSError):
            info = {}
        try:
            fcntl.flock(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            info["running"] = True
        else:
            info["running"] = False
            fcntl.flock(handle, fcntl.LOCK_UN)
    return info


def manage_monitor(args: argparse.Namespace, lock_path: str) -> int:
    info = monitor_info(lock_path)
    if args.status:
        status_file = info.get("status_file", args.status_file)
        info["status_age_seconds"] = round(time.time() - os.path.getmtime(status_file), 1) if os.path.exists(status_file) else None
        state = load_notify_state(info.get("state_file", args.notify_state_file or "/tmp/orca-worker-notify.json"))
        info["pending_deliveries"] = len(state.get("outbox", {}))
        info["delivery_error"] = state.get("delivery_error")
        info["last_cycle_at"] = state.get("last_cycle_at")
        info["healthy"] = bool(info["running"] and info["status_age_seconds"] is not None
                               and info["status_age_seconds"] < max(90, (info.get("interval") or 30) * 3)
                               and (info["last_cycle_at"] or 0) >= info.get("started_at", 0)
                               and not info["delivery_error"])
        print(json.dumps(info, ensure_ascii=False))
        return 0 if info["running"] else 1
    if args.stop:
        if not info["running"]:
            print("watchdog stopped")
            return 0
        pid = info.get("pid")
        process = subprocess.run(["ps", "-p", str(pid), "-o", "args="], capture_output=True, text=True)
        if os.path.abspath(__file__) not in process.stdout or "--loop" not in process.stdout:
            print("watchdog PID 신원 확인 실패; 중지하지 않음", file=sys.stderr)
            return 1
        os.kill(pid, signal.SIGTERM)
        deadline = time.monotonic() + 5
        while time.monotonic() < deadline and monitor_info(lock_path)["running"]:
            time.sleep(0.1)
        print(json.dumps({"stopped": not monitor_info(lock_path)["running"], "pid": pid}))
        return int(monitor_info(lock_path)["running"])
    if info["running"]:
        same_binding = info.get("run") == args.run and info.get("coordinator") == args.coordinator_terminal
        print(json.dumps({**info, "already_running": True, "same_binding": same_binding}, ensure_ascii=False))
        return 0 if same_binding else 1
    log_path = args.log_file or "/tmp/orca-worker-watchdog.log"
    command = [sys.executable, os.path.abspath(__file__), "--loop", str(args.loop or 30),
               "--status-file", args.status_file, "--stall-min", str(args.stall_min),
               "--inbox-interval", str(args.inbox_interval), "--log-file", log_path]
    for option, value in (("--run", args.run), ("--coordinator-terminal", args.coordinator_terminal),
                          ("--tree-filter", args.tree_filter), ("--notify-state-file", args.notify_state_file)):
        if value:
            command.extend([option, value])
    with open(log_path, "a", encoding="utf-8") as log:
        child = subprocess.Popen(command, stdin=subprocess.DEVNULL, stdout=log, stderr=log,
                                 start_new_session=True, close_fds=True)
    deadline = time.monotonic() + 5
    while time.monotonic() < deadline:
        info = monitor_info(lock_path)
        if info["running"] and info.get("pid") == child.pid:
            print(json.dumps({**info, "log_file": log_path}, ensure_ascii=False))
            return 0
        if child.poll() is not None:
            if info["running"] and info.get("run") == args.run and info.get("coordinator") == args.coordinator_terminal:
                print(json.dumps({**info, "already_running": True}, ensure_ascii=False))
                return 0
            break
        time.sleep(0.1)
    print(f"watchdog 시작 확인 실패: {log_path}", file=sys.stderr)
    return 1


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tree-filter", default=None, help="수동 필터: 이 문자열이 포함된 worktree만 점검 (repoId 자동 감지 실패 시 폴백)")
    parser.add_argument("--stall-min", type=float, default=8, help="무출력 몇 분부터 정지로 볼지")
    parser.add_argument("--status-file", default="/tmp/orca-worker-status.txt")
    parser.add_argument("--loop", type=int, metavar="SECONDS", help="주기적으로 반복 실행")
    parser.add_argument("--run", default=None, help="코디네이터 Orca Run ID (알림 inbox 확인용)")
    parser.add_argument("--coordinator-terminal", default=None, help="check --peek에 사용할 코디네이터 terminal handle")
    parser.add_argument("--inbox-interval", type=int, default=300, help="상태 알림과 inbox 확인 간격(초, 기본 300)")
    parser.add_argument("--notify-state-file", default=None, help="알림 dedupe JSON 경로")
    parser.add_argument("--selftest", action="store_true")
    management = parser.add_mutually_exclusive_group()
    management.add_argument("--start", action="store_true", help="터미널 없이 백그라운드 단일 프로세스 시작 (기본 30초)")
    management.add_argument("--stop", action="store_true", help="이 저장소의 watchdog만 안전하게 중지")
    management.add_argument("--status", action="store_true", help="PID/Run/상태 신선도/전달 실패 출력")
    parser.add_argument("--log-file", default=None)
    args = parser.parse_args()

    if args.selftest:
        return selftest()

    if bool(args.run) != bool(args.coordinator_terminal):
        parser.error("--run과 --coordinator-terminal은 함께 지정해야 합니다")
    if args.start and not args.run:
        parser.error("--start에는 --run과 --coordinator-terminal이 필요합니다")
    if args.loop is not None and args.loop < 1:
        parser.error("--loop은 1 이상이어야 합니다")
    if args.inbox_interval < 1:
        parser.error("--inbox-interval은 1 이상이어야 합니다")

    lock_path = monitor_lock_path()
    if args.start or args.stop or args.status:
        return manage_monitor(args, lock_path)

    # worktree/Run이 달라도 같은 Git 저장소에는 watchdog 하나만 허용한다.
    lock = open(lock_path, "a+", encoding="utf-8")
    try:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
    except BlockingIOError:
        lock.close()
        print("이미 이 저장소의 watchdog이 실행 중입니다. --status로 확인하세요.", file=sys.stderr)
        return 1

    state_path = args.notify_state_file or (
        f"/tmp/orca-worker-notify-{args.run}.json" if args.run else "/tmp/orca-worker-notify.json"
    )

    lock.seek(0)
    lock.truncate()
    json.dump({"pid": os.getpid(), "run": args.run, "coordinator": args.coordinator_terminal,
               "status_file": args.status_file, "state_file": state_path,
               "started_at": time.time(), "interval": args.loop, "log_file": args.log_file}, lock)
    lock.flush()
    os.fsync(lock.fileno())
    signal.signal(signal.SIGTERM, lambda *_: sys.exit(0))
    repo_id = get_repo_id()
    warn_msg = None
    if not repo_id:
        warn_msg = "repoId 자동 감지 실패 — 문자열 필터로 대체"
        if not args.tree_filter:
            warn_msg += " (--tree-filter 미지정, 모든 워커 점검 안 함)"
    state = load_notify_state(state_path)
    next_inbox = 0.0
    try:
        while True:
            try:
                result = run_once(repo_id, args.tree_filter, args.stall_min, args.status_file,
                                  warn_msg, args.run, state)
            except Exception as exc:
                # 스캔 장애가 감시 프로세스를 조용히 종료시키지 않게 한다.
                atomic_write(args.status_file, f"# {time.strftime('%Y-%m-%d %H:%M:%S')} 점검\nUNKNOWN WATCHDOG scan 스캔 실패 {type(exc).__name__}\n")
                result = 1
            check_inbox = time.monotonic() >= next_inbox
            try:
                notify_cycle(args.status_file, state_path, args.run, args.coordinator_terminal,
                             state=state, check_inbox=check_inbox)
            except Exception as exc:
                # 전달 장애도 다음 주기에 재시도한다. 오류 전문/시크릿은 출력하지 않는다.
                state["delivery_error"] = f"알림 주기 실패: {type(exc).__name__}"
                print(state["delivery_error"], file=sys.stderr, flush=True)
                save_notify_state(state_path, state)
            if check_inbox:
                next_inbox = time.monotonic() + args.inbox_interval
            if not args.loop:
                return result
            time.sleep(args.loop)
    finally:
        fcntl.flock(lock, fcntl.LOCK_UN)
        lock.close()


if __name__ == "__main__":
    sys.exit(main())
