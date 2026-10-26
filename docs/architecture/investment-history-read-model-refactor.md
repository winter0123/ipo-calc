# 투자내역 read model 분리 아키텍처

## 흐름

```text
Controller
  → InvestmentHistoryQueryService
      → 인증 계정 및 필터 소유권 검증
      → SubscriptionRepository (계정 스코프 + page)
      → InvestmentHistoryReadModelLoader (페이지 단위 배치 조회/map 구성)
      → InvestmentHistoryAssembler (DTO 변환)
      → PageImpl 반환
```

상세 조회도 같은 로더를 사용해 단건 Allocation/Sale/IPO/Investor/Account/Firm 참조 데이터를 모은다. `InvestmentHistoryQueryService`는 Subscription 조회와 존재 확인, 소유권 검증, 로더·assembler 조율을 맡는다. 새 로더는 외부 HTTP 호출, 권한 판단, DTO 정책을 소유하지 않는다.

## 데이터 로딩 규칙

- subscription 목록에서 ID, IPO ID, investor ID, securities account ID를 distinct 집합으로 만든다.
- allocation은 subscription ID 집합으로 읽고 subscription ID → allocation map을 만든다.
- sale은 allocation ID 집합으로 한 번에 읽고 allocation ID → sales map을 만든 뒤 assembler가 요구하는 subscription ID 기준으로 변환한다.
- IPO, Investor, SecuritiesAccount, SecuritiesFirm은 페이지에서 참조되는 ID만 집합 조회한다.
- 입력 subscription이 비면 모든 관련 집합 조회를 생략하고 빈 map 묶음을 반환한다.
- 순서는 subscription page와 assembler 입력의 기존 순서를 따른다. `PageImpl`에 기존 Pageable 및 total elements를 전달한다.

## 책임 경계와 위험

계정 스코프는 현재와 같이 `SubscriptionRepository.findByInvestorAccountIdWithFilters`에 남긴다. 로더로 계정 필터를 옮기지 않는다. Repository 호출 수는 데이터 건수에 따라 선형 증가하지 않아야 한다. 구현 시 실제 `InvestmentHistoryQueryService`, DTO assembler, 관련 repository와 MockMvc 테스트를 다시 대조한다.
