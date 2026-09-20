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
- **OpenRouter 무료 모델 계정 전체에 일일 사용량 한도(`free-models-per-day-high-balance`) 도달** — Nvidia Nemotron 3 Ultra/Super, Poolside Laguna S 2.1 등 서로 다른 프로바이더 3종 모두에서 동일한 오류 확인, 특정 모델이 아닌 계정 단위 일일 한도로 판단됨. Epic 3 architecture 작업이 이 시점에서 막힘 — 10분 간격으로 자동 재시도하는 루프를 가동해 한도가 풀리는 즉시 이어서 진행하도록 설정.
