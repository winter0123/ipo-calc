# vibe-ai-team 프로젝트 에이전트 운영 안내서

> 이 문서는 이 저장소에만 적용되며 **Master(코디네이터)가 읽는다.** 공통 규칙은 루트 [AGENTS.md](../AGENTS.md)에 있고, 이 문서는 그 규칙을 공모주 투자 수익 관리 서비스의 실제 스택·Orca 환경·무료 모델 운영 방식으로 구체화한다. 역할별 기술스택·검증 명령·구현 규칙은 이 문서에 없고 `docs/guide-backend.md`/`guide-frontend.md`/`guide-planning.md`에 있다 — Master는 워커 spec을 쓸 때 해당 가이드에서 검증 명령을 그대로 복사해 넣는다.
>
> **기본 구조는 코디네이터 1명 + 병렬 워커다**(4.2.0절, AGENTS.md 6.0절). 새 Master 세션은 `AGENTS.md` → 이 문서 → `docs/roadmap.md` → `docs/current-status.md` 순서로 읽고, 워커에게 줄 spec은 `docs/worker-spec-template.md` 형식으로 쓴다. 워커는 대화 맥락을 모르므로 자기 spec과 자기 역할 가이드만 읽는다.

## 1. 문서 경계와 진실 소스


| 문서                             | 책임                                                                 | 누가 읽는가 |
| ------------------------------ | ------------------------------------------------------------------ | --- |
| `AGENTS.md`                    | 모든 프로젝트에 재사용할 개발·Orca·병렬화·검증 원칙                                    | Master |
| 이 문서                           | 현재 프로젝트의 공통 운영(작업공간, 무료 모델 정책, GitHub·알림, 모니터링)                    | Master |
| `docs/guide-backend.md`        | 백엔드 기술스택·검증 명령·구현 규칙                                              | Master(워커 spec 작성 시), backend 워커 |
| `docs/guide-frontend.md`       | 프론트엔드 기술스택·검증 명령·구현 규칙                                            | Master(워커 spec 작성 시), frontend 워커 |
| `docs/guide-planning.md`       | 조사·기획·설계 규칙, 선행 큐 운영, 사용자 가이드 작성                                  | Master(워커 spec 작성 시), planning 워커 |
| `docs/worker-spec-template.md` | 워커에게 주는 자기완결적 task spec 형식                                        | Master |
| `scripts/orca-worker-watchdog.py` | 워커 정지·업스트림 에러·모델 폴백 감지(셸)                                      | 별도 터미널에서 상시 실행 |
| `docs/work-plan.md`            | 제품 도메인, MVP 요구사항, 금융 규칙과 미결정 사항                                    | Master |
| `docs/roadmap.md`              | 현재 구현 순서, Epic 의존성, SF 게이트, 실행 큐                                   | Master |
| `docs/current-status.md`       | 세션 종료 시점의 실제 진행 상황과 다음 작업                                          | Master(작성·소유) |
| `docs/ai-free-model.md`        | 무료 모델의 검증 결과와 PR별 사용 관찰 기록                                         | Master |
| `docs/daily-log/YYYY_MM_DD.md` | 날짜별 append-only 이력                                                 | Master(작성·소유) |
| `docs/prompt-master.md`        | 새 Master 세션을 시작할 때 붙여넣는 초기 프롬프트                                    | 사용자가 Master 세션에 붙여넣음 |
| `docs/prompt-backend.md`       | (선택) backend 팀장 초기 프롬프트 — 팀장 계층을 쓰는 세션에서만                          | AGENTS.md 6.0.2를 택했을 때만 |
| `docs/prompt-frontend.md`      | (선택) frontend 팀장 초기 프롬프트 — 팀장 계층을 쓰는 세션에서만                         | 〃 |
| `docs/prompt-planning.md`      | (선택) planning 팀장 초기 프롬프트 — 팀장 계층을 쓰는 세션에서만                         | 〃 |


과거 운영 중 발생한 사건의 시간순 서사는 `docs/daily-log/YYYY_MM_DD.md`와 각 Epic의 work-log에 유지한다. 이 문서에는 앞으로도 행동을 바꾸는 규칙과 재현 가능한 명령만 남긴다.

## 2. 제품과 저장소 구조

이 프로젝트는 한 로그인 계정이 여러 투자자와 증권계좌의 공모주 청약, 배정, 매도, 비용, 실현수익을 기록·조회하는 서비스다. 도메인·MVP·제외 범위는 `docs/work-plan.md`를 따르며, 세금·수수료·외부 데이터·개인정보는 조사와 사용자 승인 없이 추측 구현하지 않는다.

```text
<repo>/
├── backend/                  # Java 21 / Spring Boot / Gradle
├── ui/                       # React / TypeScript / pnpm
├── docs/
│   ├── work-plan.md
│   ├── roadmap.md
│   ├── project-agent-guide.md
│   ├── ai-free-model.md
│   └── daily-log/
└── docker-compose.yml
```

