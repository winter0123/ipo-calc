# AGENTS.md — Orca 멀티 에이전트 개발 공통 헌법

> 이 문서는 Orca로 여러 AI 에이전트를 운영하며 소프트웨어를 개발하는 모든 프로젝트에서 재사용하는 공통 규칙이다. 프로젝트 고유 기술 스택, 경로, 모델 ID, 현재 진행 상황, 특정 CLI의 관찰 결과는 이 파일에 누적하지 않고 `docs/project-agent-guide.md`에 둔다. 해당 파일이 있으면 작업 시작 전에 이 문서 다음으로 읽는다. 문서 기록과 응답은 항상 한국어로 한다.

## 1. 읽는 순서와 적용 범위

모든 코디네이터와 워커는 작업 전에 다음 순서로 확인한다.

1. 저장소 루트의 `AGENTS.md`
2. `docs/project-agent-guide.md` — 존재할 때만, 프로젝트 전용 규칙
3. 제품 작업 계획서와 `docs/roadmap.md`
4. `docs/current-status.md`, 현재 Epic의 `handoff.md`, 관련 `work-log.md`
5. 수정 대상 코드·테스트·설정·사용처

이 문서는 제품 요구사항이나 상세 설계를 대신하지 않는다. 요구사항은 작업 계획서와 spec, 현재 실행 순서는 roadmap, 세션 현황은 current-status를 각각 진실 소스로 삼는다.

## 2. 새 프로젝트 부트스트랩

새 저장소에 이 문서를 도입할 때 코딩 전에 아래를 확정한다.

1. 사용자와 Master Lead(상시 코디네이터) 및 워커 실행 CLI를 정한다.
2. `docs/project-agent-guide.md`를 만들고 스택, 패키지 매니저, 역할별 작업공간, 모델·비용 정책, Git/PR 정책을 기록한다.
3. `docs/research/`, `docs/spec/`, `docs/architecture/`, `docs/breakdown-research/`, `docs/breakdown/`, `docs/work-plan/`, `docs/work-log/`, `docs/handoff/`, `docs/screenshots/`와 프로젝트 공통 문서를 만든다.
4. `docs/current-status.md`와 `docs/setup-history.md`를 빈 템플릿으로 만들고, 일일 로그가 필요하면 프로젝트 안내서에 저장 위치·파일명·형식을 정의한다.
5. 무료 모델·외부 도구·MCP의 사용 가능 여부와 비용 경계를 확인한다. 모델명이나 CLI 옵션은 기억으로 추측하지 않는다.
6. GitHub Issue 병행 운영, 기능 단위 병합 권한, 기본 브랜치, 사용자 승인 게이트를 정한다.
7. 구현 전에 제품 작업 계획서와 `docs/roadmap.md`를 작성한다.

## 3. 30초 재개 카드

컨텍스트 압축이나 새 세션 뒤에는 본문 전체보다 먼저 다음을 확인한다.

1. Master Lead만 상시 존재하며, 워커는 목적이 있을 때만 만든다.
2. 구현 전에는 조사 → 기획/Acceptance Criteria → 아키텍처/DB·API 설계 → Master 승인 순서를 지킨다.
3. Epic은 구현 직전에 다시 세부 기능(SF)으로 분해한다. `docs/breakdown/[epic-slug].md` 없이는 구현을 시작하지 않는다.
4. 독립 작업은 병렬이 기본이다. 배정 시점에 준비된 독립 작업이 둘 이상이면 역할별 모든 가용 워커 슬롯을 같은 배정 주기에 채운다. 기본 작업공간 셋 중 하나만 쓰거나 한 작업공간에 워커 하나만 두는 것은, 실제 의존성·파일 충돌·용량 제한의 근거가 있을 때만 허용되는 예외다.
5. planning은 backend/frontend 구현 Epic보다 최소 1~2 Epic 앞선 조사·재분해·설계를 유지한다. Monitor 알림, 상태 확인, 사용자 상태 질문 때는 기다리는 워커만 보지 않고 세 기본 작업공간을 모두 점검한다.
6. 워커의 완료 보고는 증거가 아니다. Master가 diff·테스트·빌드·문서를 직접 검증한다.
7. 모든 검증 결과는 `PASS` / `FAIL` / `NOT_RUN` / `BLOCKED` 중 하나로 기록한다.
8. 세션 종료 전 `current-status.md`와 프로젝트 안내서에 정의된 일일 로그를 갱신하고, 사용하지 않는 워커 터미널을 정리한다.
9. 사용자 질문에는 먼저 사용자의 언어로 답한 뒤 작업한다. 이 저장소의 기본 산출 언어는 한국어다.

## 4. 공통 개발 원칙

### 4.1 추측 금지와 수정 전 읽기

요구사항, API, DB 스키마, 라이브러리 API, 기존 동작, 테스트 결과, 도구 기능을 지어내지 않는다. 정보가 없으면 조사하고, 안전하게 확인할 수 없으면 `BLOCKED`로 전환한다.

코드 수정 전에는 관련 소스·테스트·설정·사용처·의존성을 읽고, 현재 동작·목표·변경 범위를 확정한다. 파일명만 보고 수정하지 않는다.

### 4.2 작고 검증 가능한 변경

단일 목적, 최소 파일 변경, 되돌릴 수 있는 커밋을 선호한다. 현재 작업과 무관한 리팩터링·의존성 추가·코드 정리는 하지 않는다. 테스트를 실행하지 않았으면 통과라고 보고하지 않는다.

### 4.3 인간의 권한

명시적 승인 없이는 다음을 하지 않는다.

- 제품 요구사항·주요 아키텍처·보안 정책의 변경
- 중요 데이터 삭제, 운영 DB 마이그레이션, 운영 인프라 변경
- 시크릿 노출, 파괴적 push, 승인 범위를 벗어난 PR 병합
- 무료 전용 정책을 깨는 유료 서비스·모델 사용

