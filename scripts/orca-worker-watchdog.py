#!/usr/bin/env python3
"""Orca 워커 감시 — 업스트림 에러·무출력 정지를 감지해 한 줄씩 상태 파일에 적는다.

코디네이터(Claude)가 점검마다 워커 터미널 원문을 읽으면 회당 수천 토큰이 든다.
이 스크립트가 대신 읽고 한 줄로 줄여주면 코디네이터는 상태 파일만 보면 된다.

재배정은 하지 않는다: 에러 문구가 떠도 OpenCode가 스스로 회복하는 경우가 있어,
오진으로 작업 중인 워커를 죽이는 비용이 자동 재배정의 이득보다 크다.
# ponytail: 감지만 함. 재배정 자동화는 오진율을 실측한 뒤에 붙인다.

사용:
  scripts/orca-worker-watchdog.py                 # 1회 점검
  scripts/orca-worker-watchdog.py --loop 120      # 120초마다 반복(별도 터미널에서 실행)
  scripts/orca-worker-watchdog.py --selftest

종료 코드: 0=모두 정상, 1=문제 있는 워커 있음
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
import time

# 2026-09-28 실제로 워커를 멈춰 세운 문구들에서 뽑았다.
ERROR_PATTERNS = re.compile(
    r"rate[- ]limited|Upstream idle timeout|Request rejected|\b429\b"
    r"|quota exceeded|insufficient credits|API Error"
    r"|Endpoint is unavailable|Upstream request failed",
    re.IGNORECASE,
)
# OpenCode 상태줄: "Build · Laguna S 2.1 (free) OpenRouter" — 지정 모델과 실제 모델 대조용.
MODEL_LINE = re.compile(r"Build\s*·\s*(.+?)(?:\s*\(free\))?(?:\s+OpenRouter)?\s*$", re.MULTILINE)


def orca(*args: str) -> dict | None:
    """orca CLI를 JSON으로 호출한다. 실패하면 None."""
    try:
        proc = subprocess.run(["orca", *args], capture_output=True, text=True, timeout=30)
    except (OSError, subprocess.TimeoutExpired):
        return None
    try:
        payload = json.loads(proc.stdout)
    except json.JSONDecodeError:
        return None
    return payload.get("result") if payload.get("ok") else None


def classify(tail: str, idle_min: float, stall_min: float) -> tuple[str, str]:
    """터미널 꼬리 텍스트와 무출력 시간으로 상태를 판정한다."""
    hit = ERROR_PATTERNS.search(tail)
    if hit:
        return "ERROR", f"문구={hit.group(0)!r}"
    if idle_min >= stall_min:
        return "STALL", f"{idle_min:.0f}분 무출력"
    return "OK", ""


def active_dispatches(tree_filter: str) -> list[tuple[str, str]]:
    res = orca("orchestration", "worker-list", "--json", "--limit", "100")
    if not res:
        return []
    rows = []
    for worker in res.get("workers", []):
        tree = (worker.get("resource") or {}).get("worktreeId", "")
        if tree_filter in tree and worker.get("dispatchStatus") == "dispatched":
            rows.append((worker["dispatchId"], tree.rsplit("/", 1)[-1]))
    return rows


def inspect(dispatch: str, stall_min: float) -> tuple[str, str]:
    res = orca("orchestration", "worker-show", "--dispatch", dispatch, "--json")
    handle = ((res or {}).get("terminal") or {}).get("handle")
    if not handle:
        return "UNKNOWN", "터미널 핸들 없음"
    last_ms = (res["terminal"] or {}).get("lastOutputAt") or 0
    idle_min = (time.time() * 1000 - last_ms) / 60000 if last_ms else 0.0

    read = orca("terminal", "read", "--terminal", handle, "--limit", "40", "--json")
    tail = "\n".join(((read or {}).get("terminal") or {}).get("tail", []))
    models = MODEL_LINE.findall(tail)
    # 상태줄은 한 줄에 모델명과 브랜치명이 공백 여러 칸으로 나뉘어 같이 찍힌다.
    model = re.split(r"\s{2,}", models[-1].strip())[0] if models else "?"

    status, why = classify(tail, idle_min, stall_min)
    detail = f"model={model} idle={idle_min:.0f}m handle={handle}"
    return status, f"{detail} {why}".strip()


def run_once(tree_filter: str, stall_min: float, status_path: str) -> int:
    stamp = time.strftime("%Y-%m-%d %H:%M:%S")
    lines = [f"# {stamp} 점검"]
    problems = 0
    for dispatch, tree in active_dispatches(tree_filter):
        status, detail = inspect(dispatch, stall_min)
        if status != "OK":
            problems += 1
        lines.append(f"{status:7} {tree:12} {dispatch} {detail}")
    if len(lines) == 1:
        lines.append("활성 워커 없음")
    body = "\n".join(lines)
    with open(status_path, "w", encoding="utf-8") as handle:
        handle.write(body + "\n")
    print(body)
    return 1 if problems else 0


def selftest() -> int:
    assert classify("[Poolside] ... temporarily rate-limited upstream", 1, 8)[0] == "ERROR"
    assert classify("Upstream idle timeout", 1, 8)[0] == "ERROR"
    assert classify("Endpoint is unavailable", 1, 8)[0] == "ERROR"
    assert classify("Upstream request failed", 1, 8)[0] == "ERROR"
    assert classify("Error from provider (Console): Upstream request failed: Endpoint is unavailable.", 1, 8)[0] == "ERROR"
    assert classify("정상 작업 중", 9, 8)[0] == "STALL"
    assert classify("정상 작업 중", 2, 8)[0] == "OK"
    assert MODEL_LINE.findall("Build · Laguna S 2.1 (free) OpenRouter")[-1] == "Laguna S 2.1"
    print("selftest OK")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tree-filter", default="vibe-ai-team", help="이 문자열이 포함된 worktree만 점검")
    parser.add_argument("--stall-min", type=float, default=8, help="무출력 몇 분부터 정지로 볼지")
    parser.add_argument("--status-file", default="/tmp/orca-worker-status.txt")
    parser.add_argument("--loop", type=int, metavar="SECONDS", help="주기적으로 반복 실행")
    parser.add_argument("--selftest", action="store_true")
    args = parser.parse_args()

    if args.selftest:
        return selftest()
    if not args.loop:
        return run_once(args.tree_filter, args.stall_min, args.status_file)
    while True:
        run_once(args.tree_filter, args.stall_min, args.status_file)
        time.sleep(args.loop)


if __name__ == "__main__":
    sys.exit(main())
