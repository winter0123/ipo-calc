# 프로젝트 현재 상황 (Master Lead 소유, 계속 최신화)

> 마지막 갱신: 2026-09-22 낮 (Epic 8 SF-3 완료, Epic 10 breakdown 완료, GitHub Issue 병행 관리 시작 이후 갱신)

## 📌 GitHub Issue를 Orca 태스크와 병행 운영 중 (2026-09-22부터)
AGENTS.md 8.4장 개정에 따라 이 프로젝트는 Orca 태스크(실행 단위)와 별개로 GitHub Issue(사람이 보는 진행 현황판)를 함께 쓰기로 했다. Epic마다 이슈 1개, 라벨은 `status:planned/in-progress/review/changes-requested/approved`, 완료되면 close(라벨 제거). 이슈 #35~#133 정리 완료, 이후 Epic 상태가 바뀔 때마다 그 즉시 이슈도 갱신한다.
**주의**: PR 본문에 부분 완료(SF 일부만 끝난 상태)인데 `Closes #`를 쓰면 GitHub가 Epic 이슈를 오토클로즈해버린다(2026-09-22, PR #135에서 실제로 발생해 Epic 8 이슈 #126이 잘못 닫힘 → 바로 reopen). **Epic이 완전히 끝나지 않았으면 PR 본문에 `Part of #<이슈번호>`만 쓰고 `Closes #`는 Epic의 마지막 SF PR에서만 쓴다.**

## 📌 로드맵 의존관계 재확인: 매 Epic 완료 시 재확인
docs/roadmap.md의 실제 의존관계를 재확인한 결과, Epic 13(web-account-setup)의 선행조건은 Epic 1·2·3·4뿐이었다(Epic 5~12 불필요). **매 Epic 완료 시 로드맵의 실제 의존관계 그래프를 다시 확인해, 새로 선행조건이 충족된 Epic이 있으면 즉시 병렬 착수한다** — 백엔드/프론트엔드/기획을 각자의 워크스페이스에서 동시에 진행.

## ⚠️ 재발 방지: 이 문서는 매 세션 종료 전 반드시 갱신한다
2026-09-21 세션 종료 시점에 이 문서를 갱신하지 않은 채 세션이 끝나, 다음 세션(2026-09-22)이 하루치 실제 진행 상황(Epic 6 완료, Epic 7 완료, Epic 8 SF-1~2, Epic 14 SF-1~2)을 전혀 모르는 채로 시작할 뻔했다. 세션을 닫기 전에는 예외 없이 이 파일을 실제 `gh pr list --state merged`와 대조해서 갱신한다(9.2장 원칙 그대로).

## 워크스페이스 구조 (2026-09-20부터 역할별 고정 3개, 절대 삭제하지 않음)

| 워크스페이스 | 경로 | 역할 | 현재 진행 중인 작업 (2026-09-22 낮) |
|---|---|---|---|
| `backend` | `/Users/joy/orca/workspaces/vibe-ai-team/backend` | 백엔드 구현(B그룹) | Epic 1 머지 이후 미사용 — 실질적으로 `backend-3`가 현재 backend 작업 브랜치 역할 수행 중 |
| `backend-3` | `/Users/joy/orca/workspaces/vibe-ai-team/backend-3` | 백엔드 구현(B그룹), 추가 워크트리 | `winter0123/sale-update` 브랜치에서 **Epic 8 SF-4(매도 수정 API) 진행 중**(워커 배정 완료, 결과 대기) |
| `frontend` | `/Users/joy/orca/workspaces/vibe-ai-team/frontend` | 프론트 구현(B그룹) | `winter0123/web-subscription-workflow-sf3` 브랜치에서 **Epic 14 SF-3 테스트 보완 진행 중**(프로덕션 코드는 완료·검증됨, 테스트 파일 2개 추가 작업 중) |
| `planning` | `/Users/joy/orca/workspaces/vibe-ai-team/planning` | 조사·기획·설계(A그룹) | `winter0123/dashboard-aggregation-api-breakdown` 브랜치에서 **Epic 12 breakdown 진행 중**(워커 배정 완료, 결과 대기) |

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
| 8 | 분할 매도 기록 | sale-management | breakdown 완료(PR #103, 총 5개 SF). **SF-1~3 완료**(PR #120/#121/#135). **SF-4(수정) 진행 중**(backend-3), **SF-5(취소) 남음**. GitHub 이슈 #126 |
| 9 | 비용 기록 | cost-recording | Phase 0 완료(PR #56) + breakdown 완료(PR #106) — 구현 미착수. Epic 8 완료 후 착수. GitHub 이슈 #128 |
| 10 | 수익·수익률 계산 | profit-calculation | Phase 0 완료(조사 PR #42, 기획 PR #57) + **breakdown 완료**(PR #136, 총 3개 SF: 기반계층→단일계산API→투자자별집계API). 구현 착수는 Epic 8·9 backend 머지가 선행 조건. GitHub 이슈 #129 |
| 11 | 투자 내역 조회 API | investment-query-api | Phase 0 완료(조사 PR #43, 기획 PR #58) + breakdown 완료(PR #108) — 구현 미착수. 로드맵상 선행조건은 Epic 1~10 전체이므로 Epic 8·9·10 완료 후 착수 가능. GitHub 이슈 #130 |
| 12 | 대시보드 집계 API | dashboard-aggregation-api | Phase 0 완료(조사 PR #44, 기획 PR #59) — **breakdown 진행 중**(planning). GitHub 이슈 #131 |
| 13 | 웹 계정·투자자·계좌 설정 | web-account-setup | **구현 완료**(SF-1~5, PR #88/#89/#93/#100/#110) + FSD 구조 정정(PR #111) + 사용자 가이드(PR #95) |
| 14 | 웹 IPO·청약·배정 입력 | web-subscription-workflow | breakdown 완료(PR #107, 총 3개 SF). **SF-1·SF-2 완료**(PR #113/#114). **SF-3(배정 등록 화면) 진행 중**(frontend, 프로덕션 코드 검증 완료·테스트 보완만 남음). GitHub 이슈 #127 |
| 15 | 웹 매도·비용·수익 확인 | web-sale-profit-workflow | Phase 0 완료(조사 PR #47, 기획 PR #62) — breakdown 미완료. GitHub 이슈 #132 |
| 16 | 웹 조회·대시보드 | web-inquiry-dashboard | Phase 0 완료(조사 PR #48, 기획 PR #63) — breakdown 미완료. GitHub 이슈 #133 |

**요약: 로드맵 16개 Epic 전체 Phase 0(조사+기획) 100% 완료. 구현 완료: Epic 1~7, 13. 구현 진행 중: Epic 8(SF-4), Epic 14(SF-3 테스트 보완). breakdown 완료 대기: Epic 9, 10, 11. breakdown 진행 중: Epic 12. breakdown 필요: Epic 15, 16.**

## 다음 단계 후보 (2026-09-22 낮 기준)
1. **backend (`backend-3`)**: Epic 8 SF-4(매도 수정 API, 누적 매도수량 재검증) 결과 대기 중. 끝나면 SF-5(취소 API)로 이어서 Epic 8 완료.
2. **frontend**: Epic 14 SF-3 테스트 보완(`AllocationRegisterForm.test.tsx`, `AllocationRegisterPage.test.tsx`) 결과 대기 중. 끝나면 병합하고 Epic 14는 남은 SF 없음(Epic 11 대기 상태로 보류).
3. **planning**: Epic 12(dashboard-aggregation-api) breakdown 결과 대기 중. 끝나면 Epic 15 → Epic 16 breakdown 순으로 이어간다(6.4장 "planning은 최소 1~2 Epic 앞서 작업" 원칙).
4. Epic 8 완료되면: Epic 9(cost-recording, breakdown 이미 있음) backend 착수 검토.
5. 매 Epic/SF 완료 시점마다 이 문서를 그 즉시 갱신한다.
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
