# 일자별 작업 요약 (매일 추가, append-only)

## 2026-09-20

- Epic 1(account-authentication) SF-5(데이터 격리 방어), SF-6(비밀번호 변경 API) 완료. Epic 1 전체 34개 테스트 통과, PR #41 생성(사람 승인 대기).
  - SF-6 진행 중 Spring Security 세션 관리 버그 3건을 Master가 직접 진단·수정 지시하여 해결(SessionRegistry 인스턴스 불일치, AuthService의 SessionAuthenticationStrategy 미호출, 만료 세션 기본 응답 코드).
- 워크스페이스 운영 방식을 Epic별 생성/삭제에서 **역할별 고정 3개(backend/frontend/planning)로 절대 삭제하지 않는 방식**으로 전환(사용자 지시). 관련 git 제약(같은 저장소 여러 워크트리는 동일 브랜치 동시 체크아웃 불가) 발견 및 절차 수정.
- main 병합 정책을 "Epic 전체 완료 후 한 번에"에서 "기능(SF)/문서 단위로 계속 진행"으로 전환(사용자 지시). 코드는 테스트 통과 확인 후에만, 조사 문서는 완성 즉시 병합.
- Epic 2~16(투자자 관리, 증권사 기준정보, 증권계좌, 공모주 기준정보, 청약, 배정, 매도, 비용, 수익 계산, 조회 API, 대시보드 집계 API, 웹 계정 설정, 웹 청약·배정 입력, 웹 매도·수익 확인, 웹 조회·대시보드) 전체 Phase 0 조사 완료 및 병합. **로드맵 16개 Epic 전체 조사 완료.**
  - 조사 과정에서 무료 모델(Nemotron 3 Ultra/Super, Laguna S 2.1 등)의 반복적 stall(도구 호출 없이 "완료하겠다"고만 말하고 멈춤) 다수 발생 — Master가 매번 `git diff`/`terminal read`로 실제 진행 상황을 확인한 뒤에만 개입(interrupt → 모델 교체 재시도)함.
  - Epic 14(web-subscription-workflow), Epic 15(web-sale-profit-workflow) 조사 초안이 "실시간 결제/거래 처리"를 전제로 작성되는 오류 발견 — 이 서비스는 실제로는 이미 완료된 청약·매도 결과를 사후 기록하는 서비스라는 로드맵 범위에 맞게 Master가 직접 수정.
- `docs/current-status.md`, `docs/daily-log.md` 신규 생성(9.2 요구사항 이행).

## 2026-09-21

