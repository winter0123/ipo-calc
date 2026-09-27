# 프로젝트 현재 상황 (Master Lead 소유, 계속 최신화)

> 마지막 갱신: 2026-09-23 (세션 재개 직후) — PR #143까지 반영, 워크스페이스 2차 소실 확인 및 재생성. 다음 세션 시작 전에는 `orca worktree list --json`, `orca terminal list --json`, `orca orchestration worker-list --json`으로 실제 live 상태를 다시 확인한다.

## ⚠️ 2026-09-23 세션 시작 시 발견: 고정 워크스페이스 2차 소실 (재발)

이전 세션(2026-09-22) 종료 후 다시 `/Users/joy/orca/workspaces/vibe-ai-team/` 디렉터리가 비었다(`.orca-worktree-trash`도 빈 상태). `orca worktree list --json`에도 vibe-ai-team 하위 워크스페이스가 전혀 등록되어 있지 않았다(main 체크아웃과 무관한 다른 두 저장소만 조회됨). 2026-09-22에도 동일 현상이 있었고 원인은 이번에도 미상이다(Orca 자체 이슈로 추정, 이 세션이 삭제하지 않음).

**영향 범위 확인**: `orca orchestration worker-list --json`(최근 100건)을 확인한 결과 진행 중이던 3개 워커(backend-2의 Epic 9 SF-2, backend-3-2의 Epic 11 SF-2, planning-3의 Epic 16 breakdown)가 모두 `failed`/`retained`로 정산되어 있었고 reclaimable 워커는 0건이었다. `git branch -r`로 실제 원격 브랜치 상태를 대조한 결과:
- `winter0123/cost-recording`: SF-1 커밋만 존재 — **Epic 9 SF-2(CostController/Service/DTO/테스트) 미커밋 작업 전량 유실**
- `winter0123/investment-query-api-sf1`: SF-1 커밋만 존재 — **Epic 11 SF-2(InvestmentHistoryController/QueryService/테스트) 미커밋 작업 전량 유실**
- Epic 16(web-inquiry-dashboard) breakdown: 브랜치 자체가 origin에 없음 — **작성 중이던 내용 전량 유실**
- 반면 **PR #143(Epic 15 breakdown)은 이미 병합 완료**되어 있었다 — 이전 세션이 종료 직전 이 작업만은 병합까지 끝냈던 것으로 확인됨. current-status.md가 그 사실을 반영하지 못한 채 종료되어 있었다(세션 중간에 끊긴 것으로 추정).
- main 체크아웃에는 이전 세션이 마지막에 남긴 미커밋 문서 수정(AGENTS.md/project-agent-guide.md/ai-free-model.md/current-status.md/roadmap.md/prompt.md)이 작업 트리에 그대로 남아 있었다(git 자체는 무손상) — 이번 세션이 `git merge --ff-only origin/main`으로 origin 최신(PR #143까지)과 합치고 그 위에 이 갱신을 커밋한다.

**결론**: git에 push된 작업은 이번에도 100% 무손상. 유실은 항상 "워커가 파일은 썼지만 아직 커밋하지 않은 상태에서 워크스페이스가 사라진 경우"에만 발생한다. **재발 방지책(제안, 사용자 확인 필요)**: 워커가 파일 단위 작업을 완료하는 즉시(전체 SF 완료를 기다리지 않고) WIP 커밋을 주기적으로 남기도록 지시를 강화하는 방안을 고려할 것 — 이번 세션에서는 우선 유실된 3개 작업을 처음부터 재배정한다.

**조치**: `origin/main`(PR #143 포함) 기준으로 4개 고정 워크스페이스를 재생성했다. 아래 "워크스페이스 구조" 표에 실제 경로를 최신화한다.

## 진행 중 워커 (2026-09-23, 이번 세션 — Run `run_84af99a11878`)

- **backend** (`/Users/joy/orca/workspaces/vibe-ai-team/backend-2`, 브랜치 `winter0123/cost-recording`): Epic 9 SF-2 비용 등록 API 재착수(이전 미커밋 작업 유실로 처음부터), **OpenCode**+OpenRouter `nvidia/nemotron-3-ultra-550b-a55b:free`, dispatch `ctx_0dec97cba0ec`, task `task_023073a68744`, terminal `term_e065240a-a246-4fbb-afbb-f556c3bc67e7`
- **backend-3** (`/Users/joy/orca/workspaces/vibe-ai-team/backend-3-3`, 브랜치 `winter0123/investment-query-api-sf1`): Epic 11 SF-2 투자 내역 전체 조회 API 재착수, **OpenCode**+OpenRouter `nvidia/nemotron-3-ultra-550b-a55b:free`, dispatch `ctx_1734e746ae1a`, task `task_d2c7315a2c15`, terminal `term_11aede01-21a3-4bc5-8f4c-e3cfcf522533`
- **planning** (`/Users/joy/orca/workspaces/vibe-ai-team/planning-3`, 브랜치 `winter0123/web-inquiry-dashboard-breakdown`): Epic 16 breakdown 재착수, **OpenCode**+OpenRouter `nvidia/nemotron-3-super-120b-a12b:free`, dispatch `ctx_620115cd1ce8`, task `task_e85940d48ee2`, terminal `term_01c4ca30-ceed-4aaa-bc25-92c95b117b8c`
- **frontend** (`/Users/joy/orca/workspaces/vibe-ai-team/frontend-3`, 브랜치 `winter0123/web-sale-profit-workflow-sf0`): Epic 15 SF-0(공통 apiFetch 에러 처리 수정, Issue #134) 신규 착수 — **사용자 결정(2026-09-23): 별도 선행 SF-0으로 처리**(roadmap.md 8절 갱신 완료), **OpenCode**+OpenRouter `nvidia/nemotron-3-ultra-550b-a55b:free`, dispatch `ctx_b07bd568ab7d`, task `task_d9f76f155e4c`, terminal `term_de4e7862-4855-4917-8d68-85fe1fe726aa`

cost-recording/investment-query-api-sf1 브랜치는 모두 이번 세션에 `git rebase origin/main` + `--force-with-lease` push로 최신화했다(SF-1 커밋은 이미 main에 스쿼시 병합되어 있어 rebase 시 자동 스킵됨 — 실질적으로 origin/main과 완전히 동일한 지점에서 재시작).

## ⚠️ 2026-09-22 세션 시작 시 발견: 고정 워크스페이스 소실 및 재생성

이전 세션 종료 후 `backend`, `frontend`, `planning`, `backend-3` 네 워크스페이스가 Orca 등록에서 사라지고 `/Users/joy/orca/workspaces/vibe-ai-team/` 디렉터리가 비어 있었다(`.orca-worktree-trash`도 비어 있어 복구 불가). 원인은 미상이며 이 세션이 삭제한 것은 아니다. git 쪽은 무손상 확인(모든 관련 브랜치가 로컬·origin에 그대로 존재, `gh pr list --state merged`와 current-status.md 기록이 100% 일치) — 실제로 유실된 작업은 없었다.

**조치**: `origin/main` 기준으로 워크스페이스를 재생성했다. Orca가 기존 이름 카운터를 기억하고 있어 `backend`, `planning`, `frontend`라는 이름 대신 다음 실제 경로가 생성됐다(표시 이름은 계속 backend/planning/frontend로 고정됨):

| 역할 | 실제 경로(신규) | 브랜치 |
|---|---|---|
| backend | `/Users/joy/orca/workspaces/vibe-ai-team/backend-2` | `winter0123/sale-management-cancel` |
| planning | `/Users/joy/orca/workspaces/vibe-ai-team/planning-3` | `winter0123/web-sale-profit-workflow-breakdown` |
| frontend | `/Users/joy/orca/workspaces/vibe-ai-team/frontend-2` | `winter0123/frontend-2` (아직 작업 없음, 유휴) |
| backend-3 | 아직 미생성 (Epic 8 병합 후 Epic 9/11 병렬 착수 시점에 재생성 예정) |  |

**다음 세션 유의사항**: `docs/project-agent-guide.md`의 워크스페이스 경로 표(5.2절)가 `backend`/`frontend`/`planning`(접미사 없음)로 돼 있으나 실제로는 위 표의 `-2`/`-3` 접미사 경로를 써야 한다. `orca worktree list --json`으로 실제 경로를 재확인하지 않고 안내서의 경로를 그대로 신뢰하지 않는다.

## 진행 중 (2026-09-22, 이번 세션)

- **PR #140 병합 완료**: Epic 8(sale-management) SF-5 매도 취소 API. Epic 8 전체 완료. GitHub 이슈 #126 close.
- **backend-2** (`/Users/joy/orca/workspaces/vibe-ai-team/backend-2`, 브랜치 `winter0123/cost-recording-sf2`): Epic 9 SF-2 CostControllerTest 작성 중, **OpenCode**+OpenRouter `poolside/laguna-s-2.1:free`, dispatch `ctx_82d594f0ddb9` (task_4c7b866aee0f)
- **backend-3-2** (`/Users/joy/orca/workspaces/vibe-ai-team/backend-3-2`, 브랜치 `winter0123/investment-query-api-sf2`): Epic 11 SF-2 InvestmentHistoryControllerTest/QueryServiceTest 작성 중, **OpenCode**+OpenRouter `poolside/laguna-s-2.1:free`, dispatch `ctx_a0d927d8de4d` (task_ece0f48225ef)
- **planning-3** (`/Users/joy/orca/workspaces/vibe-ai-team/planning-3`, 브랜치 `winter0123/web-inquiry-dashboard-breakdown`): Epic 16 breakdown 작성 중, **OpenCode**+OpenRouter `nvidia/nemotron-3-super-120b-a12b:free`, dispatch `ctx_5b9574d2bbcc` (task_740a9428def3)
- **frontend-2** (`/Users/joy/orca/workspaces/vibe-ai-team/frontend-2`): 유휴. Epic 15 breakdown 완료·승인 + GitHub Issue #134 결정 후 착수 예정.

**정정(2026-09-22)**: `backend-3-2` 생성 시 `--parent-worktree`를 실수로 main이 아닌 `backend-2`로 지정해 backend-3이 backend의 하위 worktree로 잘못 등록됐었다. 실제 작업(git 브랜치/커밋)에는 영향 없었으며, `orca worktree set --parent-worktree path:/Users/joy/IdeaProjects/vibe-ai-team`으로 main의 형제 관계로 즉시 정정했다.

**사용자 지시(2026-09-22)**: 이제부터 새로 만드는 모든 워커는 OpenCode+OpenRouter 경로(`opencode --model openrouter/<provider>/<model>:free`)로 생성한다. `docs/project-agent-guide.md` 6장도 이 순서로 갱신됨(6.2 OpenCode 경로가 1순위, 6.3 Claude Code+OpenRouter 직접 경로가 2순위).

## PR 병합 이력 (이번 세션, 2026-09-22)
- PR #140: Epic 8 SF-5(매도 취소 API) — Epic 8 완료, 이슈 #126 close
- PR #141: Epic 9 SF-1(Cost 엔티티/Repository/마이그레이션)
- PR #142: Epic 11 SF-1(투자내역조회 기반 계층)
- **사용자 결정(2026-09-22)**: 취소된 청약도 투자 내역에 표시함(상태값으로 구분) — roadmap.md 8절 갱신 완료

## 진행 중 워커 (최신, 2026-09-22 07:52 KST - Poolside rate limit으로 재디스패치)
- backend-2: Epic 9 SF-2 CostControllerTest, **OpenCode**+OpenRouter (model auto), dispatch `ctx_dc72f6d72d59`, task_4c7b866aee0f, terminal `term_6c3bbbe9-d9fe-4247-9404-06d0072fb131` — **활성 작업 중 확인됨** (스피닝 애니메이션)
- backend-3-2: Epic 11 SF-2 InvestmentHistoryControllerTest/QueryServiceTest, **OpenCode**+OpenRouter (model auto), dispatch `ctx_557d275a2e4e`, task_ece0f48225ef, terminal `term_9f7f755b-6793-4ddc-82dc-59607b122ad6` — **활성 작업 중 확인됨** (스피닝 애니메이션)
- planning-3: Epic 16 breakdown, **OpenCode**+OpenRouter (model auto), dispatch `ctx_28fd283babc2`, task_740a9428def3, terminal `term_0a815a70-c4c5-429d-8b10-1c400ee379e2` — **활성 작업 중 확인됨** (문서 읽기 시작, Poolside 재시도 중이나 읽기 진행 중)
- frontend-2: 유휴

**정리 완료**: 이전 Poolside rate limit 걸린 3개 워커(`ctx_82d594f0ddb9`, `ctx_a0d927d8de4d`, `ctx_5b9574d2bbcc`)는 abandon + release 처리함. 구 워커 터미널들은 retained 상태이나 새 디스패치가 별도 터미널로 정상 작동 중.

## 세션 종료 시점 요약 (2026-09-22, 이전 세션분 — 아래는 과거 기록)

이번 세션에서 병합된 것: PR #135(Epic8 SF-3), #136(Epic10 breakdown), #137(Epic14 SF-3, Epic14 전체 완료), #138(Epic12 breakdown), #139(Epic8 SF-4). Epic 14는 이슈 #127까지 정상 close됨.
**알아둘 것**: 3건(Epic10 breakdown, Epic12 breakdown, Epic8 SF-4) 모두 워커가 실제 작업은 끝냈지만 `worker_done`을 정상 전송하지 못했다(디스패치 capability 오류 1건, tool-call 오용 1건, 명령 미실행 1건) — Master가 diff/테스트/문서 내용을 직접 재검증한 뒤 수동으로 completed 처리하고 병합했다. 자동 보고를 못 받는 사례가 이번 세션에서 3번 반복됐으니, 다음 세션에서도 `check --wait` 타임아웃 시 `worker-read --dispatch <id> --json`으로 트랜스크립트를 직접 확인하는 습관을 유지한다.
**다음 세션 시작 시 바로 할 일**: Epic 8 SF-5(취소 API)를 backend에 배정하고, planning은 Epic 15 breakdown을 시작한다. Epic 8이 병합되면 Epic 9와 Epic 11은 서로 독립이므로 별도 backend worktree에서 병렬 착수한다. frontend는 Epic 15 breakdown 승인 후 화면 구조·mock·테스트 계획을 시작한다.

## 📌 GitHub Issue를 Orca 태스크와 병행 운영 중 (2026-09-22부터)

`docs/project-agent-guide.md`의 GitHub 운영 규칙에 따라 이 프로젝트는 Orca 태스크(실행 단위)와 별개로 GitHub Issue(사람이 보는 진행 현황판)를 함께 쓴다. Epic마다 이슈 1개, 라벨은 `status:planned/in-progress/review/changes-requested/approved`, 완료되면 close(라벨 제거). 이슈 #35~#133 정리 완료, 이후 Epic 상태가 바뀔 때마다 그 즉시 이슈도 갱신한다.
**주의**: PR 본문에 부분 완료(SF 일부만 끝난 상태)인데 `Closes #`를 쓰면 GitHub가 Epic 이슈를 오토클로즈해버린다(2026-09-22, PR #135에서 실제로 발생해 Epic 8 이슈 #126이 잘못 닫힘 → 바로 reopen). **Epic이 완전히 끝나지 않았으면 PR 본문에 `Part of #<이슈번호>`만 쓰고 `Closes #`는 Epic의 마지막 SF PR에서만 쓴다.**

## 📌 로드맵 의존관계 재확인: 매 Epic 완료 시 재확인

`docs/roadmap.md`의 실제 의존관계를 재확인했다. Epic 13의 선행조건은 Epic 1·2·3·4뿐이고, Epic 11은 Epic 7·8 뒤에 Epic 9·10과 독립적으로 착수할 수 있다. **매 Epic 완료 시 의존성 그래프를 다시 확인해 새로 열린 작업은 즉시 병렬 착수한다** — 백엔드/프론트엔드/기획을 각자의 워크스페이스에서 동시에 진행.

## ⚠️ 재발 방지: 이 문서는 매 세션 종료 전 반드시 갱신한다

2026-09-21 세션 종료 시점에 이 문서를 갱신하지 않은 채 세션이 끝나, 다음 세션(2026-09-22)이 하루치 실제 진행 상황(Epic 6 완료, Epic 7 완료, Epic 8 SF-1~2, Epic 14 SF-1~2)을 전혀 모르는 채로 시작할 뻔했다. 세션을 닫기 전에는 예외 없이 이 파일을 실제 `gh pr list --state merged`와 대조해서 갱신한다(`AGENTS.md` 10.2절 원칙).

## 워크스페이스 구조 (2026-09-20부터 역할별 고정 3개, 절대 삭제하지 않음)

| 워크스페이스 | 경로 | 역할 | 다음 착수 대상 (live 상태는 세션 시작 시 재확인) |
|---|---|---|---|
| `backend` | `/Users/joy/orca/workspaces/vibe-ai-team/backend` | 백엔드 구현(B그룹) | Epic 8 SF-5, 이후 Epic 9 또는 Epic 11 |
| `backend-3` | `/Users/joy/orca/workspaces/vibe-ai-team/backend-3` | 백엔드 구현(B그룹), 추가 워크트리 | Epic 8 완료 뒤 Epic 9/Epic 11 중 병렬 대상 |
| `frontend` | `/Users/joy/orca/workspaces/vibe-ai-team/frontend` | 프론트 구현(B그룹) | Epic 15 breakdown 승인 뒤 화면 구조·mock·테스트 계획 |
| `planning` | `/Users/joy/orca/workspaces/vibe-ai-team/planning` | 조사·기획·설계(A그룹) | Epic 15 breakdown, 이후 Epic 16 breakdown |

**표준 절차(2026-09-22 확립)**: SF/문서 PR이 머지되면, 같은 워크트리에서 다음 작업을 시작하기 전에 ① `git fetch origin main && git checkout -b <다음작업명> origin/main`으로 새 브랜치를 만들고 ② 머지된 옛 브랜치는 `git branch -D`로 지운다. 브랜치명을 재사용하거나 머지된 브랜치 위에 그대로 남아있지 않는다.

## Epic 진행 현황 (로드맵 16개 Epic 기준, 2026-09-22 `gh pr list --state merged` 전수 대조로 재확인)

| # | Epic | epic-slug | 상태 |
|---|---|---|---|
| 1 | 로그인 계정과 인증·인가 | account-authentication | **구현 완료 및 머지됨**(PR #41) |
| 2 | 투자자 관리 | investor-management | **구현 완료**(SF-1~6, PR #65~#70) |
| 3 | 증권사 기준정보 관리 | securities-firm-catalog | **구현 완료**(SF-1~5, PR #72~#76) |
| 4 | 증권계좌 관리 | securities-account-management | **구현 완료**(SF-1~7, PR #78~#84) |
| 5 | 공모주 기준정보 관리 | ipo-catalog | **구현 완료**(SF-1~6, PR #87/#91/#92/#94/#96/#97) |
| 6 | 청약 기록 | subscription-management | **구현 완료**(SF-1~5, PR #98/#104/#105/#109/#112 — SF-5 청약 취소 API로 Epic 6 완료, 2026-09-21) |
| 7 | 배정 결과 관리 | allocation-management | **구현 완료**(SF-1~5, PR #115~#119, 2026-09-22 새벽 완료) |
| 8 | 분할 매도 기록 | sale-management | breakdown 완료(PR #103, 총 5개 SF). **SF-1~4 완료**(PR #120/#121/#135/#139). **SF-5(취소) 남음** — 다음 세션 backend-3에 바로 배정. GitHub 이슈 #126 |
| 9 | 비용 기록 | cost-recording | Phase 0 완료(PR #56) + breakdown 완료(PR #106) — 구현 미착수. Epic 8 완료 후 착수. GitHub 이슈 #128 |
| 10 | 수익·수익률 계산 | profit-calculation | Phase 0 완료(조사 PR #42, 기획 PR #57) + **breakdown 완료**(PR #136, 총 3개 SF: 기반계층→단일계산API→투자자별집계API). 구현 착수는 Epic 8·9 backend 머지가 선행 조건. GitHub 이슈 #129 |
| 11 | 투자 내역 조회 API | investment-query-api | Phase 0 완료(조사 PR #43, 기획 PR #58) + breakdown 완료(PR #108) — 구현 미착수. Epic 7·8 머지 뒤 Epic 9·10과 병렬 착수 가능. GitHub 이슈 #130 |
| 12 | 대시보드 집계 API | dashboard-aggregation-api | Phase 0 완료(조사 PR #44, 기획 PR #59) + **breakdown 완료**(PR #138, 총 4개 SF). 구현 착수는 Epic 10 머지가 선행 조건. GitHub 이슈 #131 |
| 13 | 웹 계정·투자자·계좌 설정 | web-account-setup | **구현 완료**(SF-1~5, PR #88/#89/#93/#100/#110) + FSD 구조 정정(PR #111) + 사용자 가이드(PR #95) |
| 14 | 웹 IPO·청약·배정 입력 | web-subscription-workflow | **구현 완료**(SF-1~3, PR #113/#114/#137). GitHub 이슈 #127 close됨 |
| 15 | 웹 매도·비용·수익 확인 | web-sale-profit-workflow | Phase 0 완료(조사 PR #47, 기획 PR #62) — breakdown 미완료. GitHub 이슈 #132 |
| 16 | 웹 조회·대시보드 | web-inquiry-dashboard | Phase 0 완료(조사 PR #48, 기획 PR #63) — breakdown 미완료. GitHub 이슈 #133 |

**요약: 로드맵 16개 Epic 전체 Phase 0(조사+기획) 100% 완료. 구현 완료: Epic 1~7, 13, 14. 구현 진행: Epic 8(SF-1~4 완료, SF-5 남음). breakdown 완료·구현 대기: Epic 9~12. breakdown 필요: Epic 15, 16.**

## 다음 단계 후보 (2026-09-22 기준)

1. **backend**: Epic 8 SF-5(매도 취소 API)를 구현·검증·병합한다.
2. **planning**: Epic 15 → Epic 16 breakdown을 순서대로 작성·승인한다.
3. **backend 병렬화**: Epic 8 병합 뒤 Epic 9(cost-recording)와 Epic 11(investment-query-api)을 각각 별도 worktree에서 시작한다.
4. **frontend**: Epic 15 breakdown 승인 후 API 계약 기반 화면 구조·mock·테스트를 시작하고, E8/E9/E10 완료 뒤 실제 API 통합을 진행한다.
5. 매 Epic/SF 완료 시 이 문서와 `docs/roadmap.md`를 함께 갱신한다.
6. **알려진 블로커**: GitHub 이슈 #134(`apiFetch`가 서버 400 에러 메시지를 버리는 구조적 버그, Epic 2/4/14 SF-1/SF-3 폼 4곳에 영향) — 별도 SF로 다룰지 사용자 결정 필요.

## 재사용 가능한 공용 인프라 (Epic 2에서 확보, Epic 3~12가 활용 가능)

- `SecurityUtils.getCurrentAccountId()` (`com.vibeaiteam.accountauthentication.util.security`) — 계정 소유권 검증 헬퍼 (Epic 4처럼 계정 소유 데이터가 있는 Epic만 사용, Epic 3처럼 글로벌 기준정보는 불필요)
- 컴포넌트/엔티티/JPA 스캔 범위가 `com.vibeaiteam` 전체로 확대됨 — 새 Epic 패키지(`com.vibeaiteam.<epic>`)는 별도 스캔 설정 없이 바로 인식됨
- CSRF 전역 비활성화 + `AuthenticationEntryPoint`(401) 설정 완료(D-I-006) — 새 쓰기 API마다 CSRF 예외 추가할 필요 없음
- `SuccessResponse`/`ErrorResponse` 등 공용 DTO(`com.vibeaiteam.accountauthentication.controller.dto`) 재사용 가능
- (name, status) 복합 유니크 인덱스 패턴(Epic 3, D-SF-001) — "활성 레코드끼리만 유니크, 비활성화된 레코드는 재사용 가능" 요구사항이 있는 다른 기준정보성 Epic에도 재사용 가능
- AES-256-GCM 자체 구현 암호화 유틸(Epic 4, `securitiesaccountmanagement.infrastructure.encryption.AccountNumberEncryptor`) — 외부 라이브러리 없이 JDK `javax.crypto`만 사용, 다른 Epic에서 민감정보 암호화가 필요하면 동일 패턴 재사용 가능
- 2단계 소유권 검증 패턴(Epic 4) — 대상 리소스가 `Investor`를 거쳐야 소유 계정을 알 수 있는 하위 리소스(예: SecuritiesAccount)에 재사용 가능. `Investor` 자신처럼 1단계로 끝나는 경우와 구분할 것
- partial unique index + `DataIntegrityViolationException` 방어 코드로 명시적 락 없이 "그룹 내 유일 플래그"(예: 기본 계좌)를 다루는 패턴(Epic 4, D-SA-006) — 개인용 도구처럼 동시 요청 경쟁이 실질적으로 없는 경우 낙관적 락(@Version) 없이도 안전
- 누적 수량 검증(Σ ACTIVE quantity ≤ 상한)을 Service 계층 `@Transactional` 경계 안에서 수행하는 패턴(Epic 7 배정, Epic 8 매도) — 동시성 제어(락) 없이도 개인용 순차 입력 서비스에서는 충분(D-SL-004)
- 청약 목록 화면처럼 백엔드 API가 아직 없을 때 계약(spec/architecture 문서)만으로 프론트를 먼저 구현하고 mock으로 검증한 뒤, 실제 백엔드가 나오면 통합하는 패턴(Epic 14 SF-2) — 프론트/백엔드 워크스페이스가 서로 다른 Epic 순서로 진행될 때 병목을 줄이는 데 재사용 가능

## 알려진 이슈/결정 사항

- Epic 1 구현 중 SessionRegistry 인스턴스 불일치, AuthService의 SessionAuthenticationStrategy 미호출, 만료 세션 기본 응답 코드 등 3건의 Spring Security 버그를 Master가 직접 진단하여 수정 지시함(자세한 내용은 `docs/work-log/account-authentication.md` 참고).
- `application-test.yml`에 평문 비밀번호(`vibe_local_password`) 하드코딩 — 추후 보안 검토 필요(미해결).
- 테스트가 Testcontainers 대신 실제 로컬 dev Postgres를 공유해서 사용 중 — 추후 격리 개선 검토 필요(미해결).
- Orca 오케스트레이션 기록상 `run_76df20e13c0d`에 지난 세션들의 워커 dispatch 100건(성공 53/실패 29/abandon 18)이 남아있으나, `worker-list --terminal-state reclaimable` 조회 결과 0건 — 전부 정상 정산된 기록이며 실제 정리(release)가 밀린 건 없음(2026-09-22 확인). 실제 열린 터미널은 6개뿐이었다.
