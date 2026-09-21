# 프로젝트 현재 상황 (Master Lead 소유, 계속 최신화)

> 마지막 갱신: 2026-09-21 (Epic 1~5, 13 구현 완료. Epic 6은 SF-4까지 완료. Epic 7/8/9/11/14 breakdown 선행 준비 완료. 오늘 작업 종료)

## 📌 로드맵 의존관계 재확인: 매 Epic 완료 시 재확인
docs/roadmap.md의 실제 의존관계를 재확인한 결과, Epic 13(web-account-setup)의 선행조건은 Epic 1·2·3·4뿐이었다(Epic 5~12 불필요). **매 Epic 완료 시 로드맵의 실제 의존관계 그래프를 다시 확인해, 새로 선행조건이 충족된 Epic이 있으면 즉시 병렬 착수한다** — 백엔드/프론트엔드/기획을 각자의 워크스페이스에서 동시에 진행.

## ⚠️ 현재 상태: 무료 모델 할당량(OpenRouter + OpenCode Zen 양쪽) 재도달 → 오늘은 네이티브 Claude로 마무리 (2026-09-21 저녁)

오늘 오후~저녁 사이 OpenRouter 계정 일일 한도(`free-models-per-day-high-balance`)와 OpenCode Zen(big-pickle 등)의 `Rate limit exceeded`/`Free usage exceeded`가 **동시에** 재발함(backend/frontend/planning 워커 3개가 거의 동시에 멈춤). 사용자 지시로 이번 세션의 마지막 3개 태스크(Epic6 SF-4, Epic13 SF-5, Epic11 breakdown)만 예외적으로 네이티브 Claude 모델(`claude --model claude-sonnet-5`, AGENTS.md 22.6)로 전환해 완료했다. **다음 세션 시작 시 OpenRouter/OpenCode Zen 무료 할당량이 리셋됐는지 먼저 확인**(`claude --model <openrouter 무료 모델 ID>`로 하나 띄워 같은 오류가 나는지)한 뒤, 정상이면 22.4 우선순위(1순위 `claude --model`)로 되돌린다.

## 워크스페이스 구조 (2026-09-20부터 역할별 고정 3개, 절대 삭제하지 않음)

| 워크스페이스 | 경로 | 역할 | 현재 상태 |
|---|---|---|---|
| `backend` | `/Users/joy/orca/workspaces/vibe-ai-team/backend` | 백엔드 구현(B그룹) | PR #41 머지 확인 후 `winter0123/investor-management` 브랜치 생성(origin/main 기준, Epic 1 코드 포함). Epic 2 SF 분해 착수 중 |
| `frontend` | `/Users/joy/orca/workspaces/vibe-ai-team/frontend` | 프론트 구현(B그룹) | 생성됨, 아직 미사용 |
| `planning` | `/Users/joy/orca/workspaces/vibe-ai-team/planning` | 조사·기획·설계(A그룹) | 사용 중, main 기준 유지 |

## Epic 진행 현황 (로드맵 16개 Epic 기준)