- Epic 2(investor-management) Phase 0 전체(기획 spec + 설계 architecture) 완료 및 병합(PR #49). Master 결정 사항 D-I-001~005 기록(`docs/work-log/investor-management.md`) — 특히 D-I-001: 초안의 UUID 제안을 기존 Account 엔티티(Long IDENTITY)와의 일관성을 위해 Long ID로 최종 결정.
- Epic 3(securities-firm-catalog) spec 작성 완료 및 병합. 초안의 논리 오류(DELETE 엔드포인트가 연결된 계좌 있으면 실패하도록 잘못 작성됨 — 실제로는 논리 삭제이므로 연결 여부와 무관하게 항상 성공해야 함)를 Master가 발견해 수정.
- **OpenRouter 무료 모델 계정 전체에 일일 사용량 한도(`free-models-per-day-high-balance`) 도달** — Nvidia Nemotron 3 Ultra/Super, Poolside Laguna S 2.1, Google Gemma 4종 서로 다른 프로바이더 모두에서 동일한 오류 확인, 특정 모델이 아닌 계정 단위 일일 한도로 최종 판단됨. Epic 3 architecture 작업이 이 시점에서 막힘 — 10분 간격 자동 재시도 루프를 12회(약 1시간 52분) 가동했으나 모두 실패.
- **우회 경로 발견 및 해결**: `opencode/` 프로바이더 자체의 무료 모델(OpenCode Zen)이 OpenRouter와 별개의 quota pool임을 확인 — `opencode/big-pickle`로 즉시 Epic 3 architecture 작성 성공(문서 품질 양호, UUID/DELETE 로직 오류 없음, 다만 Flyway SQL의 `ON UPDATE` 문법 오류를 Master가 직접 수정). PR #50으로 병합 완료. AGENTS.md 22장에 대체 경로 문서화. 사용자 지시("오픈라우터 한도풀릴때 까지는 오픈코드무료모델 이것저것 써보면서 해")에 따라 OpenRouter 한도가 풀릴 때까지는 opencode zen 모델을 우선 사용하기로 함.
- **DB 문법 버그 재발 방지**: Epic 3/4 architecture 문서의 Flyway SQL이 MySQL 문법(AUTO_INCREMENT, ENGINE=InnoDB)과 PostgreSQL 전용 partial index를 한 CREATE TABLE에 섞어 실행 불가능했음을 Master가 발견·수정(이 프로젝트 DB는 PostgreSQL). 이미 병합된 Epic 3 문서도 함께 수정. AGENTS.md에 "PK는 GENERATED ALWAYS AS IDENTITY, 조건부 유니크는 별도 CREATE UNIQUE INDEX" 규칙 추가해 이후 Epic(4~6)부터는 재발하지 않음.
- Epic 4(securities-account-management) Phase 0 완료(PR #51) — spec의 계좌번호 마스킹 자리수가 조사 결론(뒷 4자리)과 다르게 6자리로 작성된 것과 오타 2건을 Master가 수정.
- Epic 5(ipo-catalog) Phase 0 완료(PR #52) — spec/architecture 모두 별도 수정 없이 병합. Architecture 작성 중 무료 모델 2종이 연속으로 "Thinking" 단계에서 멈춰(진행 없이 컨텍스트 크기 고정) 인터럽트 후 재시도, 3번째 모델(Big Pickle)이 첫 시도에서 파일이 중간에 잘렸으나 스스로 감지해 재작성 완료.
- Epic 6(subscription-management) Phase 0 완료(PR #53) — **조사 문서 스코프 오류 발견**: 이 서비스는 사후 기록 서비스인데 조사가 실시간 청약 접수/배정 처리(상태 전이 머신, 낙관적/비관적 락)를 전제로 되어 있었음(Epic 14/15와 동일 패턴). Master가 D-S-001~006 결정을 먼저 내려 `docs/work-log/subscription-management.md`에 기록(배정/환불은 Epic 7로 분리, 단순 ACTIVE/CANCELLED만 사용, 락 메커니즘 불필요)한 뒤 spec/architecture 작업에 반영해 스코프 정정.
- Epic 7(allocation-management) Phase 0 완료(PR #54) — Epic 6과 동일 패턴의 스코프 오류를 조사 문서에서 확인, D-A-001~007 결정(Subscription:Allocation=1:1, ACTIVE 기준 부분 유니크로 CANCELLED 재등록 허용, 잔여수량 계산은 Epic 8/11 분리)을 기록. Worker가 "CANCELLED 후 영구 재등록 불가"로 설계한 부분을 Subscription과의 일관성을 위해 Master가 수정.
- Epic 8(sale-management) Phase 0 완료(PR #55) — 동일 패턴 3회째 반복 확인. D-SL-001~007 결정(상태 머신 없음, 매도수량/일시 포함 전체 필드 자유 수정+재검증, 락 불필요, 수익 계산은 Epic 9/10 분리, Allocation:Sale=1:N)을 기록해 spec/architecture에 반영. 이 과정에서 한 무료 모델이 절대경로(~/orca/...) 접근으로 예기치 못한 권한 다이얼로그를 유발해 인터럽트 후 재시도.
- **반복 패턴 확인**: Epic 6/7/8 세 개 연속으로 조사 문서가 "이미 완료된 거래를 사후 기록"이라는 프로젝트 핵심 전제를 놓치고 실시간 처리(상태 전이, 동시성 락, 엄격한 불변 필드)를 가정함. Epic 9(비용 기록)/10(수익 계산)도 조사 문서 검토 시 동일 패턴 여부를 우선 확인하기로 함.
- Epic 9(cost-recording) Phase 0 완료(PR #56) — 실시간 처리 오류는 없었으나, Epic 8(Sale)이 이미 fee/tax/otherCost 필드로 매도 비용을 기록하도록 설계돼 있어 Epic 9와 책임이 겹치는 문제를 발견. D-C-001~006 결정(Cost는 청약/취득 단계 비용만 담당, 매도 비용과 중복 설계 금지)으로 경계를 정리.
- Epic 10(profit-calculation) Phase 0 완료(PR #57) — 조사 문서가 제안한 CalculationTrace(출처/근거/정책버전/수정이력) 엔티티 설계가 Epic 8/9에서 확립한 "별도 이력 테이블 불필요" 원칙과 배치되어 D-P-001~006으로 대폭 단순화: Profit은 저장하지 않는 파생값(on-demand 계산), 세금은 Sale.tax 그대로 사용(자동계산 절대 금지), 취득가액은 배정금액 단일 기준. 워커가 필드명을 실제 spec에서 grep으로 직접 검증해 작성하는 등 품질이 우수했음.
- **마일스톤**: Epic 2~10 전체 Phase 0(조사→기획→설계) 완료로 핵심 금융 기록 체인(Account→Investor→SecuritiesAccount→IPO→Subscription→Allocation→Sale→Cost→Profit) 전체의 spec/architecture가 준비됨. 남은 Epic 11~16은 이 도메인을 조회·집계·화면으로 노출하는 API/웹 Epic들.
- Epic 11(investment-query-api) Phase 0 완료(PR #58) — 조사 문서가 Sale(매도) 기준 조회를 제안했으나 이러면 미배정/미매도 청약이 결과에서 누락되는 설계 결함을 Master가 발견, D-Q-001~006으로 Subscription 기준(anchor) 조회로 정정. 워커가 Allocation.status에 없는 "CONFIRMED" 값을 예시로 쓴 것도 수정. 이 작업에서 4개 무료 모델이 연속으로 stall/JSON 파싱 오류를 겪어 매번 재시도.
- Epic 12(dashboard-aggregation-api) Phase 0 완료(PR #59) — 조사 문서가 Epic 10/11에서 이미 폐기된 초기 결론을 인용해 D-D-001~007로 정렬. **중대 보안 결함 발견·수정**: architecture 초안이 "단일 사용자 가정이라 소유권 검증 불필요"라고 명시했으나 이 프로젝트는 Epic 1부터 멀티 계정 서비스이므로 그대로면 계정 간 재무 데이터가 섞이거나 노출되는 결함이었음. Master가 SecurityUtils 기반 계정 소유권 필터링을 spec/architecture 양쪽에 추가. 이 작업에서 3개 무료 모델이 연속 stall해 4번째(Big Pickle)가 완료.
- **마일스톤**: Epic 2~12 전체 Phase 0 완료로 백엔드 API 관련 로드맵 전체(계정 인증, 투자자/증권사/계좌/공모주 기준정보, 청약~수익 계산 체인, 조회/집계 API)의 spec/architecture가 준비됨. 남은 Epic 13~16은 이 API들을 사용하는 웹 프론트엔드 화면 Epic들.
- Epic 13(web-account-setup) Phase 0 완료(PR #60) — 첫 프론트엔드 Epic. 조사 문서가 투자자/증권계좌 등록 API 경로를 "미정"으로 남겼으나 Epic 2/4 spec 완성으로 확정되어 D-W13-001~006에 반영. 워커가 회원가입 비밀번호 검증 규칙을 실제 account-authentication spec과 다르게 단순화한 것을 Master가 수정. architecture는 FSD 레이어 구조로 설계, 미확인 프론트 라이브러리는 "package.json 확인 후 결정" 원칙 적용.
