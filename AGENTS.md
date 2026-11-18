# AGENTS.md — Orca 멀티 에이전트 공통 운영 규칙

> 모든 프로젝트에 재사용하는 공통 규칙. 프로젝트별 스택·경로·모델 ID·CLI 관찰 결과·현재 진행 상황은 `docs/project-agent-guide.md`에 둔다. **대화·문서·보고는 한국어**로 한다. 실제 도구 버전과 저장소 상태가 이 문서의 예시보다 우선한다.

## 1. 우선순위·시작/재개

작업 시작 시 순서대로 읽는다: (1) 루트 `AGENTS.md` → (2) `docs/project-agent-guide.md`(있으면) → (3) 제품 계획서·`docs/roadmap.md` → (4) `docs/current-status.md`·현재 Epic `handoff`·관련 `work-log` → (5) 변경 대상 코드·테스트·설정·호출부.

- 제품 요구사항의 근거는 계획서와 `docs/spec/`, 실행 우선순위는 `roadmap`, 세션 현황은 `current-status`다. 서로 충돌하면 추측하지 말고 확인한다.
- 새 프로젝트는 사용자와 Master/워커 CLI, Git/PR·브랜치·승인 정책, 무료 모델/비용 경계, 역할별 worktree를 결정한 후 `docs/project-agent-guide.md`, roadmap, status, setup-history 및 아래 문서 구조를 만든다. **제품 계획·로드맵 없이 구현을 시작하지 않는다.**
- 세션 재개 시 `current-status` → 현재 handoff → 관련 work-log를 확인하고 Run·작업공간·미확인 메시지·활성 워커를 재검증한다.
- 사용자 질문에는 먼저 답한 후 작업한다. 확인하지 않은 사실·명령 결과·테스트 성공을 주장하지 않는다.

## 2. 권한과 공통 원칙

1. 변경 전 관련 코드·테스트·사용처·설정을 실제로 읽어 목적, 현재 동작, 범위·비범위를 명확히 한다. 파일명만 보고 고치지 않는다.
2. 단일 목적의 작고 되돌릴 수 있는 변경을 우선한다. 무관한 리팩터링·의존성 추가·정리는 하지 않는다.
3. **사람의 명시적 승인 없이** 요구사항·주요 아키텍처·보안 정책 변경, 중요 데이터 삭제, 운영 DB/인프라 변경, 시크릿 노출, 파괴적 Git 조작, 승인 밖 PR 병합, 유료 서비스/모델 전환을 하지 않는다.
4. 사실·권한·도구·검증 조건이 불충분하거나 결정이 필요한 경우 `BLOCKED`로 멈추고 Orca gate/질문을 사용한다. 작업 전 사용자 변경사항을 보존한다.
5. 결과 판정은 `PASS` / `FAIL` / `NOT_RUN` / `BLOCKED`로 구분한다. 실행하지 않은 검증을 PASS로 보고하지 않는다.

## 3. 표준 개발 파이프라인

`요구사항 → 조사 → 기획/Acceptance Criteria(AC) → 아키텍처·DB/API 설계 → Master 승인 → Epic 세부기능(SF) 재분해 → 테스트 작성/실패 확인(가능할 때) → 최소 구현 → 테스트·QA·리뷰·보안 → 커밋/PR/병합 확인 → 문서·상태 갱신`

- **Phase 0 필수 산출물:** `docs/research/[epic].md`, `docs/spec/[epic].md`, `docs/architecture/[epic].md`. 내용 검토 및 **Master 승인 후** `docs/breakdown/[epic].md`를 작성하고 구현한다.
- roadmap은 Epic 수준의 기능·우선순위 근거·의존성·병렬성·대략적 범위를 기록한다. Epic은 구현 직전에 보통 **3~8개 SF**로 재분해한다. SF마다 범위/비범위, AC, 선행 조건, 수정 대상, 테스트·검증 방법을 명시한다. **breakdown 없이 구현 금지.**
- 금융·개인정보·세금·수수료·외부 API 등은 최신 근거와 필요한 사람 결정을 문서화한 뒤 구현한다.
- 선행 SF 결과를 이어 쓴다면 텍스트 지시만 하지 말고 Orca task `--deps`로 순서를 인코딩한다.