`frontend/`는 소스 폴더가 아니라 Orca 작업공간 이름이므로, 프론트엔드 앱 소스는 반드시 `ui/`에 둔다.

## 3. 현재 기술 기준 개요

이 프로젝트는 백엔드(Java/Spring Boot)와 프론트엔드(React/TypeScript) 두 스택을 쓴다. **정확한 검증 명령과 구현 규칙은 이 문서에 없다** — `docs/guide-backend.md`와 `docs/guide-frontend.md`에 있다. Master는 워커 spec에 그 명령을 그대로 복사해 넣고, 병합 전에도 같은 명령을 직접 재실행해 확인한다(워커의 산문 보고는 증거로 쓰지 않는다).

로컬 환경·Docker·DB 복구 절차와 실제 환경 변수 이름은 `docs/setup-history.md`를 진실 소스로 사용한다. 시크릿 값은 이 문서, 커밋, work-log 어디에도 쓰지 않는다.

## 4. GitHub·브랜치·작업공간 운영

### 4.1 GitHub Issue 병행 운영

이 프로젝트는 Orca 태스크(실행 단위)와 GitHub Issue(사람이 보는 현황)를 함께 운영한다. Git Manager 또는 Master만 `gh issue`·`gh pr`을 실행한다.


| Orca 상태            | GitHub 상태                          |
| ------------------ | ---------------------------------- |
| PLANNED            | `status:planned` 이슈 생성             |
| IN\_PROGRESS       | `status:in-progress`               |
| READY\_FOR\_REVIEW | PR 생성, `status:review`             |
| CHANGES\_REQUIRED  | 사유 코멘트, `status:changes-requested` |
| APPROVED           | `status:approved`                  |
| COMPLETED          | 실제 병합 확인 후 이슈 close                |


Epic이 끝나지 않은 SF PR에는 `Part of #<epic-issue>`만 쓴다. `Closes #<epic-issue>`는 마지막 SF PR에서만 사용한다. 그렇지 않으면 GitHub가 Epic Issue를 조기에 자동 close한다.

의미 있는 Run은 Git Manager가 GitHub Issue를 만든 뒤에 시작한다. 문서 또는 코드 산출물을 내는 Task는 해당 Issue를 연결한 단일 목적 커밋을 남기며, 여러 Task 결과를 한 커밋으로 몰아넣지 않는다. Run의 모든 Task가 검증·병합까지 끝난 뒤에만 실제 병합 상태를 확인하고 Issue를 close한다.

```bash
gh issue create --title "<제목>" --body "<spec 요약 + 상위 Epic 링크>" --label "status:planned"
gh issue edit <번호> --remove-label "status:planned" --add-label "status:in-progress"
gh issue edit <번호> --add-label "status:review" --remove-label "status:in-progress"
gh pr view <PR번호> --json state,mergedAt
```

일상적인 SF 완료 PR은 테스트·리뷰·보안·병합 확인을 거친 뒤 사용자 사전 승인 범위에서 main에 병합할 수 있다. 보안 설정, 데이터 마이그레이션, 큰 범위·애매한 변경은 항상 사용자 게이트를 연다.

### 4.2 작업공간

기본 작업공간은 아래 셋이며 프로젝트 종료까지 유지한다.


| 작업공간       | 역할           | 기본 경로                                              |
| ---------- | ------------ | -------------------------------------------------- |
| `backend`  | 백엔드 구현 워커 1개           | `/Users/joy/orca/workspaces/vibe-ai-team/backend`  |
| `frontend` | 프론트엔드 구현 워커 1개          | `/Users/joy/orca/workspaces/vibe-ai-team/frontend` |
| `planning` | 조사·기획·설계 워커 1개          | `/Users/joy/orca/workspaces/vibe-ai-team/planning` |


독립 브랜치가 동시에 필요하면 `backend-2`, `backend-3`처럼 추가 작업공간을 만든다. 역할별 모든 `<역할>-N`을 합쳐 활성 워커는 최대 5개다. 새 워커를 만들기 직전에 `worker-list`로 실제 수를 세고, 독립 대기 작업 수와 여유 수만큼 한 번에 병렬 배정한다.

`planning`은 backend/frontend의 완료를 기다리지 않는다. backend 또는 frontend가 Epic N을 구현 중이면 planning은 의존성을 확인한 뒤 최소 Epic N+1, 가능하면 N+2의 `breakdown`·spec 보강·엣지 케이스 검토를 진행해 다음 구현 작업을 준비한다. 신규 계획 문서가 바닥나면 기존 문서의 드리프트 검토, 사용자 가이드, QA 증적처럼 충돌 없는 산출물로 큐를 채운다. 비워 둘 수밖에 없다면 사유와 재평가 조건을 work-log에 남긴다.

```bash
orca orchestration worker-list --json
orca worktree create --repo id:<repoId> --name <역할>-2 --base-branch origin/main --json
orca worktree list --json
orca terminal create --worktree path:<새-작업공간-절대경로> --title "<제목>" --json
orca terminal show --terminal <handle> --json
```

