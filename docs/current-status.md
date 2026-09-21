# 프로젝트 현재 상황 (Master Lead 소유, 계속 최신화)

> 마지막 갱신: 2026-09-21 (Epic 5 전체 완료(SF-1~6), Epic 13 SF-4 진행 중 — 백엔드/프론트엔드 병렬 진행 중)

## 📌 로드맵 의존관계 재확인: Epic 13(프론트엔드)은 Epic 1~4만 있으면 시작 가능
docs/roadmap.md의 실제 의존관계를 재확인한 결과, Epic 13(web-account-setup)의 선행조건은 Epic 1·2·3·4뿐이다(Epic 5~12 불필요). Epic 4가 완료된 시점(2026-09-21)부터 Epic 13은 이미 착수 가능한 상태였으나, "Epic 1→2→...→16 순차 진행"이라는 암묵적 가정 때문에 뒤늦게 시작함(사용자가 "프론트 개발은 왜 안해?"로 직접 지적). **이후로는 매 Epic 완료 시 로드맵의 실제 의존관계 그래프를 다시 확인해, 새로 선행조건이 충족된 Epic이 있으면 즉시 병렬 착수한다** — 백엔드/프론트엔드를 각자의 워크스페이스에서 동시에 진행 가능.

## ⚠️ 현재 상태: OpenRouter 무료 모델 계정 일일 한도 재도달 (2026-09-21 오후, opencode로 우회 중)

`free-models-per-day-high-balance` 오류가 서로 다른 프로바이더 3종(Nvidia Nemotron 3 Ultra/Super, Poolside Laguna S 2.1) 및 Google Gemma까지 총 4종에서 동일하게 발생 — 계정 전체의 일일 무료 모델 사용량 한도로 최종 확인됨. 12회 재시도(15:03~16:55 UTC)까지 모두 실패한 뒤, **`opencode/` 프로바이더 자체의 무료 모델(OpenCode Zen)** 이 OpenRouter와 별개의 quota pool임을 발견 — `opencode/big-pickle`로 즉시 Epic 3 architecture 작업 성공. AGENTS.md 22장에 대체 경로로 문서화함.

