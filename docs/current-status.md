# 프로젝트 현재 상황 (Master Lead 소유, 계속 최신화)

> 마지막 갱신: 2026-09-21 (Epic 4 구현 완료 — B그룹 구현 Epic 4개째)

## ✅ 해결됨: OpenRouter 무료 모델 계정 일일 한도 블로커 (2026-09-21, 우회 경로 확보)

`free-models-per-day-high-balance` 오류가 서로 다른 프로바이더 3종(Nvidia Nemotron 3 Ultra/Super, Poolside Laguna S 2.1) 및 Google Gemma까지 총 4종에서 동일하게 발생 — 계정 전체의 일일 무료 모델 사용량 한도로 최종 확인됨. 12회 재시도(15:03~16:55 UTC)까지 모두 실패한 뒤, **`opencode/` 프로바이더 자체의 무료 모델(OpenCode Zen)** 이 OpenRouter와 별개의 quota pool임을 발견 — `opencode/big-pickle`로 즉시 Epic 3 architecture 작업 성공. AGENTS.md 22장에 대체 경로로 문서화함. OpenRouter 재시도 루프(`bbqngsqy6`)는 중복 방지를 위해 정지시켰고, 앞으로 OpenRouter 한도가 풀릴 때까지는 `opencode/` 모델(big-pickle, mimo-v2.5-free, ling-3.0-flash-fin-free 등)을 우선 사용(2026-09-21 사용자 지시: "오픈라우터 한도풀릴때 까지는 오픈코드무료모델 이것저것 써보면서 해").

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
| 5 | 공모주 기준정보 관리 | ipo-catalog | **Phase 0 전체 완료**(조사+spec+architecture), PR #52 병합됨. 다음: SF 분해 |
| 6 | 청약 기록 | subscription-management | **Phase 0 전체 완료**(조사+spec+architecture, D-S-001~006 스코프 정정 포함), PR #53 병합됨. 다음: SF 분해 |
| 7 | 배정 결과 관리 | allocation-management | **Phase 0 전체 완료**(조사+spec+architecture, D-A-001~007 스코프 정정 포함), PR #54 병합됨. 다음: SF 분해 |
| 8 | 분할 매도 기록 | sale-management | **Phase 0 전체 완료**(조사+spec+architecture, D-SL-001~007 스코프 정정 포함), PR #55 병합됨. 다음: SF 분해 |
| 9 | 비용 기록 | cost-recording | **Phase 0 전체 완료**(조사+spec+architecture, D-C-001~006로 Sale과의 책임 경계 정정 포함), PR #56 병합됨. 다음: SF 분해 |
| 10 | 수익·수익률 계산 | profit-calculation | **Phase 0 전체 완료**(조사+spec+architecture, D-P-001~006로 CalculationTrace 과설계 정정 포함), PR #57 병합됨. 읽기 전용 계산 API로 설계(Profit 엔티티 없음). 다음: SF 분해 |
| 11 | 투자 내역 조회 API | investment-query-api | **Phase 0 전체 완료**(조사+spec+architecture, D-Q-001~006로 Subscription 기준 조회 정정 포함), PR #58 병합됨. 다음: SF 분해 |
| 12 | 대시보드 집계 API | dashboard-aggregation-api | **Phase 0 전체 완료**(조사+spec+architecture, D-D-001~007 정렬 + 계정 격리 보안 결함 수정 포함), PR #59 병합됨. 다음: SF 분해 |
| 13 | 웹 계정·투자자·계좌 설정 | web-account-setup | **Phase 0 전체 완료**(조사+spec+architecture, D-W13-001~006로 API 계약 최신화 포함), PR #60 병합됨 — 첫 프론트엔드 Epic. 다음: SF 분해 |
| 14 | 웹 IPO·청약·배정 입력 | web-subscription-workflow | **Phase 0 전체 완료**(조사+spec+architecture, D-W14-001~006 최신화 포함), PR #61 병합됨. 다음: SF 분해 |
| 15 | 웹 매도·비용·수익 확인 | web-sale-profit-workflow | **Phase 0 전체 완료**(조사+spec+architecture, D-W15-001~006 최신화 포함), PR #62 병합됨. 다음: SF 분해 |
| 16 | 웹 조회·대시보드 | web-inquiry-dashboard | **Phase 0 전체 완료**(조사+spec+architecture, D-W16-001~007 최신화 포함, 빈 결과 404→200 오류 수정), PR #63 병합됨. 다음: SF 분해 |

**요약: 🎉 로드맵 16개 Epic 전체의 Phase 0(조사→기획→설계) 100% 완료. Epic 1, 2, 3, 4는 구현(기획→설계→구현→테스트)까지 완료, 모두 병합됨. Epic 5~16은 Phase 0 전체 완료, 모두 병합됨. 다음 단계는 Epic 5부터 순차적으로 SF 분해 및 실제 구현 착수.**

## 다음 단계 후보
1. ~~Epic 1 PR #41 사람 승인 및 머지~~ — **완료(2026-09-20 23:10 UTC)**.
2. ~~Epic 2(investor-management) 세부기능(SF) 분해 및 구현~~ — **완료(2026-09-21 00:23 UTC)**. SF-1~6, PR #65~#70 병합, 66개 테스트 GREEN.
3. ~~Epic 3(securities-firm-catalog) 세부기능(SF) 분해 및 구현~~ — **완료(2026-09-21 01:02 UTC)**. SF-1~5, PR #72~#76 병합, 94개 테스트 GREEN.
4. ~~Epic 4(securities-account-management) 세부기능(SF) 분해 및 구현~~ — **완료(2026-09-21 03:36 UTC)**. SF-1~7, PR #78~#84 병합, 140개 테스트 GREEN.
5. Epic 5(ipo-catalog)부터 순차적으로 SF 분해 및 구현 착수 (backend 워크스페이스, 22.4 우선순위: `claude --model <openrouter 무료 모델>` → opencode 순). Epic 13~16은 frontend 워크스페이스에서 대응 백엔드 Epic 완료 후 순차 진행

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