고정 작업공간과 그 하위 작업공간은 메인 작업공간의 자식 관계로 관리한다. 이 경우 `--no-parent`를 쓰지 않는다. 생성 뒤 `orca worktree list --json`으로 `parentWorktreeId`를 확인한다. 임시 `<역할>-N` 작업공간만 병합·터미널 종료를 확인한 뒤 제거한다.

한 작업공간에는 한 브랜치만 둔다. 다른 작업이 같은 브랜치에서 서로 다른 파일만 작성하는 A그룹 문서 작업이면 워커만 추가할 수 있지만, 다른 PR·브랜치가 필요하면 별도 작업공간을 만든다. 공유 작업공간의 병렬 워커는 배정 파일만 수정·검증하고 Git 명령으로 인덱스나 브랜치를 바꾸지 않는다. Master 또는 Git Manager 한 명이 결과를 검증한 뒤 순차로 commit하며, 워커별 독립 커밋이 필요하면 별도 worktree를 만든다.

### 4.2.0 이 프로젝트의 기본 구조 (2026-09-29 세션부터)

**코디네이터 1명(Claude) + 병렬 워커(OpenCode 무료 모델) N개.** 상시 팀장 세션을 두지 않는다. 상세는 AGENTS.md 6.0절, 코디네이터 운영 지침은 `docs/prompt-master.md`, 워커에게 줄 spec 형식은 `docs/worker-spec-template.md`를 본다.

요약하면:

| 항목          | 방식                                                                                                                                         |
| ----------- | ------------------------------------------------------------------------------------------------------------------------------------------ |
| Claude 세션 수 | 1개(Master)만 상시 유지                                                                                                                          |
| 워커          | worktree당 1개, OpenCode + OpenRouter 무료 모델, 동일 시간대 모델 중복 배정 금지                                                                              |
| 일감 순서       | 세션 시작 때 `task-create --deps`로 DAG 정의 → `ready`가 되는 대로 pull                                                                                 |
| 대기          | `check --wait --timeout-ms 3600000` 긴 blocking 하나로 대기. watchdog이 떠 있는 동안에는 짧은 타임아웃 재시도와 5분 주기 수동 터미널 점검을 하지 않는다 (watchdog이 없을 때만 6.1.1 적용) |
| 정지 감지       | `scripts/orca-worker-watchdog.py --loop 120` (셸), Master는 상태 파일만 읽음                                                                        |
| 검증          | Master가 테스트·lint·build를 직접 실행, 워커의 산문 보고는 증거 아님                                                                                            |
| 병합          | Master가 검증 후 병합, 이어서 task를 `completed`로 닫아 후속 의존성 해제                                                                                       |

2026-09-28에 팀장 계층을 하루 운영해본 결과와 그 구조를 언제 쓰는지는 AGENTS.md 6.0.2절에 정리돼 있다.

### 4.2.1 팀장(Lead) 계층 — 선택적 구조 (2026-09-28 실험)

> 이 절은 팀장 계층을 쓰기로 한 세션에만 적용한다. 기본은 위 4.2.0이다.

이 프로젝트는 2026-09-28 하루 동안 AGENTS.md 6.0.2절의 팀장 계층 구조를 운영했다(사용자 결정, nested depth 한도 2로 확대됨). Master(main, depth 0)는 이제 워커에게 직접 task를 배정하지 않고, 역할별 고정 작업공간(backend/frontend/planning)에 상주하는 팀장(depth 1)에게 위임한다. 팀장이 그 밑에 실제 구현 워커(depth 2)를 직접 생성·검증·배정한다.

**팀장 3개 고정 정보**:

| 팀장 이름 | 담당 작업공간 | 실제 경로(2026-09-28 재생성) | 에이전트·모델 | 병합 권한 |
|---|---|---|---|---|
| backend 팀장 | backend | `orca worktree list --json`으로 확인 (아래 "팀장 세션 현황" 갱신) | Claude Code, `claude-sonnet-5` (Master와 동일) | PR 생성까지, main 병합은 Master가 최종 확인 후 수행 |
| frontend 팀장 | frontend | 〃 | 〃 | 〃 |
| planning 팀장 | planning | 〃 | 〃 | 〃 |

