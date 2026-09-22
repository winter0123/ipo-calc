# AGENTS.md — Orca 멀티 에이전트 개발 공통 헌법

> 이 문서는 Orca로 여러 AI 에이전트를 운영하며 소프트웨어를 개발하는 모든 프로젝트에서 재사용하는 공통 규칙이다. 프로젝트 고유 기술 스택, 경로, 모델 ID, 현재 진행 상황, 특정 CLI의 관찰 결과는 이 파일에 누적하지 않고 `docs/project-agent-guide.md`에 둔다. 해당 파일이 있으면 작업 시작 전에 이 문서 다음으로 읽는다.

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

`dispatched` 상태가 계속된다고 워커가 실제 동작 중인 것은 아니다. 모니터링 중에는 터미널 또는 트랜스크립트를 직접 읽는다. Monitor 알림 수신, `dispatched` 상태 반복, 사용자 상태 질문, 정기 진행 보고 때마다 현재 완료를 기다리는 역할만 확인하지 않는다. backend·frontend·planning의 터미널·워커·다음 큐를 모두 확인해 유휴 작업공간과 즉시 배정 가능한 작업을 찾는다.

```bash
orca terminal read --terminal <worker-핸들> --limit 50 --json
orca orchestration worker-read --dispatch <dispatch_id> --limit 50 --json
orca terminal list --worktree <selector> --json
orca diagnostics memory --json
```

세 기본 작업공간에는 각각 `orca terminal list --worktree path:<작업공간-절대경로> --json`을 실행한다. 활성 워커는 마지막 트랜스크립트와 파일 변경까지 확인하고, 비어 있는 작업공간은 7.1의 준비 작업·선행 planning 큐를 다시 계산해 같은 턴에 배정하거나 예외 사유를 기록한다.

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

기본값은 무료 모델과 읽기 전용 도구다. 정확한 모델 ID, 가용성, 역할별 기본 배정, Claude/OpenRouter 전환법은 프로젝트 안내서에 기록한다.

- 모델은 실행 직전에 현재 무료 여부와 도구 호출 가능 여부를 확인한다.
- 일일 무료 한도·일시 장애·역할 부적합을 구분한다. 단순 429/5xx와 영구 비호환을 같은 실패로 기록하지 않는다.
- 모델을 바꾸면 이전 모델, 새 모델, 사유, 영향 작업을 work-log에 남긴다.
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
