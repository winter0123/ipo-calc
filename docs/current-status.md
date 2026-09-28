# 프로젝트 현재 상황 (Master 소유, 계속 최신화)

> 마지막 갱신: 2026-09-28 세션 종료 시점(3차 재개 세션). **로드맵 16개 Epic 전체 구현 완료.** 워크스페이스 경로는 이 문서를 믿지 말고 `orca worktree list --json`으로 재확인한다.

## 세션 요약 (2026-09-28, 3차 재개 — "코디네이터 1명 + 병렬 워커" 구조 첫 실전 운영)

`AGENTS.md` 6.0절 구조(Master 1명 + OpenCode 무료 모델 워커 N개)로 세션을 시작해, 대시보드 집계 API(Epic 12) SF-2~4와 웹 조회·대시보드(Epic 16) SF-2~5를 순차로 완료했다. **이로써 로드맵 16개 Epic 전체가 구현 완료됐다.** 상세 PR 목록과 이슈·트러블슈팅은 `docs/daily-log/2026_09_28.md`의 세 번째 세션 섹션("3차 재개 세션 — ... 로드맵 16개 Epic 전체 완료")을 진실 소스로 한다 — 이 문서에는 중복 기록하지 않는다.

세션 중 사용자 결정으로 반영한 것: ① checkstyle(Google Java Style) 플러그인을 이번 세션에 추가(PR #170) — 기존 코드 34개 파일 6,940건 위반은 범위 밖으로 분리해 뒀다(아래 "알려진 부채" 참고). ② OpenRouter 일일 무료 한도 소진 시 워커 전체를 OpenCode Zen 경로로 전환하는 절차를 실전 적용했다(project-agent-guide.md 5.2절 그대로 동작 확인).

## 로드맵 진행 현황 (2026-09-28 세션 종료 시점)

**완료: Epic 1~16 전체.** GitHub Issue #128(Epic 9)·#129(Epic 10)·#130(Epic 11)·#131(Epic 12)·#132(Epic 15)·#133(Epic 16) 모두 close 확인.

로드맵에 정의된 16개 Epic의 최초 구현은 이번 세션으로 끝났다. 다음 세션은 신규 기능 착수가 아니라 아래 "알려진 부채·다음 세션 후보"에서 우선순위를 사용자와 정하는 것으로 시작한다.

## 알려진 부채·다음 세션 후보 (착수 전 사용자 우선순위 확인 필요)

1. **checkstyle 기존 위반 6,940건 정리 여부**: `checkstyleMain`/`checkstyleTest`는 `check`/`build` 라이프사이클에 안 걸려 있어 당장 빌드를 막지는 않지만, 방치하면 신규 코드에도 검증 없이 위반이 쌓인다. 전체 정리(대규모 diff, 리스크 있음) vs. 신규/수정 파일만 점진적으로 정리 vs. 방치 중 사용자 결정 필요.
2. **Epic 16 남은 사용자 결정 3건** (`docs/work-plan/web-inquiry-dashboard-sf2-task-draft.md` 등에 기록됨):
   - 투자 내역 목록/상세에 투자자명·증권계좌 표시가 필요한지 — 필요하면 백엔드 DTO 확장 또는 별도 조회 API가 있어야 한다(현재 `InvestmentHistoryDto` 응답에 해당 필드 없음).
   - `ui/src/shared/components/`에 공용 Button/Input/FormField/ErrorMessage/Spinner를 만들지, 계속 인라인 패턴으로 갈지 — 여러 SF에서 반복적으로 "재사용 지시했지만 실제로는 없다"는 드리프트가 발견됐다.
   - 비용 등록 화면의 성공 후 이동(navigate) 처리가 아직 없다(SaleRegisterForm은 PR #168로 `/investments`가 해소됨, CostRegisterForm은 미해결).
3. **GitHub Issue #134 관련 공통 수정**: 여러 Epic 15/16 draft가 "별도 SF-0로 분리 여부 미정"으로 남겨둔 항목이다. Epic 15 SF-0(PR #147)에서 `apiFetch` 자체는 이미 수정됐으므로, 남은 범위가 실제로 있는지부터 재확인이 필요할 수 있다.
4. **Epic 16 접근성(axe-core)·E2E 시나리오 실측 검증**: 이번 세션 워커들은 코드 레벨로 접근성 속성(aria-*)을 구현했다고 보고했지만, 실제 브라우저에서 axe-core 스캔이나 E2E 시나리오를 돌려 확인한 적은 없다. 웹 UI QA 패스가 필요하다.
5. **팀장(Lead) 계층 재사용 여부**: AGENTS.md 6.0.2절에 "마감 촉박·다도메인 동시 진행 시 선택지"로 강등돼 남아 있다. 이번 세션은 6.0(코디네이터+병렬 워커) 단독으로 8개 PR(SF)을 무리 없이 처리했다 — 특별한 이유가 없으면 계속 6.0을 기본으로 쓴다.

## 워크스페이스 상태 (2026-09-28 세션 종료 시점)

- `backend-4`(`/Users/joy/orca/workspaces/vibe-ai-team/backend-4`): 마지막 브랜치 `winter0123/dashboard-aggregation-api-sf4`(병합 완료, PR #174). 다음 세션 착수 시 `git checkout -B <새 브랜치> origin/main`으로 새로 시작할 것 — 이 브랜치를 이어 쓰지 않는다.
- `frontend-4`(`/Users/joy/orca/workspaces/vibe-ai-team/frontend-4`): 마지막 브랜치 `winter0123/dashboard-sf5`(병합 완료, PR #178). 동일하게 다음 세션은 origin/main 기준 새 브랜치로 시작.
- `planning-4`(`/Users/joy/orca/workspaces/vibe-ai-team/planning-4`): 마지막 브랜치 `winter0123/user-guides-catchup`(병합 완료, PR #172).
- `backend-5`(임시): checkstyle 설정 작업 후 병합 확인하고 세션 중 제거함(`orca worktree rm`).
- 세션 종료 시점 모든 워커 dispatch가 `completed`/`retained`로 정산됐고 `worker-list --terminal-state reclaimable`은 0건이다. watchdog(`scripts/orca-worker-watchdog.py`)은 세션 종료와 함께 중지했다 — 다음 세션 시작 시 다시 `--loop 120`으로 띄운다.
- Master 메인 체크아웃(`/Users/joy/IdeaProjects/vibe-ai-team`)은 이번 세션 중 origin/main과 한 번 분기됐던 로컬 전용 커밋(908203c, 문서 전용)을 발견해 병합·재푸시로 정합성을 회복했다. 다음 세션 시작 시 `git status`/`git log`로 재확인할 것.

## 모니터링·운영 방식 (변경 없음, 유지)

- `check --wait`의 `--timeout-ms`는 900000(15분) 이상. 터미널 원문을 반복해서 읽지 않고 watchdog 상태 파일만 본다. 상세 절차는 `docs/project-agent-guide.md` 6.1절.
- 세션 재개(`/low-priority` 등)로 프로세스 incarnation이 바뀌면 `orca orchestration run-use --id <run_id>`로 재바인딩한다 — 이번 세션에 실제로 필요했다.
- OpenRouter 일일 무료 한도 소진(`free-models-per-day-high-balance`)이 확인되면 개별 모델 재시도 대신 워커 전체를 OpenCode Zen(`opencode/<모델>`) 경로로 일괄 전환한다.