**운영 규칙**:
- 팀장은 1회성 워커가 아니라 세션 내내 유지되는 상시 코디네이터다. 팀장의 터미널 제목은 "backend 팀장"/"frontend 팀장"/"planning 팀장"으로 고정한다.
- **팀장은 Edit/Write로 파일을 직접 고치지 않는다(예외 없음)**: "분량이 적다", "직접 하는 게 빠르다"는 판단이 들어도 예외를 두지 않는다. 실제 코드/문서 변경은 항상 depth 2 워커가 하고, 팀장은 조사·판단·1차 검증만 한다(2026-09-28: 이 예외를 허용했던 문구 때문에 팀장이 직접 프로덕션 코드·문서를 고친 사고 발생 → 예외 폐기). 필요하면 워커를 동시에 여러 개(5개 이상도 가능) 만들어 병렬로 돌려도 된다 — 금지는 워커 수가 아니라 depth 3(워커의 워커) 위임뿐이다.
- 팀장이 만드는 워커(depth 2)는 이 프로젝트 무료 모델 정책(5장)과 모델 다양화 원칙을 그대로 따른다 — OpenCode + OpenRouter, 같은 시간대 동일 모델 반복 배정 금지.
- 팀장은 자기 워커의 `worker_done`을 `check --wait`로 직접 기다리고 diff·테스트를 1차 검증한다. 429/timeout으로 조용히 멈춘 워커는 팀장이 직접 감지·재배정한다 — 6.1.1절의 5분 주기 직접 확인 절차가 팀장에게도 동일 적용된다.
- 팀장은 검증 후 자신이 commit·PR 생성까지 한다. **main 병합은 Master가 팀장의 PR을 한 번 더 확인한 뒤 수행**한다(팀장은 병합하지 않는다).
- 팀장은 **담당 SF 하나가 검증·병합될 때마다** Master의 Run에 요약 보고를 보낸다(`orca orchestration send --to run:<master-run-id>`). Master는 이 요약 보고만 받고 팀장 밑 워커 dispatch에는 개입하지 않는다 — 단, 팀장 자체가 응답 없을 때는 Master가 직접 개입해 팀장을 재생성한다.
- depth 한도: Master(0) → 팀장(1) → 팀장의 워커(2)까지만 허용. 팀장의 워커가 추가로 워커를 만드는 depth 3 위임은 금지.
- 이 구조 도입 시점에 기존 하위 작업공간(backend-2/backend-3-3/frontend-3/planning-3 등 임시 접미사 경로)은 정리하고, 접미사 없는 `backend`/`frontend`/`planning` 이름으로 재생성했다(2026-09-28). 각 팀장은 이 경로에 상주한다.

**팀장 세션 현황(최신 갱신은 current-status.md 참고)**: 팀장의 실제 터미널 handle, dispatch ID, 담당 중인 Epic/SF는 세션마다 바뀌므로 이 문서가 아니라 `docs/current-status.md`에 최신 상태로 기록한다.

**구조 개선 3종(2026-09-28 채택, AGENTS.md 6.0.3절과 동일)**:
1. Epic/SF 간 선행조건은 `orca orchestration task-create --deps '["<선행 task_id>"]'`로 인코딩한다. 선행 task가 `completed`가 되면 후속 task가 자동으로 `ready`로 전환되므로, "대기해라 → 완료되면 알려줄게"를 Master가 매번 수동으로 챙길 필요가 없다.
2. 팀장은 자기 SF를 끝내고 유휴가 되면 `orca orchestration task-list --status ready --run <run_id> --json`으로 backlog를 스스로 훑어 다음 일을 찾는다(pull) — Master의 새 지시를 기다리며 노는 시간을 없앤다.
3. 유료 모델/서비스, 파괴적 git, 요구사항 모호 등 사용자 판단이 필요한 지점은 `gate-create`로 명시적 Decision Gate를 만든다. Master는 `gate-list`로 열린 게이트를 확인해 사용자에게 전달한다.

```bash
cd <작업공간-경로>
git fetch origin main
git checkout -b <git사용자명>/<epic-slug> origin/main
git push -u origin <git사용자명>/<epic-slug>
```

메인 저장소가 `main`을 점유하므로 하위 worktree에서 `git checkout main`을 하지 않는다. 항상 `origin/main`에서 새 브랜치를 만든다. 병합 뒤 다음 작업 시작 시 새 브랜치를 만든 후에만 이전 브랜치를 정리한다.

## 5. OpenCode 무료 모델 운영

### 5.1 기본 원칙

- Master Lead는 Orca 위의 Claude Code 코디네이터다. `worker-start`로 만들지 않는다.
- 워커는 기본적으로 무료 모델만 사용하며, OpenRouter 모델은 ID의 `:free` 접미사와 가격 정보를 모두 확인한다. OpenCode Zen 모델은 해당 서비스의 현재 무료 카탈로그에서 확인한다. 유료 API·서비스와 네이티브 Claude 전환은 사용자 명시 승인 없이는 금지다.
- OpenRouter 무료 모델은 항상 `opencode --model openrouter/<id>`로 실행한다. Claude Code에서 OpenRouter를 경유하는 설정은 사용하지 않는다.
- OpenRouter 계정 일일 한도에 도달하면 별도 할당량인 `opencode/<모델>`(OpenCode Zen)을 사용한다.
- 모델 배정은 기본값일 뿐이다. 역할과 맞지 않거나 반복 오류가 나면 다른 무료 모델로 바꾸고, 모델·사유·영향을 work-log와 `docs/ai-free-model.md`에 남긴다.