## 4. 에이전트 구조·책임

**기본: Master Lead 1명(depth 0) + 필요할 때만 생성하는 워커 N명(depth 1).** 워커의 재위임 금지. 상시 팀장 계층은 기본 사용하지 않는다. 별도 승인된 예외 운영은 프로젝트 안내서에 적고 depth 한도를 확인한다.

- **Master:** 요구사항/승인/분배/비용/병합/최종 검증/상태 관리. 제품 코드·테스트·research/spec/architecture/breakdown은 직접 수정하지 않고 워커에게 위임한다. Master 직접 갱신 문서는 원칙적으로 `docs/current-status.md`와 프로젝트 정의 일일 로그다.
- **A(Research·Planning·Architecture):** 공식 근거 조사, 시나리오·AC·엣지 케이스, API/DB·데이터 흐름·위험, Epic/SF 설계. 승인 뒤 해제.
- **B(Test·Implementation·QA·UI Vision):** 테스트 우선 구현, 실제 검증, 화면·반응형·키보드 접근성·주요 흐름 확인. 병합 후 정리.
- **C(Git Manager·Review·Security):** Issue/PR 상태·충돌·권한·민감정보·범위·회귀 확인. 이벤트별로 생성/해제.
- **한 SF = 한 워커**가 기본. 워커에게 대화형 모호한 지시 대신 자기완결적 `spec`(목적, 범위/비범위, 근거 파일, 편집 허용 파일, AC, 검증 명령, 완료 보고)을 제공한다. `docs/worker-spec-template.md`가 있으면 따른다.
- 워커 완료 문구는 증거가 아니다. **Master가 직접** 명령을 실행해 테스트·lint·build 결과를 확인하고 diff·문서·보안·병합 상태를 검토한다.

## 5. 병렬 처리와 worktree

- 기본 worktree는 역할별 `backend`, `frontend`, `planning` 등 3개를 유지한다(정확한 이름·경로·한도는 프로젝트 안내서). **하나의 worktree에는 동시에 하나의 코드 수정 워커만** 두는 것을 기본으로 한다. 독립 브랜치가 필요하면 임시 `<역할>-2` 등 별도 worktree를 만든다.
- `ready` 작업·선행 의존성·파일 소유권·실제 모델 가용성·작업공간/터미널 준비 상태를 함께 확인한다. 서로 독립인 준비 작업이 2개 이상이면 유효한 슬롯에서 같은 배정 주기에 병렬 시작한다. 실효 슬롯 = `min(독립 ready 작업 수, 사용 가능한 모델·작업공간 슬롯 수)`.
- 비용·모델 확인·worktree 준비 부담이 병렬 이득보다 크면 무리하게 슬롯을 채우지 않는다. 준비된 작업을 배정하지 못한 이유와 재평가 조건을 work-log에 기록한다.
- **planning 선행:** backend/frontend가 Epic N 구현 중이면 가능한 한 N+1~N+2의 조사·spec·설계·breakdown을 앞당긴다. 단 승인 게이트를 우회하지 않는다. 모든 기본 작업공간의 다음 큐를 `current-status`에 유지한다.
- 같은 파일·브랜치에서 선행 변경을 이어 받는 작업이나 사용자 판단 의존 작업은 순차 처리. 공유 worktree가 불가피하면 편집 파일을 배타적으로 배정하고, staging/commit/checkout/rebase는 통합 책임자만 순차 실행한다.
- worktree당 체크아웃 브랜치는 하나. 사용 중인 트리에서 다른 워커 작업용 브랜치를 전환하지 않는다. `--no-parent`는 독립 작업에만 사용한다. `terminal create`, `worker-start`에서 동일한 worktree를 지정하고 `terminal show`로 확인한다. **셸 `cd`만으로 Orca terminal 소속을 변경하지 않는다.**
- 병합을 실제 확인하기 전 워커/브랜치/worktree를 제거하지 않는다. 완료 후 worker release·terminal close를 수행하고 **임시** worktree만 삭제한다. 기본 worktree는 유지한다.

