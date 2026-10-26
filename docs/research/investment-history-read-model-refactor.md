# 투자내역 조회 read model 분리 조사

> 조사 기준: `main` / `5855945a` (2026-10-26)
> 상태: 첫 리팩토링 트랙의 조사 기록. 사용자/API 계약은 변경하지 않는다.

## 관찰

- `InvestmentHistoryQueryService`는 상세 조회와 페이지 조회를 모두 제공한다. 페이지 조회는 현재 계정 ID를 받아 `SubscriptionRepository.findByInvestorAccountIdWithFilters`로 계정 범위를 강제하고, 페이지 결과에 한해 Allocation, Sale, IPO, Investor, SecuritiesAccount, SecuritiesFirm을 배치 조회한다.
- 기존 배치 흐름은 N+1 방지에 중요하다. Sale은 allocation ID로 읽은 후 subscription ID 기준으로 재매핑한다. DTO는 `InvestmentHistoryAssembler`가 만든다.
- 서비스에는 목록 경로의 배치 저장소들과 상세 경로의 단건 저장소들이 함께 주입돼 책임이 넓다. `EntityManager` 필드는 현재 소스에서 사용되지 않는다.
- 기존 서비스 테스트는 Spring Boot test context와 저장소 mock을 이용해 계정 범위, 필터, 소유권 오류, 배정·매도 잔여수량, DTO 이름 등을 확인한다. 실제 계약 세부는 구현 전 관련 API/DTO/MockMvc 테스트와 대조한다.
- API 및 DTO 외부 변경, repository 쿼리 변경, pagination 변경은 이번 범위가 아니다.

## 위험과 질문

1. 계정 스코프가 repository 호출에서 빠지면 타 계정 데이터가 섞일 수 있다.
2. 배치 로딩을 레코드별 조회로 바꾸면 N+1이 생길 수 있다.
3. 빈 페이지에서 불필요한 `IN ()` 조회를 하지 않는 현 동작을 보존해야 한다.
4. 같은 투자자/증권계좌/증권사/IPO 데이터가 누락되거나 nullable 처리·순서가 달라질 수 있다.
5. repository 메서드별 실 SQL과 쿼리 수는 프로젝트의 격리 슬롯에서 재확인한다.

## 결론

첫 SF는 목록 조회의 배치 데이터 수집 및 맵 구성을 `InvestmentHistoryReadModelLoader`로 이동하고, `InvestmentHistoryQueryService`는 인증·필터 소유권 검증, page fetch, assembler 호출과 `Page` 반환을 조정하도록 한다. 상세 조회의 단건 경로는 그대로 둔다. 클래스 추출 자체 외에 조회 조건이나 트랜잭션을 변경하지 않는다.

## 조사 완료 기준

- DTO/controller 응답 및 권한 계약을 기존 테스트 위치와 대조한다.
- 배치 조회 호출/빈 결과 회귀를 테스트로 고정한다.
- 실제 변경 뒤 전체 결과 동일성과 쿼리 호출 수를 테스트 증거로 기록한다.