모델 카탈로그와 실제 성공/실패/역할 적합도 기록은 [ai-free-model.md](ai-free-model.md)에 모아 두었다. 이 문서의 **역할 적합도 매트릭스와 판정 등급을 워커 모델 선정의 우선 근거**로 사용하고, 목록은 고정 사실이 아니므로 매 실행 전에 최신 무료 여부를 확인한다.

```bash
opencode models
opencode models openrouter
curl -s "https://openrouter.ai/api/v1/models"
```

현재 권장 기본 배정은 다음과 같다. 실제 ID·도구 호환성은 카탈로그와 현재 API를 함께 확인한다.


| 역할                           | 1차 후보                                    | 보완 규칙                                                                 |
| ---------------------------- | ---------------------------------------- | --------------------------------------------------------------------- |
| Research / Planning          | `nvidia/nemotron-3-ultra-550b-a55b:free` | 첫 응답·파일 증적으로 확인                                                       |
| Architecture                 | `nvidia/nemotron-3-super-120b-a12b:free` | API/DB 설계 근거를 재검토                                                     |
| Implementation / Git Manager | `nvidia/nemotron-3-ultra-550b-a55b:free` | 멈춤 시 다른 작동 확인 모델로 교체                                                  |
| Test / QA                    | `poolside/laguna-s-2.1:free`             | 실제 테스트 실행 결과가 필수                                                      |
| UI Vision                    | `inclusionai/ling-3.0-flash-vl:free`     | 화면·파일 증적을 남김                                                          |
| Review / Security            | 호환되는 작동 확인 무료 모델                         | `nemotron-3.5-content-safety`는 Claude 직접 경로에서 무응답 사례가 있어 그대로 재사용하지 않음 |


`nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free`는 Research/Planning 가능성은 남아 있으나, Implementation/Test처럼 도구 호출이 잦은 역할에는 재배정하지 않는다. 관찰 근거는 `ai-free-model.md`에 유지한다.

### 5.2 OpenCode 실행 경로

현재 Orca에서는 `worker-start --agent opencode --model ...`가 launch-time 모델 선택을 지원하지 않아 실패할 수 있다. 이 경우 모델을 터미널 생성 때 직접 지정하고, 이후에는 해당 터미널을 worker에 묶는다.

```bash
# OpenRouter 모델을 OpenCode로 실행
orca terminal create --worktree path:<작업공간-절대경로> --title "<역할-작업명>" \
  --command 'opencode --model openrouter/<provider>/<model>:free' --json
orca terminal read --terminal <handle> --json
orca orchestration worker-start --task <task_id> --terminal <handle> \
  --worktree path:<작업공간-절대경로> --json

# OpenRouter 계정 일일 한도 도달 시 OpenCode Zen 무료 모델을 사용
orca terminal create --worktree path:<작업공간-절대경로> --title "<역할-작업명>" \
  --command 'opencode --model opencode/<현재-무료-모델>' --json
```

`free-models-per-day-high-balance`는 모델 하나의 오류가 아니라 OpenRouter 계정 전체의 일일 한도다. 이 오류가 확인되면 OpenRouter 모델을 바꿔 반복하지 말고 OpenCode Zen 경로로 전환한다. 단순 429/5xx는 10초 → 20초 → 40초 → 80초의 지수 백오프을 최대 4회 적용하고, 총 3분을 넘기면 `BLOCKED`로 기록한다.

OpenCode 워커가 `/compact`를 거치면 dispatch capability가 사라져 `worker_done` 전송이 거부될 수 있다. 이 경우 자동 완료 메시지를 기다리지 않고 산출 파일·터미널·테스트를 Master가 확인한다.

```bash
orca terminal close --terminal <handle> --tab --json
orca orchestration worker-stop --dispatch <dispatch_id> --json
orca orchestration task-update --id <task_id> --status completed \
  --result '{"outcome":"succeeded","reportPath":"<파일경로>","note":"worker_done 자동보고 불가로 Master가 증적 확인 후 수동 완료 처리"}' --json
```

수동 완료는 자동 보고처럼 기록하지 않으며, 원인·증적·처리 방식을 work-log에 남긴다.

### 5.3 무료 여부와 호환성 확인

새 OpenRouter 모델의 첫 오류가 나면 곧바로 실패 목록에 넣지 않는다. 먼저 OpenRouter에서 여전히 무료인지 확인한다.

```bash
curl -s "https://openrouter.ai/api/v1/models" | python3 -c "import json,sys; d=json.load(sys.stdin); m=[x for x in d['data'] if x['id']=='<모델ID>']; print(m)"
```

OpenRouter 목록에 없거나 `:free`가 사라졌거나 `pricing.prompt`/`pricing.completion`이 0이 아니면 OpenRouter 무료 모델이 아니다. OpenCode Zen 모델은 `opencode models`와 현재 무료 카탈로그로 별도 확인한다. 일시 장애와 무료 목록 제외를 구분한다. 새 모델은 작은 파일 쓰기 태스크로 검증하고 결과를 `docs/ai-free-model.md`에 추가한다.

### 5.4 Master 사용량과 컨텍스트 대응