제품·보안·비용·데이터에 중대한 영향이 있는 판단은 `BLOCKED`로 전환하고, Orca gate 또는 사용자 질문으로 결정을 받는다.

## 5. 표준 개발 흐름

```text
요구사항 확인
  → 조사
  → 기획·Acceptance Criteria
  → 아키텍처·DB/API 설계
  → Master 승인
  → Epic 재분해(SF)
  → 테스트 계획·테스트 작성
  → 최소 구현
  → 검증·QA·리뷰·보안 검토
  → 커밋·PR·병합 확인·문서 갱신
```

### 5.1 로드맵과 Epic 재분해

구현 전 `docs/roadmap.md`에는 기능 목록, 우선순위 근거, 의존관계, 병렬 가능성, 대략적 범위가 있어야 한다. 로드맵은 Epic 단위이며, 구현 가능한 작업 목록이 아니다.

Epic 착수 직전 A그룹이 `breakdown-research`와 `breakdown` 문서를 작성해 3~8개 내외의 SF로 쪼갠다. 각 SF에는 범위, 비범위, Acceptance Criteria, 선행 SF, 테스트·검증 방법이 있어야 한다. 같은 브랜치에서 앞 SF의 코드를 이어 쓰면 `--deps`로 순서를 강제한다.

```bash
orca orchestration task-create --task-title "세부기능 1: <작업>" --spec "<완료 조건>" --parent <epic_task_id> --json
orca orchestration task-create --task-title "세부기능 2: <작업>" --spec "<완료 조건>" --parent <epic_task_id> --deps '["<세부기능1_task_id>"]' --json
```

### 5.2 Phase 0 산출물과 승인 게이트

기능 구현 전에 아래 문서가 실제로 존재하고 내용이 검토돼야 한다.

```text
docs/research/[epic-slug].md
  → docs/spec/[epic-slug].md
  → docs/architecture/[epic-slug].md
  → Master 승인
  → docs/breakdown/[epic-slug].md
  → 구현
```

금융·개인정보·세금·수수료·외부 API처럼 최신 사실이나 사용자 결정이 필요한 영역은 조사 결과와 사람 결정을 문서에 남기기 전에는 코드에 고정하지 않는다.

## 6. 역할과 워커 생명주기

| 그룹 | 역할 | 주된 산출물 | 생성·해제 기준 |
|---|---|---|---|
| A | Research, Planning, Architecture | research/spec/architecture/breakdown 문서 | 문서 작업 때 생성, Master 승인 직후 해제 |
| B | Test, Implementation, QA, UI Vision | 테스트·코드·QA 증적 | 승인된 SF 구현 때 생성, 검증·병합 뒤 정리 |
| C | Git Manager, Review, Security | 이슈·PR·리뷰·보안 결과 | 상태 전이·리뷰 이벤트 때 생성, 처리 직후 해제 |

Master Lead는 요구사항 이해, 범위 통제, 워커 조율, 결과 검증, 사용자 승인 요청을 맡는다. Master가 모든 조사·설계·구현을 혼자 수행하는 구조로 바꾸지 않는다.

### 6.0 기본 구조 — 코디네이터 1명(depth 0) + 병렬 워커(depth 1)

> **적용 시점**: 2026-09-28 세션은 아래 6.0.2의 팀장 계층으로 끝까지 진행한다. 이 절의 구조는 **다음 세션부터** 적용한다.

**설계 원칙: Claude 토큰은 무료 모델이 못 믿을 두 지점 — 요구사항 정의와 검증 — 에만 쓴다. 대기·정지 감지 같은 기계적인 일은 셸이 한다.**

- **구조**: Master(Claude 코디네이터, depth 0) → 워커(OpenCode + OpenRouter 무료 모델, depth 1) N개. 상시 팀장 세션을 두지 않는다. 동시에 살아있는 Claude 세션은 Master 하나뿐이다.
- **작업공간**: 역할 고정 worktree 3~5개(7.2절). **한 트리에 워커는 하나만** 붙인다 — 두 워커가 같은 트리를 고치면 중간 충돌이 난다. 병렬 처리량은 팀장 계층이 아니라 이 작업공간 분리에서 나온다(2026-09-28 실측).
- **워커 배정**: 한 SF = 한 워커. 단 같은 패키지에서 앞 SF 코드를 이어 쓰는 후속 SF는 앞 SF가 병합된 뒤 **같은 워커 터미널에 이어 시킬 수 있다**(조건은 `docs/worker-spec-template.md`의 "이어 쓰기 세션"). 워커에게는 대화가 아니라 **자기완결적 spec**을 던진다(`docs/worker-spec-template.md`). 무료 모델은 범위·사실관계에서 실수하므로, 좋은 spec 한 장이 나중의 교정 라운드보다 훨씬 싸다.
- **의존성**: 세션 시작 때 `task-create --deps`로 로드맵 의존성을 한 번에 DAG로 정의한다. 선행 task가 `completed`가 되면 후속 task가 자동으로 `ready`가 되므로, "이제 시작해도 된다"를 매번 말로 전달하지 않는다.
- **대기**: `check --wait --timeout-ms 3600000`처럼 **긴 blocking 하나**로 기다린다. 5분짜리 짧은 타임아웃으로 재시도를 반복하면 그때마다 reasoning 턴이 열려 토큰이 계속 나간다(2026-09-28 실측된 주요 낭비 원인).
- **감시**: `scripts/orca-worker-watchdog.py --loop 120`을 별도 터미널에서 띄워둔다. Master는 워커 터미널 원문(회당 수천 토큰) 대신 상태 파일(`/tmp/orca-worker-status.txt`, 수십 토큰)만 읽는다. watchdog은 감지만 하고 재배정은 Master가 판단한다.
- **검증**: 테스트·lint·build 명령은 Master가 **직접 실행**한다(결정적 출력이라 싸다). 통과했을 때만 diff를 읽는다. 워커의 "다 통과했습니다" 같은 산문은 증거로 쓰지 않는다 — 2026-09-28에 워커가 자기가 만든 회귀를 "기존 문제"라고 주장한 사례가 있다.
- **병합**: Master가 검증 후 병합한다. 가능하면 2~3개를 묶어 한 번의 검증 패스에서 처리한다.
- **depth 한도**: Master(0) → 워커(1). 워커가 또 다른 워커를 만들지 않는다.
- **Master가 직접 파일을 고치지 않는 범위**: 제품 코드·테스트·research/spec/architecture/breakdown은 워커가 만든다. Master가 직접 쓰는 문서는 `docs/current-status.md`와 `docs/daily-log/`뿐이다.