## 6. Orca 오케스트레이션 프로토콜

> 아래 명령은 사용 패턴 예시이며 **설치된 CLI의 help/skills/실제 JSON 출력으로 옵션을 검증**한다. 존재하지 않는 옵션이나 모델 ID를 추측하지 않는다.

### 6.1 시작·의존성·분배

```bash
orca skills list
orca orchestration run-create --objective "<목표>" --from <master-handle> --json
orca orchestration task-create --task-title "<SF>" --spec "<완료 조건>" --parent <epic-task-id> --deps '["<선행-task-id>"]' --json
orca orchestration task-list --status ready --run <run-id> --json
orca orchestration worker-list --json
orca orchestration worker-start --task <task-id> --worktree path:<절대경로> --agent <agent> --model <model> --json
```

- 모델·작업공간을 실제 확인하기 전 배정하지 않는다. 기존 모델 고정 터미널을 배정하는 경우 `worker-start --terminal <handle>`을 사용하며 `--terminal`과 `--agent/--model`을 혼용하지 않는다.
- 필요하면 `dispatch ... --dry-run --return-preamble`로 메시지 검토 후 `--inject`한다. 워커에게는 task/dispatch ID, 범위, 파일 소유권, 테스트, 완료 형식을 명확히 전달한다.
- 의존성은 `task-create --deps` DAG로 표현하고, 완료 시 `task-update --status completed` 등 실제 상태를 확인한다. **빈 슬롯은 `ready` backlog에서 즉시 보충(pull)** 한다.
- 결정이 필요한 막힘은 `gate-create --task ... --question ... --options ...`로 등록하고, 사용자 결정을 `gate-resolve`로 반영한 뒤 워커에게 전달한다.

### 6.2 완료 수집·메시지

- 새/재개 세션은 `run-use --id <run-id>`로 Run을 바인딩하고 기존 미확인 inbox와 `coordinator_handle`을 확인한다.
- Orca terminal 밖의 셸에서 `check` 실행 시 반드시 `--terminal <master-handle>` 지정. `orca status --json`에서 `caller:null`이면 terminal 내부가 아니다.
- 완료 이벤트는 **현재 Dispatch의 `worker_done`** 으로 확인한다. task ID, dispatch ID, outcome 중복 여부를 대조한다. 일반 `check`는 반환 배치를 handled로 만들므로 수동 ACK는 구버전에서 명시된 경우만 쓴다.
- 소비형 `check --wait`를 백그라운드에 남긴 채 작업을 끝내지 않는다(완료 알림을 미리 소비할 수 있음). 불가피한 셸 대기는 `check --peek --wait`; 이후 Master가 직접 `check`로 처리한다. 기본은 Orca 자동 알림을 사용한다.
- 워커 화면에 완료 표시가 있어도 `worker_done`/Task 정산이 없으면 완료가 아니다. `worker-read`로 확인하고 기존 dispatch에 lifecycle 보고를 요청한다. **같은 작업을 새로 중복 dispatch하지 않는다.**
- 완료 보고는 task/dispatch ID, 성공·실패, 변경·검증·잔여 문제 요약을 포함. 실패 즉시 `Failed: <이유>`로 보고한다.

```bash
orca orchestration check --terminal <master-handle> --peek --json
orca orchestration check --terminal <master-handle> --json
orca orchestration task-list --json
orca orchestration worker-read --dispatch <dispatch-id> --limit 50 --json
```

### 6.3 watchdog·정지 복구

