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