| # | Epic | epic-slug | 상태 |
|---|---|---|---|
| 1 | 로그인 계정과 인증·인가 | account-authentication | **구현 완료 및 머지됨**(SF-1~6, 34개 테스트 통과, PR #41). `account-authentication` 워크스페이스는 `backend`로 이관 완료, worktree 정리됨 |
| 2 | 투자자 관리 | investor-management | **구현 완료**(SF-1~6, 66개 테스트 통과, PR #65~#70 병합됨). SF-3에서 CSRF 전역 비활성화 등 공용 보안설정 정리(D-I-006) 포함 |
| 3 | 증권사 기준정보 관리 | securities-firm-catalog | **구현 완료**(SF-1~5, 94개 테스트 통과, PR #72~#76 병합됨). 계정 소유권 검증 불필요한 글로벌 기준정보(D-SF-001~003) |
| 4 | 증권계좌 관리 | securities-account-management | **구현 완료**(SF-1~7, 140개 테스트 통과, PR #78~#84 병합됨). AES-256-GCM 계좌번호 암호화(D-SA-002), 2단계 소유권 검증(SecuritiesAccount→Investor→계정), partial unique index 기반 기본 계좌 동시성 제어(D-SA-006) 포함 |
| 5 | 공모주 기준정보 관리 | ipo-catalog | **구현 완료 및 머지됨**(SF-1~6, 184개 테스트 통과, PR #87/#91/#92/#94/#96/#97). D-IPO-001~004 확정. SF-2 검증 중 V5 마이그레이션의 partial unique index 버그 발견·V6로 수정(stock_code 유무 무시하고 name+date 유니크 강제하던 결함). SF-6(CSV 일괄 등록)에서 IpoController에 AGENTS.md 15장 신규 규칙에 따른 한글 클래스 주석 최초 적용 |
| 6 | 청약 기록 | subscription-management | **SF-1~4 완료**(엔티티/마이그레이션 PR #98, 등록 API PR #104, 목록/상세 조회 PR #105, 수정 API PR #109 — 210개 테스트 통과). Investor/SecuritiesAccount는 1·2단계 소유권 검증(404/403), IPO는 존재 검증만(글로벌 기준정보). 다음: SF-5(취소 API) |
| 7 | 배정 결과 관리 | allocation-management | Phase 0 완료(PR #54) + **SF 분해(breakdown) 완료**(PR #103, docs/breakdown/allocation-management.md) — Subscription 존재 검증만 필요(소유권 검증 불필요, 글로벌 기준정보). 다음: Epic 6 완료 후 backend에서 구현 착수 |
| 8 | 분할 매도 기록 | sale-management | Phase 0 완료(PR #55) + **SF 분해(breakdown) 완료**(PR #103, docs/breakdown/sale-management.md) — 핵심은 누적 매도수량 검증(Σ ACTIVE quantity ≤ allocatedQuantity). 다음: Epic 7 완료 후 구현 착수 |
| 9 | 비용 기록 | cost-recording | Phase 0 완료(PR #56) + **SF 분해(breakdown) 완료**(PR #106, docs/breakdown/cost-recording.md) — Epic 7/8과 달리 계정 소유 데이터로 판단해 등록 시 소유권 검증(403) 필요. 다음: Epic 8 완료 후 구현 착수 |
| 10 | 수익·수익률 계산 | profit-calculation | **Phase 0 전체 완료**(조사+spec+architecture, D-P-001~006로 CalculationTrace 과설계 정정 포함), PR #57 병합됨. 읽기 전용 계산 API로 설계(Profit 엔티티 없음). 다음: SF 분해 |
| 11 | 투자 내역 조회 API | investment-query-api | Phase 0 완료(PR #58) + **SF 분해(breakdown) 완료**(PR #108, docs/breakdown/investment-query-api.md) — 자체 테이블 없는 순수 조회 API. Epic 7/8/9와 달리 조회 시에도 소유권 검증 필수. SF-2에서 필터별 개별 검증만으로는 ipoId 단독 필터 시 계정 스코프 누출 위험 발견(구현 시 반드시 결과 쿼리에도 계정 스코프 강제). 실제 구현 착수는 Epic 7·8 backend 머지가 선행 조건 |
| 12 | 대시보드 집계 API | dashboard-aggregation-api | **Phase 0 전체 완료**(조사+spec+architecture, D-D-001~007 정렬 + 계정 격리 보안 결함 수정 포함), PR #59 병합됨. 다음: SF 분해 |
| 13 | 웹 계정·투자자·계좌 설정 | web-account-setup | **구현 완료 및 머지됨**(SF-1~5, 28개 테스트 통과, PR #88/#89/#93/#100/#110). 로그인/회원가입/투자자등록/증권계좌등록 4개 화면 전부 완료. SF-5에서 계좌번호 하이픈 자동삽입 버그(JSX 속성 순서), RHF 미지원 옵션 버그 발견·수정, spec-백엔드 계약 불일치(404 vs 400) 발견·양쪽 대응 |
| 14 | 웹 IPO·청약·배정 입력 | web-subscription-workflow | Phase 0 완료(PR #61) + **SF 분해(breakdown) 완료**(PR #107, docs/breakdown/web-subscription-workflow.md) — SF-1(청약 등록 화면)은 필요 API 4개가 모두 merge되어 **즉시 착수 가능**. SF-2/3은 Epic 11(GET /investment-history) 미구현이 실제 선행 조건임을 architecture 문서 대조로 발견(그 문서의 전제와 달랐음). 다음: frontend가 Epic 13을 끝냈으므로 SF-1 바로 착수 가능 |
| 15 | 웹 매도·비용·수익 확인 | web-sale-profit-workflow | **Phase 0 전체 완료**(조사+spec+architecture, D-W15-001~006 최신화 포함), PR #62 병합됨. 다음: SF 분해 |
| 16 | 웹 조회·대시보드 | web-inquiry-dashboard | **Phase 0 전체 완료**(조사+spec+architecture, D-W16-001~007 최신화 포함, 빈 결과 404→200 오류 수정), PR #63 병합됨. 다음: SF 분해 |

**요약: 🎉 로드맵 16개 Epic 전체의 Phase 0 100% 완료. Epic 1~5, 13은 구현까지 완료. Epic 6은 SF-4까지 완료(SF-5만 남음). Epic 7/8/9/11/14는 SF 분해(breakdown)까지 완료해 backend/frontend가 순서대로 이어받을 수 있게 선행 준비됨.**

## 다음 단계 후보
1. **Epic 6(subscription-management, backend)**: SF-5(취소 API, DELETE /subscriptions/{id})만 남음 — 다음 세션에서 바로 착수.
2. **Epic 14(web-subscription-workflow, frontend)**: Epic 13이 끝났으므로 SF-1(청약 등록 화면)을 즉시 착수 가능(필요 API 4개 모두 머지됨). SF-2/3은 Epic 11 백엔드 완료가 선행 조건.
3. **Epic 7(allocation-management, backend)**: Epic 6 SF-5 완료 후 이어서 착수(breakdown 이미 준비됨).
4. **Epic 9(cost-recording)**: Epic 7 다음 순서로 이미 breakdown 준비됨(backend 담당자가 판단해 Epic 8 전에 착수할 수도 있음 — 두 Epic 다 Epic 6에만 의존하므로 순서는 유동적).
5. **Epic 11(investment-query-api)**: Epic 7·8 backend 머지가 선행 조건. 완료되면 Epic 14 SF-2/3의 블로커도 함께 풀림.
6. 매 Epic 완료 시점마다 로드맵 의존관계를 재확인해 새로 선행조건이 충족되는 Epic이 있으면 즉시 병렬 착수한다.
7. 헤르메스(Hermes, Telegram) 알림 파이프라인 가동 중 — PR 생성 시 자동으로 "기능 구현 완료" 메시지 발송(AGENTS.md 28장).

## 재사용 가능한 공용 인프라 (Epic 2에서 확보, Epic 3~12가 활용 가능)
- `SecurityUtils.getCurrentAccountId()` (`com.vibeaiteam.accountauthentication.util.security`) — 계정 소유권 검증 헬퍼 (Epic 4처럼 계정 소유 데이터가 있는 Epic만 사용, Epic 3처럼 글로벌 기준정보는 불필요)
- 컴포넌트/엔티티/JPA 스캔 범위가 `com.vibeaiteam` 전체로 확대됨 — 새 Epic 패키지(`com.vibeaiteam.<epic>`)는 별도 스캔 설정 없이 바로 인식됨
- CSRF 전역 비활성화 + `AuthenticationEntryPoint`(401) 설정 완료(D-I-006) — 새 쓰기 API마다 CSRF 예외 추가할 필요 없음
- `SuccessResponse`/`ErrorResponse` 등 공용 DTO(`com.vibeaiteam.accountauthentication.controller.dto`) 재사용 가능
- (name, status) 복합 유니크 인덱스 패턴(Epic 3, D-SF-001) — "활성 레코드끼리만 유니크, 비활성화된 레코드는 재사용 가능" 요구사항이 있는 다른 기준정보성 Epic에도 재사용 가능
- AES-256-GCM 자체 구현 암호화 유틸(Epic 4, `securitiesaccountmanagement.infrastructure.encryption.AccountNumberEncryptor`) — 외부 라이브러리 없이 JDK `javax.crypto`만 사용, 다른 Epic에서 민감정보 암호화가 필요하면 동일 패턴 재사용 가능
- 2단계 소유권 검증 패턴(Epic 4) — 대상 리소스가 `Investor`를 거쳐야 소유 계정을 알 수 있는 하위 리소스(예: SecuritiesAccount)에 재사용 가능. `Investor` 자신처럼 1단계로 끝나는 경우와 구분할 것
- partial unique index + `DataIntegrityViolationException` 방어 코드로 명시적 락 없이 "그룹 내 유일 플래그"(예: 기본 계좌)를 다루는 패턴(Epic 4, D-SA-006) — 개인용 도구처럼 동시 요청 경쟁이 실질적으로 없는 경우 낙관적 락(@Version) 없이도 안전

## 알려진 이슈/결정 사항
- Epic 1 구현 중 SessionRegistry 인스턴스 불일치, AuthService의 SessionAuthenticationStrategy 미호출, 만료 세션 기본 응답 코드 등 3건의 Spring Security 버그를 Master가 직접 진단하여 수정 지시함(자세한 내용은 `docs/work-log/account-authentication.md` 참고).
- `application-test.yml`에 평문 비밀번호(`vibe_local_password`) 하드코딩 — 추후 보안 검토 필요(미해결).
- 테스트가 Testcontainers 대신 실제 로컬 dev Postgres를 공유해서 사용 중 — 추후 격리 개선 검토 필요(미해결).