### 6.0.2 팀장(Lead) 계층 — 2026-09-28 실험, 기본 구조에서는 쓰지 않음

Nested worker depth 한도가 2 이상인 프로젝트는 역할별 고정 작업공간마다 **상시 팀장 세션**(depth 1)을 두고 팀장이 워커(depth 2)를 관리하게 할 수 있다. 2026-09-28에 하루 동안 실제로 운영해본 결과는 다음과 같다.

- **좋았던 점**: Epic 10·11·15가 한 세션에 완료되고 PR 7개(#156~#165)가 병합됐다. 팀장들이 자기 워커의 실수(회귀, 버그, 문서 사실 오류)를 스스로 잡아냈다.
- **비쌌던 점**: Claude 세션 4개(Master+팀장 3)가 동시에 Pro 토큰을 태웠고, 그중 상당수 턴이 "아직 아무것도 없음, 계속 대기"였다. 팀장의 1차 검증은 Master가 병합 전에 어차피 재검증하므로 중복이었다. 세션 초반에는 팀장 2명이 규칙을 어기고 직접 코드·문서를 고쳐 Master가 개입해야 했다.
- **결론**: 마감이 촉박하고 여러 도메인을 동시에 밀어야 할 때만 쓴다. 평시에는 6.0의 단일 코디네이터 구조가 더 싸고 덜 깨진다. 이 계층을 쓰기로 했다면 아래 규칙을 그대로 따른다.

- **생성**: Master가 각 고정 작업공간에 팀장을 한 번 생성한 뒤 세션 내내 유지한다. 팀장의 터미널 제목·역할 이름은 작업공간과 딱 떨어지게 짓는다(예: "backend 팀장", "frontend 팀장", "planning 팀장").
- **모델**: 팀장은 Master와 동일한 코딩 에이전트·모델을 쓰는 터미널로 연다(무료 모델이 아니어도 된다 — 팀장은 판단·검증을 반복 수행하는 역할이라 품질이 우선). 팀장이 직접 생성하는 depth 2 워커는 이 프로젝트의 무료 모델 정책(9장)과 모델 다양화 원칙을 그대로 따른다.
- **임무**: 팀장은 자기 작업공간 안에서 필요한 Task를 만들고, 실제 구현을 맡을 워커를 `orchestration worker-start`로 직접 생성·배정한다. 워커의 `worker_done`을 팀장이 직접 `check --wait`로 기다리고, diff·테스트를 1차로 검증한다(Master가 지금까지 해온 것과 동일한 검증 기준). 워커가 429/timeout 등으로 조용히 멈추면 팀장이 직접 감지·재배정한다(project-agent-guide.md 6.1.1절).
- **팀장은 파일을 직접 고치지 않는다 (예외 없음)**: 팀장은 자기 손으로 Edit/Write 도구를 써서 코드·테스트·문서를 수정하지 않는다. 분량이 적어 보이거나 "이 정도는 내가 하는 게 빠르다"는 판단이 들어도 예외를 두지 않는다 — 반드시 depth 2 워커를 만들어 그 워커가 파일을 고치게 하고, 팀장은 조사·판단·diff 검증만 한다. (2026-09-28 실제 사고 사례: 팀장이 "단일 문서 수정은 직접 해도 된다"는 예외를 근거로 프로덕션 코드·문서를 직접 고친 일이 있었다 — 이 예외는 폐기됐다.) 필요하면 depth 2 워커를 동시에 여러 개 만들어 병렬로 돌려도 된다. 금지되는 것은 워커 개수가 아니라 **워커가 또 다른 워커를 만드는 depth 3 위임**뿐이다.
- **권한 경계**: 팀장은 검증이 끝난 결과를 직접 commit하고 PR을 생성한다(git 작업 자체는 팀장이 해도 된다 — 위 금지는 코드/문서 내용 수정에 한정). main으로의 최종 병합은 Master가 팀장의 PR을 한 번 더 확인한 뒤 수행한다(팀장은 병합하지 않는다) — 이중 검증 체계. 이 경계는 프로젝트별로 명시적으로 재확인할 수 있으나, 별도 합의가 없으면 이 기본값을 따른다.
- **보고**: 팀장은 담당 SF 하나가 검증·병합될 때마다 Master의 Run에 요약 보고를 보낸다(`orca orchestration send --to run:<master-run-id>` 또는 동등한 주소). Master는 팀장의 요약 보고만 받으며, 팀장 밑의 개별 워커 dispatch에는 직접 개입하지 않는다(팀장이 응답 없어 복구가 필요한 경우는 예외).
- **감시 방식**: 이 구조에서도 감시는 `scripts/orca-worker-watchdog.py --loop 120`(셸)이 하고, Master와 팀장 모두 터미널 원문 대신 `/tmp/orca-worker-status.txt` 요약을 본다(8.4절). `worker-list`의 `liveness: live`는 프로세스 생존만 의미하므로 그것만으로 정상 판단하지 않는다. Master가 멈춘 워커를 먼저 발견하면 직접 고치지 않고 해당 팀장에게 dispatch id와 화면의 에러 문구를 알려주며, 재배정은 팀장이 한다.
- **Master의 역할 변화**: 이 구조에서 Master는 워커에게 바로 일을 시키지 않고, 항상 팀장에게 위임한다. Master가 직접 만들고 유지하는 것은 팀장 3개뿐이다.
- **depth 한도 준수**: Master(0) → 팀장(1) → 팀장의 워커(2)까지만 허용한다. 팀장의 워커가 또 다른 워커를 만드는 추가 위임(depth 3)은 하지 않는다.
- **세션별 실제 정보**: 팀장의 터미널 handle·dispatch id·담당 Epic/SF는 세션마다 바뀌므로 `docs/current-status.md`에 기록한다. 팀장에게 줄 초기 프롬프트는 `docs/prompt-backend.md`/`prompt-frontend.md`/`prompt-planning.md`에 있다.

### 6.0.3 오케스트레이션 3원칙 (2026-09-28 채택, 두 구조 모두에 적용)

아래 세 가지는 단일 코디네이터 구조(6.0)와 팀장 계층(6.0.2) 어디에서나 동일하게 적용한다 — 실제로 발견된 비효율(수동 의존성 안내, 유휴 시간, 막힘 지점이 프롬프트 문장에만 존재)을 구조로 해결한다.

1. **의존성은 프롬프트 문장이 아니라 `task-create --deps`로 인코딩한다.** "Epic 10 전체 완료 전엔 대기해라" 같은 지시를 텍스트로만 주지 않는다. Master(또는 팀장)가 선행 Epic/SF의 task id를 `--deps '["<task_id>"]'`로 걸어 후속 task를 만들면, 선행 task가 `completed`로 바뀌는 순간 후속 task가 자동으로 `pending` → `ready`로 전환된다(`task-update --status completed`). Master나 팀장이 "됐다, 이제 시작해도 된다"를 수동으로 알려줄 필요가 없다.
2. **다음 일은 backlog에서 당겨온다(pull).** 워커 하나가 끝나 작업공간이 비면 `orca orchestration task-list --status ready --run <run_id> --json`으로 착수 가능한 task를 확인하고 곧바로 다음 워커를 배정한다. 1번의 DAG 덕분에 "이제 시작해도 되는 일" 목록이 항상 최신이므로, 매번 새 지시를 손으로 써 내려줄 필요가 없다. 팀장 계층을 쓸 때는 팀장이 같은 방식으로 자기 역할에 맞는 `ready` task를 스스로 집어간다.
3. **사용자 승인·판단이 필요한 막힘 지점은 `gate-create`로 명시화한다.** "불명확하면 물어봐라"를 프롬프트 문장으로만 두지 않는다. 유료 모델/서비스 전환, 파괴적 git 작업, 요구사항 모호로 진행이 막히는 시점에 `orca orchestration gate-create --task <task_id> --question "<질문>" --options '[...]'`로 Decision Gate를 만들어 명시적으로 블로킹 상태를 기록한다. Master는 `gate-list`로 열린 게이트를 훑어 사용자에게 전달하고, 사용자 결정을 `gate-resolve`로 반영한다.

### 6.1 역할별 지시사항

- **Research**: 기존 코드, 공식 문서, 외부 API, 데이터·기술 제약을 조사한다. 원칙적으로 코드를 수정하지 않으며 `research` 문서를 남긴다.
- **Planning/Architecture**: 사용자 시나리오, AC, 엣지 케이스, API·데이터 흐름, DB 영향, 리스크, SF 순서를 명확히 한다.
- **Test**: AC를 관찰 가능한 테스트로 바꾸고, 가능한 경우 구현 전에 실패를 확인한다.
- **Implementation**: 승인된 범위만 최소 구현한다. 기존 패턴을 재사용하고 work-log에 사실만 남긴다.
- **QA**: 단위·통합·E2E, 타입·정적 검사·빌드, 회귀 범위를 실제 명령 결과로 확인한다.
- **Review/Security**: 권한, 민감정보 로그, 입력 검증, 계획-구현 불일치, 테스트 누락, 스코프 밖 변경을 검토한다.
- **Git Manager**: 티켓·브랜치·PR·병합 상태를 진실 소스와 대조한다. GitHub Issue를 쓰는 프로젝트에서는 Orca 상태와 이슈 상태를 함께 갱신한다.
- **UI Vision**: 실제 화면, 반응형, 접근성, 핵심 사용자 흐름을 캡처·브라우저 검증한다.

## 7. 병렬화와 작업공간 규칙

### 7.1 병렬화 판단

기본 작업공간 셋(예: backend, frontend, planning)은 순서대로 하나씩 쓰는 큐가 아니라 병렬 처리 용량이다. Master는 새 작업을 배정할 때 한 작업공간만 습관적으로 선택하지 말고, 세 역할 전체의 준비된 작업을 먼저 확인한다. 서로 독립적인 조사·기획·구현·테스트·리뷰 작업이 둘 이상이면 역할별 동시 워커 한도와 실제 여유까지 한 번에 배정한다.

배정 직전에는 다음 절차를 따른다.

1. `ready` 상태의 작업을 역할별로 나열하고, 각 작업의 선행 의존성·수정 파일·필요 브랜치를 확인한다.
2. 서로 다른 파일을 다루며 선행 조건이 없는 작업은 모두 같은 배정 주기에 태스크와 워커를 만든다. 같은 브랜치에서 다른 파일만 작성하는 문서 작업은 한 작업공간에 여러 워커를 배정하고, 독립 PR·브랜치가 필요하면 필요한 수만큼 하위 worktree를 만든다.
3. `orca orchestration worker-list --json`으로 역할별 모든 `<역할>-N` 작업공간의 활성 워커 수와 프로젝트 안내서의 역할별 한도를 확인한 뒤, `min(독립 준비 작업 수, 남은 슬롯 수)`만큼 배정한다.
4. 워커가 끝나거나 중지되어 슬롯이 비면, 다른 워커가 모두 끝날 때까지 기다리지 않는다. 의존성과 충돌을 다시 확인한 뒤 다음 준비 작업으로 즉시 보충한다.

따라서 독립 문서·독립 기능·서로 다른 파일을 다루는 작업은 역할별 동시 워커 한도 안에서 한 번에 배정한다. 독립 준비 작업이 둘 이상인데 워커를 하나만 배정하려면, 의존성·파일 충돌·상위 결정 대기·검증된 용량 제한 중 어느 이유인지와 다음 재평가 시점을 work-log에 기록한다. 단일 작업을 여러 워커에게 중복 배정하거나 같은 파일을 동시에 수정해 겉보기 병렬성을 만드는 것은 금지한다.

같은 worktree를 여러 워커가 공유할 때는 각 워커의 수정 파일을 태스크에 명시하고, 워커는 배정 파일의 편집·검증만 한다. `git add`, `git commit`, 브랜치 전환, rebase는 공유 작업공간에서 동시에 수행하지 않는다. Master 또는 지정된 Git Manager 한 명을 통합 책임자로 정해, 모든 산출물을 검증한 뒤 순차로 stage·commit한다. 워커별 독립 커밋이 필요하면 해당 워커에 별도 worktree를 배정한다.

### 7.1.1 planning 선행 큐와 세 작업공간 점검

planning은 backend/frontend의 완료를 기다리는 후속 작업공간이 아니다. backend 또는 frontend가 Epic N을 구현 중이면, planning은 의존성을 해치지 않는 범위에서 최소 Epic N+1을, 가능하면 Epic N+2까지 조사·spec 보강·breakdown·엣지 케이스 검토 상태로 준비한다. 이 선행 기준은 Phase 0 승인과 SF 의존성 게이트를 생략하라는 뜻이 아니다. 다음 구현 워커가 이전 Epic 완료 직후 즉시 배정될 수 있도록 필요한 결정을 미리 끝내라는 뜻이다.

세 작업공간의 다음 작업 큐는 `current-status.md`의 다음 단계 후보에 최신으로 유지한다. planning의 신규 Epic 문서가 모두 준비됐더라도 비워 두지 않는다. 다음 Epic 관점의 기존 spec·architecture 드리프트 점검, breakdown 재검토, 사용자 가이드·QA 증적처럼 충돌 없는 기획 산출물로 큐를 채운다. 정말 시작 가능한 작업이 없을 때만 그 이유와 재평가 조건을 기록한다.

다음은 순차가 필수다.

- 같은 파일 또는 같은 문서를 여러 워커가 쓰는 경우
- 같은 브랜치에서 뒤 SF가 앞 SF의 코드를 이어 쓰는 경우
- 한 결정이 내려져야 후속 작업 범위가 확정되는 경우

워커 생성 직전에는 실제 활성 수를 확인한다.

```bash
orca orchestration worker-list --json
```

### 7.2 역할별 고정 작업공간

프로젝트는 기본적으로 backend, frontend, planning처럼 역할별 고정 작업공간을 유지하고, 독립 브랜치가 필요할 때만 `<역할>-2`, `<역할>-3` 임시 작업공간을 추가한다. 고정 작업공간의 정확한 이름·경로·한도는 프로젝트 안내서에 둔다.

한 Git worktree에는 한 브랜치만 체크아웃한다. 이미 워커가 작업 중인 작업공간에서 다른 작업용 `git checkout -b`를 실행하지 않는다. 다른 브랜치가 필요하면 별도 worktree를 만든다.

관련 작업공간을 만들 때는 프로젝트의 부모 관계 정책을 따른다. `--no-parent`는 독립 작업에만 사용하며, 역할별 하위 작업공간에 무심코 사용하지 않는다.

```bash
orca worktree create --repo id:<repoId> --name <역할>-2 --base-branch origin/main --json
orca worktree list --json
orca worktree set --worktree path:<새-작업공간-절대경로> --parent-worktree path:<부모-작업공간-절대경로> --json
orca terminal create --worktree path:<새-작업공간-절대경로> --title "<제목>" --json
orca terminal show --terminal <handle> --json
orca orchestration worker-start --task <task_id> --worktree path:<새-작업공간-절대경로> --agent <agent> --model <model> --json
```

터미널을 만든 뒤 셸에서 `cd`만 바꿔 Orca 소속을 바꾸려 하지 않는다. `terminal create`와 `worker-start` 모두에 같은 `--worktree path:<...>`를 넣고, `terminal show`로 실제 경로를 확인한다.

### 7.3 작업공간 정리

병합 확인 전에는 브랜치·작업공간·워커를 삭제하지 않는다. 병합 후에는 해당 SF의 워커 터미널을 같은 턴에서 닫는다. 고정 작업공간은 유지하고 임시 작업공간만 실제 종료 여부를 확인한 뒤 제거한다.

```bash
gh pr view <PR번호> --json state,mergedAt
orca orchestration worker-release --dispatch <dispatch_id> --json
orca terminal close --terminal <handle> --json
orca worktree rm --worktree path:<임시-작업공간-절대경로> --force --json
```

`worktree rm`은 기본 작업공간이 아닌 임시 `<역할>-N`에만 쓴다. 제거 결과는 `orca worktree list --json`과 실제 디렉터리 상태로 다시 확인한다.

## 8. Orca 오케스트레이션 실행 기준

명령 옵션은 설치된 Orca 버전에서 확인하고, 존재하지 않는 옵션을 추측하지 않는다.

```bash
orca skills list
orca skills installed --json
orca skills get <skill-name>
orca orchestration run-create --objective "<목표>" --from <코디네이터-핸들> --json
orca orchestration task-create --task-title "<제목>" --spec "<구체적 지시와 완료 조건>" --json
orca orchestration task-list --ready --brief
```

### 8.1 워커 생성과 모델 지정

모델 선택은 상속에 의존하지 않고 다음 둘 중 하나로 명시한다. 새 에이전트를 Orca가 시작하는 경로는 `worker-start --agent <agent> --model <model>`을 사용한다. 반대로 `terminal create --command '<agent> --model <model>'`로 모델을 고정한 기존 터미널을 배정하는 경로는 `worker-start --terminal <handle>`과 같은 worktree만 사용한다. `--terminal`과 `--agent`·`--model`을 함께 주지 않으며, 기존 터미널은 모델과 worktree가 맞는지 먼저 확인한다.

```bash
orca orchestration worker-start --task <task_id> --worktree <selector> --agent <agent> --model <model> --json
orca orchestration worker-show --dispatch <dispatch_id> --json
orca orchestration worker-list --json
orca orchestration worker-release --dispatch <dispatch_id> --json
orca orchestration worker-stop --dispatch <dispatch_id> --json
orca orchestration worker-abandon --dispatch <dispatch_id> --json
orca orchestration task-update --id <task_id> --status completed --result '<결과 JSON>' --json
```

프로젝트가 Claude Code 같은 에이전트를 터미널의 `--command`로 먼저 띄운 뒤 `--terminal`로 배정하는 방식이라면, 그 구체 절차는 프로젝트 안내서를 따른다. 한 worker가 어느 터미널·모델·작업공간에서 동작하는지 파일·명령 결과로 검증해야 한다.

### 8.2 배분과 워커 메시지

`--spec`에는 목적, 범위/비범위, 읽을 파일, 수정 가능한 파일, 검증 명령, 완료 조건, 보고 형식을 구체적으로 적는다. 무료 모델에 짧고 모호한 지시를 주지 않는다.

```bash
orca orchestration dispatch --task <task_id> --to <worker-핸들> --from <코디네이터-핸들> --dry-run --return-preamble --json
orca orchestration dispatch --task <task_id> --to <worker-핸들> --from <코디네이터-핸들> --inject --json
orca orchestration send --from <핸들> --type worker_done --subject "완료: <작업>" \
  --body "<무엇을 했고 / 무엇을 확인했고 / 무엇이 남았는지>" \
  --task-id <task_id> --dispatch-id <dispatch_id> --outcome succeeded --json
```

완료 보고에는 task ID, dispatch ID, 성공/실패, 3문장 요약을 넣는다. 실패는 `Failed: <이유>`로 즉시 알린다. 사람 결정이 필요할 때 로컬 TUI 질문 창을 쓰지 않고 `orchestration ask` 또는 gate를 사용한다.

### 8.3 완료 수집과 ACK

`orca orchestration check`는 메시지 큐를 소비할 수 있다. 단순 조회에는 `--peek`을 쓰고, 실제 검증·병합까지 끝낸 메시지는 같은 턴에 ACK한다. 오래된 미확인 배치가 있으면 새 완료를 기다리기 전에 처리한다.

```bash
orca orchestration check --terminal <코디네이터-핸들> --peek --json
orca orchestration check --terminal <코디네이터-핸들> --wait --types worker_done,escalation --timeout-ms 600000 --json
orca orchestration check --terminal <코디네이터-핸들> --ack <deliveryId> --json
orca orchestration task-list --json
```

`worker_done`이나 task 상태만으로 완료를 인정하지 않는다. diff, 테스트, 빌드, 문서, PR 병합 상태를 다시 확인한다.

### 8.4 정지·토큰 초과·재시도

`dispatched` 상태가 계속된다고 워커가 실제 동작 중인 것은 아니다. **`worker-list`의 `liveness: live`도 프로세스 생존만 의미한다** — OpenRouter 429로 화면에 에러만 뜬 채 멈춰도 `live`로 나온다(2026-09-28 실사고). 실제 정지 판정은 터미널 텍스트를 봐야 가능하다.

그 텍스트 확인을 사람이나 Claude가 매번 하면 회당 수천 토큰이 드므로, **셸 watchdog에 맡긴다**:

```bash
# 별도 터미널에서 한 번 띄워두고 세션 내내 돌린다
scripts/orca-worker-watchdog.py --loop 120
# 코디네이터는 원문 대신 이 요약만 읽는다
cat /tmp/orca-worker-status.txt
```

watchdog은 활성 워커의 터미널을 훑어 업스트림 에러 문구(`rate-limited`, `Upstream idle timeout`, `429`, `API Error` 등)와 무출력 정지(기본 8분)를 감지해 한 줄씩 적고, 실제 사용 중인 모델명도 같이 적는다(지정 모델과 다른 모델로 자동 폴백되는 사례가 반복 관찰됐다). **감지만 하고 재배정은 하지 않는다** — 오진으로 작업 중인 워커를 죽이는 비용이 자동화 이득보다 크기 때문에, 재배정 여부는 코디네이터가 판단한다.

유휴 작업공간을 찾을 때도 세 작업공간의 터미널을 일일이 읽지 말고 watchdog 상태 파일과 `task-list --status ready`를 먼저 본다.

```bash
orca terminal read --terminal <worker-핸들> --limit 50 --json
orca orchestration worker-read --dispatch <dispatch_id> --limit 50 --json
orca terminal list --worktree <selector> --json
orca diagnostics memory --json
```

세 기본 작업공간에는 각각 `orca terminal list --worktree path:<작업공간-절대경로> --json`을 실행한다. 활성 워커는 마지막 트랜스크립트와 파일 변경까지 확인하고, 비어 있는 작업공간은 7.1의 준비 작업·선행 planning 큐를 다시 계산해 같은 턴에 배정하거나 예외 사유를 기록한다.

watchdog의 감지 규칙은 실제로 놓친 사고에서 늘려 간다. 2026-09-30에는 ① 504 `Timeout Occurred`·`server_error` 문구가 ERROR 목록에 없었고, ② 스피너 재그리기로 `lastOutputAt`이 계속 갱신돼 무출력 정지에 도달하지 못했다. 그래서 ERROR 패턴에 504 계열을 추가하고, 하단 상태줄의 `esc interrupt` 문구가 stall 기준 시간 이상 보이지 않으면 STALL로 판정한다. 새 사고가 나면 그 문구를 selftest와 함께 추가한다.

멈춘 워커 복구 절차(재배정은 여전히 코디네이터가 판단한다): `worker-abandon --dispatch <id>` → `git status --short`로 부분 작업 확인(삭제하지 않는다) → `terminal close` → 다른 모델로 `terminal create` → 화면의 `Build · <모델명>` 줄이 지정한 모델인지 확인 → `worker-start --retry-of <이전 dispatch>` → spec 끝에 재개 문구(`docs/worker-spec-template.md`)를 덧붙인다.

오류·무응답이 보이면 먼저 파일 변경과 마지막 트랜스크립트를 확인한다. 컨텍스트 초과가 명확할 때만 `/compact` 후 같은 태스크를 재주입한다. 같은 범위에서 세 번 이상 재개 실패하면 더 작은 SF로 분해하거나 `BLOCKED`로 전환한다. 이미 생성된 파일은 원인 확인 전 삭제하지 않는다.

```bash
orca terminal send --terminal <worker-핸들> --text "/compact" --enter --json
orca orchestration dispatch --task <task_id> --to <worker-핸들> --from <코디네이터-핸들> --inject --json
```

### 8.5 사람 승인 게이트

```bash
orca orchestration gate-create --task <task_id> --question "<결정할 질문>" --options '["옵션1","옵션2"]' --json
orca orchestration gate-list --status pending --json
orca orchestration gate-resolve --id <gate_id> --resolution "<선택>" --json
```

게이트 해결 뒤에는 결정 내용을 실행 워커에 다시 전달하고, 반영 결과를 리뷰한다.

`orca orchestration reset`은 로컬 오케스트레이션 DB 전체를 지울 수 있으므로 사용하지 않는다. 개별 태스크·워커·터미널만 명시적으로 정리한다.

## 9. 무료 모델과 비용 운영

기본값은 무료 모델과 읽기 전용 도구다. OpenRouter 무료 모델은 반드시 OpenCode 경로로만 실행한다. 정확한 모델 ID, 가용성, 역할별 기본 배정은 프로젝트 안내서에 기록한다.

- 모델은 실행 직전에 현재 무료 여부와 도구 호출 가능 여부를 확인한다.
- 일일 무료 한도·일시 장애·역할 부적합을 구분한다. 단순 429/5xx와 영구 비호환을 같은 실패로 기록하지 않는다.
- 모델을 바꾸면 이전 모델, 새 모델, 사유, 영향 작업을 work-log에 남긴다.
- **OpenRouter 일일 한도 소진**(`Rate limit exceeded: free-models-per-day-high-balance`)은 시간이 지나도 풀리지 않으므로 재시도하지 않고 OpenCode Zen 무료 모델(`opencode models`의 `opencode/*-free` 등)로 전환한다. 전환 전에 후보마다 한 요청(`opencode run --model <id> "OK만 답해"`)으로 실제 작동을 확인한다 — Zen 모델도 `Endpoint is unavailable`로 죽어 있을 수 있다.
- **사용자가 모델 계열이나 경로를 제외하라고 지시하면 그 지시가 위 자동 전환보다 우선한다**(예: 2026-09-30 "오늘은 네모트론 라인은 빼고"). 지시가 이전 결정과 달라져도 최신 지시를 따르고 work-log에 남긴다.
- **잘못된 모델 ID는 오류 없이 다른 모델(Laguna 등)로 폴백된다.** `opencode models`로 ID를 확인하고, 터미널을 띄운 뒤 `Build · <모델명>` 줄이 지정한 모델인지 확인한 다음 worker-start 한다. 같은 시간대에 같은 모델을 두 워커에 배정하지 않는다.
- 한 프로젝트에서 검증된 모델 목록도 시간이 지나면 오래될 수 있다. 최신 카탈로그를 확인한 뒤 사용한다.
- 유료 모델·서비스 전환은 사용자 명시 승인 없이는 하지 않는다.

예시 확인 명령:

```bash
opencode models
opencode models openrouter
curl -s "https://openrouter.ai/api/v1/models"
```

## 10. 문서 규칙과 이어하기

### 10.1 문서 구조

```text
docs/
├── roadmap.md
├── current-status.md
├── setup-history.md
├── project-agent-guide.md
├── research/[epic-slug].md
├── spec/[epic-slug].md
├── architecture/[epic-slug].md
├── breakdown-research/[epic-slug].md
├── breakdown/[epic-slug].md
├── work-plan/[epic-slug].md
├── work-log/[epic-slug].md
├── handoff/[epic-slug].md
└── screenshots/[epic-slug]/YYYY-MM-DD-[description].png
```

같은 Epic의 slug는 문서·브랜치·티켓에서 일관되게 쓴다.

| 문서 | 반드시 담을 내용 |
|---|---|
| research | 질문, 근거, 발견, 제약, 미해결 질문 |
| spec | 사용자 목표, 시나리오, 기능 요구사항, 엣지 케이스, AC |
| architecture | 데이터 흐름, API·DB·모듈 경계, 의존성, 리스크 |
| breakdown | SF 범위·순서·의존성·AC·대응 티켓 |
| work-plan | 목표·범위·비범위·관련 파일·테스트·완료 조건·리스크 |
| work-log | 실제 실행한 작업·명령·결과·문제·다음 단계 |
| handoff | 현재 상태·남은 작업·재현법·수정 파일·주의사항 |

`work-log` 파일은 append-only다. 기존 내용을 덮어쓰지 않는다. `current-status`는 최신 스냅샷이므로 매번 덮어쓴다. 일일 로그의 사용 여부·저장 위치·파일명·형식은 프로젝트 안내서에 따른다. `setup-history`에는 새 설치·마이그레이션 이력과 재현 가능한 복구 절차를 남기며, 시크릿은 쓰지 않는다.

### 10.2 세션 종료 규칙

세션 종료 전에는 활성 Epic·SF·브랜치·작업공간·블로커·다음 3~5개 작업을 `current-status.md`에 반영한다. 프로젝트 안내서가 일일 로그를 정의하면 그 형식에 맞춰 갱신한다. 컨텍스트 압축 뒤에는 30초 카드 → current-status → handoff → 관련 work-log 순으로 복구한다.

## 11. 티켓·Git·PR 규칙

### 11.1 티켓

티켓은 하나의 명확한 완료 목표, 범위, 변경 대상, AC를 가진다. Epic과 SF는 부모-자식 관계로 연결하고, 의존 SF는 `--deps`로 표현한다. 완료된 티켓은 삭제하지 않고 close해 이력을 보존한다.

```text
PLANNED → IN_PROGRESS → READY_FOR_REVIEW → CHANGES_REQUIRED → APPROVED → COMPLETED
```

GitHub Issue 병행 여부는 프로젝트 안내서에 명시한다. 사용하기로 했으면 Orca 티켓 상태만 바꾸고 끝내지 않으며 Git Manager가 GitHub 상태도 확인한다.

### 11.2 Git 안전 규칙

작업 전과 병합 전에는 다음을 확인한다.

```bash
git status --short --branch
git log --oneline -10
git diff --stat
git diff --check
```

승인 없이 `git reset --hard`, `git clean -fd`, `git checkout -- <file>`를 실행하지 않는다. 기존 미커밋 변경은 사용자 작업으로 간주한다. 원격 push·merge·rebase의 세부 승인 정책은 프로젝트 안내서를 따른다.

커밋은 현재 티켓의 변경만 포함하며, 커밋과 PR에 티켓을 연결한다.

```text
feat: <기능> (#<issue>)
fix: <문제> (#<issue>)
test: <검증 추가> (#<issue>)
```

### 11.3 PR과 충돌

PR에는 목적, 주요 변경, 테스트 결과, 주의사항, 남은 작업을 기록한다. 병합 뒤에는 실제 병합 여부를 확인하고 해당 워커를 정리한다.

두 에이전트가 같은 파일을 수정하면 작업을 멈추고, 파일·각 변경·선택 방향·사용자 확인 필요 여부를 `docs/agent-conflicts.md`에 기록한다. 임의로 다른 워커의 변경을 덮어쓰지 않는다.

## 12. 구현·테스트·QA 기준

### 12.1 테스트 우선 흐름

```text
Acceptance Criteria → 테스트 작성 → 실패 확인 → 최소 구현 → 테스트 통과 → 리팩터링 → 전체 검증
```

프론트엔드는 핵심 사용자 행동·상태 변화·데이터 표시·접근성을, 백엔드는 업무 규칙·입력 검증·상태 전이·권한·예외·트랜잭션을 우선 검증한다. 기존 테스트 구조·도구를 먼저 읽고 재사용한다.

### 12.2 프론트엔드 공통 기준

프로젝트가 채택한 프레임워크·상태 관리·패키지 매니저·린터·포매터를 따른다. 서버 상태와 클라이언트 UI 상태를 구분하고, 실제 설치 버전의 공식 API를 우선한다. 모든 신규 화면은 처음부터 반응형·키보드 접근성·오류·로딩·빈 상태를 고려한다. 정확한 구조·브레이크포인트·검증 명령은 프로젝트 안내서에 둔다.

### 12.3 백엔드 공통 기준

Controller는 요청 검증·응답 매핑·서비스 호출에 집중하고, Service는 유스케이스·업무 규칙·트랜잭션 경계를 담당한다. Entity/Domain은 상태와 불변식을 지키며, DTO로 외부 계약을 분리한다. 민감정보를 로그에 남기지 않고 모든 조회·변경에 테넌트/권한 경계를 적용한다. 사용 DB의 문법과 마이그레이션 규칙은 프로젝트 안내서를 따른다.

### 12.4 검증과 완료 정의

실제로 존재하는 검증 명령만 실행한다. 완료 전에 최소한 관련 테스트, 타입/정적 검사, lint/format, build, `git diff --check`, 보안·권한 검토를 확인한다. 프로젝트별 명령은 안내서에 적는다.

어떤 SF도 아래를 만족하기 전 완료 처리하지 않는다.

- 요구사항·범위·AC가 명확함
- 필요한 조사·설계·테스트 계획이 완료됨
- 구현과 필요한 테스트가 존재하고 실제 검증을 통과함
- QA·리뷰·보안 검토가 완료됨
- 문서·handoff·상태가 최신이며 남은 BLOCKED가 없음
- PR 병합 정책을 충족하고, 병합 후 워커 정리가 확인됨

## 13. BLOCKED와 보고

다음 상황에서는 임의로 진행하지 않는다: 요구사항 충돌, 필요한 정보·권한 부족, 파괴적 변경 승인 필요, 운영 데이터 영향, 사용자 변경과 충돌, 테스트 환경 부재, 동일 실패 반복, API·보안 영향 검증 불가.

BLOCKED 보고에는 원인, 시도한 작업, 실패 명령·오류, 영향 범위, 가능한 선택지, 사용자 결정 사항을 포함한다. 외부 알림을 설정한 프로젝트는 프로젝트 안내서의 알림 절차를 따른다.

## 14. 프로젝트 전용 안내서의 책임

`docs/project-agent-guide.md`에는 다음처럼 재사용하면 안 되는 정보를 둔다.

- 제품명·도메인·작업 계획서 경로·현재 roadmap 상태
- 실제 저장소·작업공간 경로, 브랜치명, Issue 라벨·병합 예외
- 언어·프레임워크·패키지 매니저·정확한 테스트/빌드 명령
- Claude Code, OpenRouter, OpenCode의 모델 ID·게이트웨이·무료 할당량·관찰 기록
- 이 프로젝트에서 검증한 오류 회피법, 알림·사용자 가이드 운영

공통 규칙을 바꿀 때는 이 문서를 먼저, 프로젝트 고유 사실을 바꿀 때는 프로젝트 안내서를 먼저 갱신한다.