Master 자신의 Claude Code 사용량 제한은 사람 또는 현재 CLI가 제공하는 기능 범위에서만 다룬다. `/low-priority`, `/limit-reset`은 환경에 따라 보이지 않거나 동작하지 않을 수 있으므로 존재를 확인하고 사람이 실행한다.

워커가 10분 동안 완료·escalation을 보내지 않으면 먼저 `terminal read`와 `worker-read`로 실제 상태를 확인한다. 토큰 초과가 명확할 때만 `/compact`하고 같은 태스크를 다시 주입한다. 동일 태스크에서 세 번 반복되면 더 작은 SF로 재분해한다.

## 6. 모니터링·알림·사용자 가이드

### 6.1 모니터링

`task-list` 상태 폴링만으로는 모델 오류로 멈춘 워커를 발견할 수 없다.

**기본 방식 (2026-09-29 세션부터): 감시는 셸 watchdog이 하고, 코디네이터는 요약만 읽는다.**

```bash
scripts/orca-worker-watchdog.py --loop 120   # 별도 터미널에서 세션 내내 실행
cat /tmp/orca-worker-status.txt              # 코디네이터는 이것만 본다
```

watchdog은 활성 워커 터미널을 훑어 ① 업스트림 에러 문구(`rate-limited`, `Upstream idle timeout`, `429`, `API Error`), ② 무출력 정지(기본 8분), ③ 실제 사용 중인 모델명(지정 모델과 다르게 폴백되는 사례가 반복 관찰됨)을 한 줄로 요약한다. `ERROR`/`STALL`이 찍힌 워커만 코디네이터가 실제 터미널을 열어 확인하고 재배정을 판단한다 — watchdog은 재배정하지 않는다(오진으로 작업 중인 워커를 죽이는 비용이 더 크다).

`--selftest`로 판정 로직만 빠르게 확인할 수 있고, 종료 코드는 문제가 있으면 1이다.

**참고 — `worker-list`만으로는 부족한 이유**: `orca orchestration worker-list --json`은 각 dispatch의 `workerState`/`dispatchStatus`/`projection.liveness`를 한 번에 보여주지만, `liveness: live`는 프로세스 생존만 뜻한다.

OpenCode가 OpenRouter 업스트림 429로 멈추면 화면에 에러 문구만 뜬 채 프로세스는 계속 살아있어서 `worker-list`/`worker-show` 둘 다 `live`로만 보여준다 — 2026-09-28에 이 상태를 사용자가 화면을 직접 보고서야 발견한 사례가 있다. watchdog이 터미널 텍스트까지 보는 이유가 이것이다. `worker-list`는 `failed`/`abandoned` 1차 스캔용으로만 쓰고, "멈췄는지"의 최종 판정은 watchdog 요약(또는 직접 `terminal read`)으로 한다.

팀장 계층(4.2.1절)을 쓰는 세션이라면, 멈춘 워커를 발견해도 Master가 직접 재배정하지 않고 해당 팀장에게 dispatch id와 화면의 에러 문구를 알려주기만 한다 — 재배정은 팀장이 한다.

**watchdog 없이 직접 확인해야 할 때**(원인이 안 잡히거나 watchdog이 안 떠 있을 때):

```bash
orca terminal read --terminal <handle> --json
orca orchestration worker-read --dispatch <dispatch_id> --limit 50 --json
orca terminal list --worktree path:/Users/joy/orca/workspaces/vibe-ai-team/backend --json
orca terminal list --worktree path:/Users/joy/orca/workspaces/vibe-ai-team/frontend --json
orca terminal list --worktree path:/Users/joy/orca/workspaces/vibe-ai-team/planning --json
```

에러 이후 새 진행이 없거나, 같은 생각 문구가 반복되거나, 파일·커밋이 늘지 않으면 중지로 판단한다. 파일을 삭제하지 않고 새 무료 모델로 같은 태스크를 재배정한다. 비어 있는 작업공간은 `current-status.md`의 다음 큐와 1\~2 Epic 앞선 planning 준비 상태를 대조해 같은 점검 주기 안에 다음 작업을 배정하거나, 예외 사유를 기록한다.

### 6.1.1 "조용히 멈춘 워커" 감지 — `worker_done` 알림과 liveness 판정만으로는 부족함(2026-09-28 확인)

`orca orchestration check --wait`는 워커가 정상적으로 `worker_done`을 보낼 때만 알려준다. 무료 모델이 OpenRouter 업스트림에서 429(rate-limited)나 idle timeout으로 멈추면 터미널 프로세스 자체는 살아있어서 `worker-show`의 `projection.liveness.verdict`가 계속 `live`로 나오고, `check --wait`도 타임아웃(900초) 전까지는 아무 신호 없이 조용하다 — 즉 자동 알림 경로로는 이 상태를 절대 잡지 못한다.