**2026-09-21 오후 재도달**: Epic 5 SF-6, Epic 13 SF-4 워커 둘 다 같은 `free-models-per-day-high-balance` 오류로 동시에 막힘(약 14:55경) — `claude --model <openrouter 무료 모델>` 경로도 이 계정 한도의 영향을 받음(claude/opencode 무관하게 계정 단위). 두 워커 모두 `opencode --model opencode/<모델명>`(big-pickle, nemotron-3-ultra-free)으로 즉시 전환해 재개함. **지금은 opencode/ 무료 모델을 기본으로 쓰는 중이며, 다음 세션에서 이 문서를 읽는 사람은 OpenRouter 한도가 풀렸는지 먼저 확인(`curl -s "https://openrouter.ai/api/v1/models"`로 계정 자체 한도는 확인 불가하므로, 그냥 `claude --model <openrouter 무료 모델>`로 시험 삼아 하나 띄워보고 같은 오류가 나는지로 판단)한 뒤 22.4 우선순위(1순위 claude --model)로 되돌릴지 결정한다.**

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
| 6 | 청약 기록 | subscription-management | **Phase 0 전체 완료**(조사+spec+architecture, D-S-001~006 스코프 정정 포함), PR #53 병합됨. 다음: SF 분해 |
| 7 | 배정 결과 관리 | allocation-management | **Phase 0 전체 완료**(조사+spec+architecture, D-A-001~007 스코프 정정 포함), PR #54 병합됨. 다음: SF 분해 |
| 8 | 분할 매도 기록 | sale-management | **Phase 0 전체 완료**(조사+spec+architecture, D-SL-001~007 스코프 정정 포함), PR #55 병합됨. 다음: SF 분해 |
| 9 | 비용 기록 | cost-recording | **Phase 0 전체 완료**(조사+spec+architecture, D-C-001~006로 Sale과의 책임 경계 정정 포함), PR #56 병합됨. 다음: SF 분해 |
| 10 | 수익·수익률 계산 | profit-calculation | **Phase 0 전체 완료**(조사+spec+architecture, D-P-001~006로 CalculationTrace 과설계 정정 포함), PR #57 병합됨. 읽기 전용 계산 API로 설계(Profit 엔티티 없음). 다음: SF 분해 |
| 11 | 투자 내역 조회 API | investment-query-api | **Phase 0 전체 완료**(조사+spec+architecture, D-Q-001~006로 Subscription 기준 조회 정정 포함), PR #58 병합됨. 다음: SF 분해 |
| 12 | 대시보드 집계 API | dashboard-aggregation-api | **Phase 0 전체 완료**(조사+spec+architecture, D-D-001~007 정렬 + 계정 격리 보안 결함 수정 포함), PR #59 병합됨. 다음: SF 분해 |
| 13 | 웹 계정·투자자·계좌 설정 | web-account-setup | **SF-1~3 완료**(스캐폴딩 PR #88, 로그인 화면 PR #89, 회원가입 화면 PR #93 — 13개 테스트 통과) — **첫 프론트엔드 구현 착수**. D-W13-007~015 확정. SF-2 검증 중 noValidate 누락/QueryClientProvider 누락 등 실제 버그 발견·수정, tsconfig jest-dom 타입 gap 발견(AGENTS.md에 build 검증 필수화로 기록). 다음: SF-4(투자자 등록 화면) |
| 14 | 웹 IPO·청약·배정 입력 | web-subscription-workflow | **Phase 0 전체 완료**(조사+spec+architecture, D-W14-001~006 최신화 포함), PR #61 병합됨. 다음: SF 분해 |
| 15 | 웹 매도·비용·수익 확인 | web-sale-profit-workflow | **Phase 0 전체 완료**(조사+spec+architecture, D-W15-001~006 최신화 포함), PR #62 병합됨. 다음: SF 분해 |
| 16 | 웹 조회·대시보드 | web-inquiry-dashboard | **Phase 0 전체 완료**(조사+spec+architecture, D-W16-001~007 최신화 포함, 빈 결과 404→200 오류 수정), PR #63 병합됨. 다음: SF 분해 |

**요약: 🎉 로드맵 16개 Epic 전체의 Phase 0(조사→기획→설계) 100% 완료. Epic 1~5는 구현까지 완료. Epic 6(백엔드)과 Epic 13(프론트엔드)이 각자 워크스페이스에서 병렬로 SF 구현 진행 중.**

## 다음 단계 후보
1. ~~Epic 1 PR #41 사람 승인 및 머지~~ — **완료(2026-09-20 23:10 UTC)**.
2. ~~Epic 2(investor-management) 세부기능(SF) 분해 및 구현~~ — **완료(2026-09-21 00:23 UTC)**. SF-1~6, PR #65~#70 병합, 66개 테스트 GREEN.
3. ~~Epic 3(securities-firm-catalog) 세부기능(SF) 분해 및 구현~~ — **완료(2026-09-21 01:02 UTC)**. SF-1~5, PR #72~#76 병합, 94개 테스트 GREEN.
4. ~~Epic 4(securities-account-management) 세부기능(SF) 분해 및 구현~~ — **완료(2026-09-21 03:36 UTC)**. SF-1~7, PR #78~#84 병합, 140개 테스트 GREEN.
5. ~~Epic 5(ipo-catalog) 세부기능(SF) 분해 및 구현~~ — **완료(2026-09-21)**. SF-1~6, PR #87/#91/#92/#94/#96/#97 병합, 184개 테스트 GREEN.
6. **Epic 13(web-account-setup, frontend 워크스페이스)**: SF-1~3 완료(PR #88, #89, #93). SF-4(투자자 등록 화면) 진행 중.
7. **Epic 6(subscription-management, backend 워크스페이스)**: 선행조건(Epic 2·4·5) 모두 완료되어 착수 가능. Phase 0(조사/spec/architecture)은 이미 완료(PR #53) — 다음은 SF 분해 및 구현. Epic 5가 방금 끝난 backend 워크스페이스에 바로 이어서 배정한다(6.4장 워크스페이스 풀가동 원칙).
8. 매 Epic 완료 시점마다 로드맵 의존관계를 재확인해 새로 선행조건이 충족되는 Epic이 있으면 즉시 병렬 착수한다(위 "로드맵 의존관계 재확인" 참고).
8. 헤르메스(Hermes, Telegram) 알림 파이프라인 가동 중 — PR 생성 시 자동으로 "기능 구현 완료" 메시지 발송(AGENTS.md 28장). 공용 스크립트(`~/.hermes/scripts/feature-notify.py`)라 다른 프로젝트에도 재사용 가능.

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