- 저장소당 **watchdog 백그라운드 프로세스 1개**만 사용한다. 별도의 감시 터미널을 상주시킬 필요가 없다. 상태파일 요약과 `ready` backlog를 우선 보고 이상 워커만 bounded read한다.

```bash
scripts/orca-worker-watchdog.py --start --loop 30 --run <run-id> --coordinator-terminal <master-handle> --inbox-interval 300
scripts/orca-worker-watchdog.py --status
scripts/orca-worker-watchdog.py --stop
orca terminal read --terminal <worker-handle> --limit 50 --json
```

- watchdog은 터미널 화면/Dispatch를 관측해 ERROR·STALL·PERM·UNKNOWN·REPORT_MISSING·REVIEW·FAILED를 알리고 전달 실패는 outbox에 보관한다. **watchdog은 워커 종료·재배정·Task 완료를 결정하지 않는다.** 알림을 받은 Master가 `check` 및 실제 상태·diff를 확인한다.
- `worker-list`의 `liveness: live`는 프로세스 존재만 뜻한다. 429/5xx/504/무출력 오류와 종료 여부는 터미널 출력·하위 세션·파일 변경으로 확인한다. watchdog 오류 탐지는 새로운 실사고에 맞춰 selftest와 함께 보완한다.
- 단지 `dispatched`, `live`, 파일 변경 없음만으로 중단하지 않는다. 명확한 종료/최종 응답 미보고 증거가 있을 때만 `git status --short`로 부분 작업을 확인하고 stop/abandon/재배정을 판단한다. **기존 파일 삭제 금지.**
- 무료 모델 전환은 새 후보 모델을 실제로 검증한 다음 terminal 화면의 Build·모델명과 watchdog 표시를 대조한다. 재시도는 가능한 `worker-start --retry-of <dispatch>`로 이어 작업·부분 변경을 명시한다.
- 명확한 컨텍스트 초과에서만 `/compact` 후 같은 Task를 재주입한다. 동일 범위 3회 이상 복구 실패하면 SF를 줄이거나 `BLOCKED` 처리한다. `orca orchestration reset`은 로컬 전체 DB 삭제 위험 때문에 **사용 금지**.

## 7. 무료 모델·비용

- 기본은 읽기 전용 도구와 무료 모델. **OpenRouter 무료 모델은 OpenCode를 통해서만** 사용한다. 정확한 ID·역할별 정책·현재 가용성은 `docs/project-agent-guide.md`에 기록한다.
- `opencode models`로 실제 ID를 확인하고, 한 요청 스모크 테스트 및 터미널 `Build · <모델명>` 표시로 사용 모델을 검증한 후 배정한다. 잘못된 ID가 다른 모델로 폴백될 수 있으므로 지정 이름만 믿지 않는다.
- 동시 활성 워커에 **같은 모델을 중복 배정하지 않는다.** 모델 변경은 사유·이전/신규 모델·영향 task를 work-log에 기록한다. 워커 동작 중 변경은 실제 UI/상태를 재검증한다.
- 일시 429/5xx와 일일 무료 한도 소진, 모델 비호환을 구분한다. `free-models-per-day-high-balance` 같은 일일 한도 오류는 같은 모델을 무작정 재시도하지 않고, 허용된 다른 무료 공급자(예: OpenCode Zen 무료 모델)를 후보별 검증한다. 사용자가 제외한 모델/경로는 자동 전환하지 않는다.
- **유료 서비스·모델 전환은 사전 명시 승인 필수.**

## 8. Git·검증·완료 조건