이 프로젝트는 별도 자동 폴링(Claude Code `/loop` 등)을 걸지 않기로 했다(2026-09-28 사용자 결정: 수동 확인 유지). 대신 **최소 5분 간격**으로 활성 dispatch 전체의 터미널을 `orca terminal read --terminal <handle> --limit 50 --json`으로 직접 훑어 화면 텍스트에 `rate-limited`, `Upstream idle timeout`, `Request rejected (429)` 같은 문구가 있는지 확인한다(2026-09-28 사용자 지시). 구체적으로:

- 대기 중에도 5분을 넘기지 않는 주기로 활성 워커들의 터미널을 직접 읽는다(예: 짧은 `check --wait --timeout-ms`를 걸거나, 다른 작업을 하는 틈틈이 5분 경과 여부를 확인해 훑는다) — 900초 같은 긴 타임아웃 하나만 걸어두고 그 사이를 비워두지 않는다
- `check --wait`가 (타임아웃이든 메시지 도착이든) 한 번 돌아올 때마다 — 그 김에 다른 활성 워커들도 같이 훑는다
- 사용자가 상태를 묻거나 "멈춘 것 같다"고 알릴 때
- 진행 상황을 보고하기 직전

터미널 텍스트에서 업스트림 에러 문구가 확인되면(단순 heartbeat/thinking 반복이 아니라 실제 에러 메시지), `worker-abandon` → 해당 worktree `git status --short`로 부분 작업 확인(있으면 내용 검토 후 보존 또는 되돌리기) → `terminal close` → 다른 무료 모델로 `terminal create` → `terminal wait --for tui-idle` → `worker-start --retry-of <이전 dispatch>` 순서로 재배정한다. 같은 시간대에 서로 다른 모델 2개 이상이 연달아 즉시 실패하면 개별 모델 문제가 아니라 OpenRouter 계정 레벨 혼잡일 가능성을 의심한다.

**토큰 소비 주의(2026-09-28 확인)**: `check --wait`의 `--timeout-ms`를 300000(5분)처럼 짧게 걸면, 타임아웃/재시도가 잦아져서 그때마다 짧게라도 reasoning 텍스트를 쓰고 재대기 명령을 다시 내리는 턴이 반복된다 — 이건 "대기"가 아니라 실질적으로 계속 토큰을 쓰는 것과 같다. 워커가 오래 걸리는 작업일수록 `--timeout-ms`를 900000(15분) 이상으로 걸어 재시도 빈도 자체를 줄인다. Master의 5분 감시 원칙(6.1.1절)은 "5분마다 반드시 새 프로세스를 깨워 확인"이 아니라 "5분을 넘기지 않는 주기로 한 번씩 훑는다"는 뜻이므로, 팀장이 자기 워커를 기다릴 땐 긴 타임아웃 하나로 기다리다가 그 안에서 도착하는 신호(worker_done 등)로 깨어나는 것이 정상이다.

**주의(2026-09-28 재발 확인)**: `terminal show`의 `lastOutputAt`이 "방금"으로 나온다고 곧바로 정상 판단하지 않는다 — 그 최근 출력 자체가 `[Poolside] ... temporarily rate-limited upstream` 같은 에러 메시지이고 그 이후로 멈춰있는 경우가 실제로 있었다(사용자가 직접 화면을 보고 지적함). `lastOutputAt`은 "터미널이 죽었는지" 판단하는 보조 지표일 뿐이며, 5분 점검마다 반드시 `terminal read`로 **최근 몇 줄의 실제 텍스트 내용**까지 읽어 에러 문구 유무를 확인한다. 또한 특정 OpenRouter 모델을 지정해도 OpenCode가 내부적으로 다른 모델(주로 Laguna S 2.1)로 자동 폴백하는 사례가 반복 관찰됐다 — 터미널 하단 상태줄의 실제 모델명(`Build · <모델명>`)을 지정한 모델과 대조해 확인한다.

### 6.2 선택형 Hermes 알림

Hermes 알림은 필수가 아니다. 설정되어 있다면 기능 구현 완료 PR, 사람 결정 필요 BLOCKED, 화면 사용자 가이드 발행을 알려준다. 감지 스크립트는 Claude 세션과 독립되어야 하며, PR 감시는 `--state all`을 쓴다.

```bash
gh pr list --repo <owner>/<repo> --state all --json number,title,body --limit 50
hermes send --to telegram "🚨 사람 결정 필요: <한 줄 요약> — <선택지>"
```

공용 알림 스크립트·상태 파일은 저장소 밖 `~/.hermes/`에 둔다. 초기 연결 시에는 기존 최대 PR 번호를 상태 파일에 시딩해 과거 PR 알림이 쏟아지지 않게 한다. 기본 폴링 주기는 600초다.

### 6.3 프론트엔드 사용자 가이드

프론트엔드 SF가 병합되면 planning 워커가 실제 화면 스크린샷과 사용자 가이드를 만든다. 정확한 절차는 `docs/guide-planning.md` 4절을 본다. 휴대폰에서 볼 공유 링크가 필요하면 Master가 별도 Artifact를 발행하고 링크를 Hermes로 보낸다 — Artifact 발행은 외부 공개 행위이므로 사용자 또는 설정된 권한 범위 안에서만 수행한다.