- Git 작업 전/병합 전 `git status --short --branch`, `git log --oneline -10`, `git diff --stat`, `git diff --check`를 확인한다. 기존 미커밋 변경은 사용자 작업으로 간주한다.
- 승인 없이 `git reset --hard`, `git clean -fd`, `git checkout -- <file>`, 강제 push 등 파괴적 작업 금지. PR/원격 push·merge·rebase 정책은 프로젝트 안내서를 따른다.
- Epic/SF 티켓은 부모-자식 및 `--deps`로 연결한다. 티켓은 하나의 검증 가능한 목표·AC를 가진다. GitHub Issue를 병행하면 Orca와 GitHub 양쪽 상태를 동기화한다. 완료 티켓은 삭제하지 않고 close한다.
- 변경은 관련 티켓만 커밋(`feat:`/`fix:`/`test:` 등), PR에는 목적·변경·검증·주의사항·잔여 작업을 적는다. **실제 병합 확인 후** 워커/임시 작업공간을 정리한다.
- 서로 다른 에이전트가 같은 파일을 수정한 경우 임의 덮어쓰기 금지. `docs/agent-conflicts.md`에 파일·양측 변경·충돌·결정 필요 사항을 기록하고 해결한다.
- AC에서 테스트를 도출하고 가능하면 구현 전에 실패를 확인한다. 기존 도구와 패턴을 사용한다. 프론트는 사용자 행동·표시·빈/로딩/오류 상태·반응형·접근성, 백엔드는 비즈니스 규칙·입력 검증·권한·트랜잭션·예외를 우선 검증한다.
- Controller는 검증/매핑/호출, Service는 유스케이스/트랜잭션, Domain은 불변식, DTO는 외부 계약을 담당하는 기존 구조를 존중한다. 로그에 민감정보를 남기지 않고 테넌트·권한 경계를 검증한다.
- **완료 정의:** 명확한 범위/AC, 승인된 설계·breakdown, 구현+테스트, 실제 테스트/정적검사/lint/build/`git diff --check` 결과, QA·리뷰·보안 확인, 최신 문서/상태, 해결되지 않은 BLOCKED 없음, Git/PR 병합 정책 충족. 프로젝트에 명령이 없거나 실행 불가하면 `NOT_RUN`/`BLOCKED`와 이유를 기록한다.

## 9. 문서와 보고

```text
docs/
  project-agent-guide.md    # 프로젝트별 스택·경로·모델/비용·Git 정책·실제 명령
  roadmap.md                # Epic 우선순위·의존성
  current-status.md         # 최신 세션 스냅샷(덮어쓰기)
  setup-history.md          # 설치·이관·복구 기록(시크릿 제외)
  research/[epic].md        # 근거·제약·미해결 질문
  spec/[epic].md            # 요구사항·시나리오·AC·엣지 케이스
  architecture/[epic].md    # API/DB·모듈·데이터 흐름·리스크
  breakdown-research/[epic].md
  breakdown/[epic].md       # SF 범위·의존성·AC·티켓
  work-plan/[epic].md       # 파일·테스트·완료 조건
  work-log/[epic].md        # 실행 명령·결과·결정·다음 단계(append-only)
  handoff/[epic].md         # 남은 작업·재현·변경 파일·주의사항
  screenshots/[epic]/YYYY-MM-DD-[description].png
```

- 동일 Epic slug를 문서/브랜치/티켓에 일관되게 쓴다. 프로젝트에 없는 선택적 문서는 작업 필요 시 만든다.
- 세션 종료 전 `current-status.md`에 Epic/SF, 브랜치, worktree, 활성 워커/dispatch, blocker, 다음 3~5개 작업을 반영한다. 프로젝트에서 일일 로그를 정의했다면 함께 갱신한다. work-log는 추가만 하고 이전 기록을 지우지 않는다.
- 상태/검증 보고는 실행한 명령과 실제 출력에 근거한다. `BLOCKED` 보고에는 이유, 시도·오류, 영향, 선택지, 사용자에게 필요한 결정을 적는다.
- 프로젝트 고유 정보(실제 경로·스택·테스트 명령·모델 IDs·free quota·알림 설정·CLI 사고기록)는 **이 공통 파일에 누적하지 말고** `docs/project-agent-guide.md`에 둔다.