### 6.4 일일 로그

일일 로그는 이 프로젝트 전용 문서다. `docs/daily-log/` 폴더에 `YYYY_MM_DD.md` 파일을 날짜별로 하나씩 만든다. 기존 날짜 파일은 수정하지 않고 append-only로 보존하며, 같은 날짜에 세션이 여러 번이면 해당 날짜 파일의 표에 행을 추가한다. 새 날짜가 시작되면 새 파일을 만든다.

줄글과 문단형 불릿은 금지하고, 각 날짜 파일은 숫자 요약과 표만 사용한다. 파일 맨 위에는 그날 한 일을 1~~10개 숫자 목록으로 쓰고 각 줄은 15~~40자 내외로 요약한다. Epic은 `docs/roadmap.md`의 `<한글 기능명>(Epic N)` 형식을 사용하고, 숫자 요약에서 SF를 설명할 때만 `SF-N: <한 줄 작업명>`을 사용한다.

`기능 구현 현황` 표의 컬럼은 반드시 `Epic/Task | 분야 | 브랜치 | PR(SF 범위) | 상태 | 주요 내용`으로 유지한다. 분야와 실제 브랜치를 기록하고, 테스트 개수는 쓰지 않는다. SF 번호는 `PR(SF 범위)` 컬럼에만 `PR #90(SF-2)`처럼 쓴다. 상태는 `완료`, `진행`, `블로킹`만 사용한다. 문제와 대응은 `이슈 및 트러블슈팅` 표에 기록한다.

```text
docs/daily-log/
└── YYYY_MM_DD.md
```

## 7. 세션 종료 체크리스트

1. diff·테스트·빌드·PR 병합 상태를 실제로 확인한다.
2. 완료 SF의 워커·터미널을 바로 정리한다.
3. `docs/current-status.md`를 최신 Epic/SF·작업공간·블로커·다음 큐로 덮어쓴다.
4. `docs/daily-log/YYYY_MM_DD.md`에 당일 내용을 append한다. 파일명·숫자 요약·표·Epic/SF/PR 표기는 이 문서 6.4의 형식을 따른다.
5. 새 설치·마이그레이션이 있으면 `docs/setup-history.md`를 갱신한다.
6. 모델 교체·오류·적합도 관찰이 있으면 `docs/ai-free-model.md`와 해당 work-log에 남긴다.

## 부록 A. 폐기된 Claude Code + OpenRouter 경로(역사적 참고)

현재 운영 경로가 아니다. Claude Code에서 OpenRouter를 연결하던 과거 설정과 명령어를 보존한 참고 자료다. 새 워커에는 사용하지 말고, 필요한 경우 설정 이력 확인에만 참고한다. OpenRouter 무료 모델은 현재 반드시 OpenCode로 실행한다.

### A.1 과거 Claude Code 직접 경로

```bash
# 과거 방식: Claude 터미널 생성 시 OpenRouter 모델을 직접 지정
orca terminal create --worktree path:<작업공간-절대경로> --title "<역할-작업명>" \
  --command 'claude --model <openrouter-무료-모델-ID>' --json

orca terminal read --terminal <handle> --json
orca terminal show --terminal <handle> --json
orca orchestration task-create --task-title "<제목>" --spec "<구체 지시>" --json
orca orchestration worker-start --task <task_id> --terminal <handle> \
  --worktree path:<작업공간-절대경로> --json
orca orchestration worker-read --dispatch <dispatch_id> --limit 50 --json
```

이 경로에서는 `worker-start`에 `--agent`나 `--model`을 다시 넣지 않고, `terminal create --command`에 지정한 모델을 사용했다. `API Error: Upstream idle timeout exceeded`가 보이면 파일 변경·diff·테스트를 확인한 뒤 다른 모델로 재시도했다.

### A.2 과거 Claude 게이트웨이 해제·네이티브 전환

환경 변수의 실제 값과 API 키는 문서에 기록하지 않는다. 아래는 과거에 Claude Code의 OpenRouter 게이트웨이를 끄고 네이티브 Claude로 전환할 때 사용하던 명령이다.

```bash
unset ANTHROPIC_BASE_URL ANTHROPIC_AUTH_TOKEN CLAUDE_CODE_ENABLE_GATEWAY_MODEL_DISCOVERY
claude

orca terminal create --worktree path:<작업공간-절대경로> --title "<제목>" \
  --command 'unset ANTHROPIC_BASE_URL ANTHROPIC_AUTH_TOKEN CLAUDE_CODE_ENABLE_GATEWAY_MODEL_DISCOVERY && claude --model <정확한-모델-ID>' --json
```

세 변수를 모두 unset해야 네이티브 구독 경로로 전환되며, `sonnet-5` 같은 축약형 대신 설치 환경이 인식하는 전체 모델 ID를 사용했다. 이 부록은 현재 OpenRouter 연결을 복구하거나 새로 설정하라는 지침이 아니다.
