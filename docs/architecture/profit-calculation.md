# 아키텍처 설계: 수익 계산 (profit-calculation, Epic 10)

## 아키텍처 개요 (읽기 전용 계산 서비스)

이 Epic은 **읽기 전용 계산 서비스**이다. DB에 새로운 테이블이나 엔티티를 만들지 않으며, 기존 Sale/Cost/Allocation/Subscription/IPO 레코드를 조합해 on-demand로 순수익을 계산한다(D-P-001, D-P-006).

레이어 구조는 Sale Management 아키텍처와 동일하게 따른다:

```
controller → service → domain → repository
```

다만 이 Epic은 **쓰기 엔티티가 없으므로** domain 계층에 Profit 엔티티가 없다. 기존 Repository를 조합하여 참조 데이터를 가져오고, Service에서 계산을 수행한 뒤 DTO로 응답한다.

### 구성 컴포넌트

- `controller.profit`: `ProfitController` — HTTP GET 요청 처리
- `service.profit`: `ProfitCalculationService` — 계산 로직 오케스트레이션
- `dto.profit`: `ProfitDto` — 응답 DTO (id 없음)
- `domain`: Profit 도메인 엔티티 없음
- `repository`: ProfitRepository 없음 — 기존 `SaleRepository`, `CostRepository`, `AllocationRepository`, `SubscriptionRepository`, `IpoRepository`를 조합 사용

### 참조 엔티티 (기존)

| 엔티티 | 소유 Epic | 이 Epic에서 사용하는 필드 |
|--------|----------|------------------------|
| Sale | Epic 8 | id, allocationId, quantity, amount, fee, tax, otherCost, status |
| Allocation | Epic 7 | id, subscriptionId, allocatedQuantity, allocationPrice |
| Subscription | Epic 6 | id, investorId, ipoId |
| Cost | Epic 9 | id, subscriptionId, amount, status |
| IPO | Epic 5 | id, fixedPrice |

---

## 계산 서비스 설계

### ProfitCalculationService

단일 매도건 및 투자자별 계산을 담당하는 읽기 전용 서비스. 트랜잭션 경계는 `@Transactional(readOnly = true)`로 선언한다.

#### `ProfitCalculationService.calculateForSale(Long saleId): ProfitDto`

단일 매도건의 순수익을 계산한다. 계산 흐름은 spec의 데이터 흐름을 그대로 따르며, 각 단계에서 어떤 Repository 메서드를 호출하는지는 아래와 같다.

**계산 흐름과 Repository 호출:**

1. **Sale 조회** — `saleRepository.findByIdAndStatusAndDeletedAtIsNull(saleId, "ACTIVE")`
   - 결과가 없으면 404 Not Found 반환
2. **Allocation 조회** — `allocationRepository.findById(sale.getAllocationId())`
   - Sale.allocationId로 조회
3. **Subscription 조회** — `subscriptionRepository.findById(allocation.getSubscriptionId())`
   - Allocation.subscriptionId로 조회
4. **IPO 조건부 조회** — `allocation.getAllocationPrice()`가 null이면 `ipoRepository.findById(subscription.getIpoId())`로 `fixedPrice`를 가져온다
   - allocationPrice가 null이 아닌 경우 IPO 조회 불필요
5. **ACTIVE Cost 조회** — `costRepository.findBySubscriptionIdAndStatusAndDeletedAtIsNull(subscriptionId, "ACTIVE")`
   - Σ(ACTIVE Cost.amount)를 합산
6. **안분 계산** — Service 내부 private 메서드 또는 `ProfitCalculator` 유틸리티 클래스에서 처리
7. **ProfitDto 구성 및 반환**

#### `ProfitCalculationService.calculateForInvestor(Long investorId): InvestorProfitDto`

투자자별 전체 수익 목록을 계산한다.

**계산 흐름과 Repository 호출:**

1. **Investor 조회** — `investorRepository.findById(investorId)` — 존재하지 않으면 404
2. **ACTIVE Sale 목록 조회** — `saleRepository.findBySubscriptionInvestorIdAndStatusAndDeletedAtIsNull(investorId, "ACTIVE")`
   - 또는 `saleRepository.findActiveByInvestorId(investorId)` 커스텀 쿼리
3. **배치 조회 (N+1 방지)** — 조회된 Sale 목록에서 allocationId 집합을 추출하여:
   - `allocationRepository.findByIdIn(allocationIds)` — 배치로 Allocation 목록 조회
   - `subscriptionRepository.findByIdIn(subscriptionIds)` — 배치로 Subscription 목록 조회
   - `costRepository.findBySubscriptionIdInAndStatusAndDeletedAtIsNull(subscriptionIds, "ACTIVE")` — 배치로 ACTIVE Cost 목록 조회
   - 필요 시 `ipoRepository.findByIdIn(ipoIds)` — 배치로 IPO 목록 조회
   - **대안**: `Sale`에 JOIN FETCH를 사용해 `saleRepository.findActiveWithJoinsByInvestorId(investorId)`로 한 번에 조회
4. **매 Sale별 계산** — Step 1의 단일 계산 흐름을 각 Sale에 적용 (ProfitCalculator 활용)
5. **합산** — totalSaleAmount, totalPreTaxProfit, totalAfterTaxProfit 계산
6. **정렬** — saleDate 오름차순, 동일 시 id 오름차순
7. **InvestorProfitDto 구성 및 반환**

### N+1 쿼리 문제 방지 방향

투자자별 목록 조회 시 N+1 문제가 발생하지 않도록 다음 방향을 따른다:

- **방안 A (배치 조회)**: Sale 목록을 먼저 가져온 뒤, 참조되는 Allocation/Subscription/Cost/IPO를 `findByIdIn()`으로 일괄 조회한다. Map으로 캐싱하여 매 Sale 계산 시 재조회하지 않도록 한다.
- **방안 B (JOIN FETCH)**: `@EntityGraph` 또는 `@Query`에 `JOIN FETCH`를 사용해 Sale → Allocation → Subscription → Investor를 한 번에 로드한다. Cost는 별도 배치 조회로 처리한다(다대다 관계이므로 JOIN FETCH로 Cost까지 포함하기 어려움).
- **선택 기준**: 구현 단계에서 Sale의 연관 관계 복잡도와 데이터량을 기준으로 결정한다. MVP에서는 방안 A(배치 조회)가 더 명확하다.

---

## 계산 로직 구현 위치

안분/반올림 계산 로직은 `ProfitCalculationService` 내부의 `private` 메서드 또는 별도 유틸리티 클래스 `ProfitCalculator`로 분리하는 방향이다.

### ProfitCalculator (유틸리티 클래스, 선택)

`ProfitCalculator`가 도입되는 경우 책임:

- `calculateAcquisitionAmount(allocationPrice, saleQuantity)`: 대응취득가액 계산 — `allocationPrice × saleQuantity`
- `calculateAcquisitionAmountWithFallback(allocationPrice, fixedPrice, saleQuantity)`: allocationPrice가 null이면 fixedPrice 대체
- `calculateMatchedCost(totalActiveCost, saleQuantity, allocatedQuantity)`: 대응취득비용 계산 — `ROUND(totalActiveCost × saleQuantity / allocatedQuantity)`
- `calculatePreTaxProfit(saleAmount, acquisitionAmount, matchedCost, fee, otherCost)`: 세전순수익 계산
- `calculateAfterTaxProfit(preTaxProfit, tax)`: 세후순수익 계산
- `calculateAllocationRatio(saleQuantity, allocatedQuantity)`: 안분 비율 계산 (방어적 처리 포함)

`ProfitCalculationService`는 Repository 호출과 오케스트레이션만 담당하고, 순수 계산은 `ProfitCalculator`에 위임한다.

**결합도 낮게 유지**: 수익 공식의 각 항목은 기존 엔티티 필드에 직접 대응하므로, 세율/공제 계산 로직이 추후 변경되더라도 `ProfitCalculator`의 메서드 교체로 대응 가능하도록 설계한다.

---

## 성능/캐싱 관련

### 캐시하지 않는 이유

계산 결과를 캐싱하지 않는다(D-P-001). 그 이유와 트레이드오프:

- **이유**: Profit은 파생값으로, 원천 레코드(Sale/Cost/Allocation/IPO)가 수정되면 캐시된 값이 stale가 된다. 캐시를 도입하면 캐시 무효화 로직이 필요하고, 원인 추적이 어려워진다(D-P-001: 계산 근거는 원천 레코드 자체로 추적 가능해야 함).
- **트레이드오프**: 매 조회마다 원천 데이터를 조회하고 계산하므로, 동일 요청 반복 시 성능이 떨어진다. 그러나 이 Epic은 읽기 전용 API이며, 계산 자체가 단순한 사칙연산과 Repository 조회이므로 성능 영향은 제한적이다.
- **확장성**: 향후 조회 빈도가 증가할 경우, Application 레벨의 캐시(LRU 등)를 도입할 수 있으나, 이 Epic에서는 계산 결과의 항상성을 보장하는 것이 우선이다.

---

## API 설계

### 엔드포인트 목록

모두 읽기 전용 GET이며, 계산 결과를 저장하지 않는다(D-P-001, D-P-006).

### 1. 특정 매도건 수익 계산

- **GET** `/sales/{saleId}/profit`
- **경로 파라미터**: saleId (Long)
- **응답 (성공 — 200 OK)**: `ProfitDto`

```json
{
  "saleId": 10,
  "saleDate": "2026-10-15T14:30:00",
  "allocationId": 3,
  "quantity": 30,
  "saleAmount": 750000,
  "matchedAcquisitionAmount": 360000,
  "matchedCostAmount": 12000,
  "saleFee": 1500,
  "saleTax": 0,
  "saleOtherCost": 0,
  "preTaxProfit": 376500,
  "afterTaxProfit": 376500
}
```

**에러 응답:**
- 존재하지 않는 Sale 또는 CANCELLED 상태의 Sale: **404 Not Found**

**응답 필드 설명:**
- `matchedAcquisitionAmount`: 대응취득가액 (D-P-003)
- `matchedCostAmount`: 대응취득비용 (D-P-003)
- `preTaxProfit`: 세전순수익 = saleAmount − matchedAcquisitionAmount − matchedCostAmount − saleFee − saleOtherCost
- `afterTaxProfit`: 세후순수익 = preTaxProfit − saleTax
- Profit은 파생값이므로 **id 필드 없음** (D-P-001)
- `saleFee`/`saleTax`/`saleOtherCost`는 null이면 0으로 반환

### 2. 투자자별 전체 수익 목록

- **GET** `/investors/{investorId}/profits`
- **경로 파라미터**: investorId (Long)
- **응답 (성공 — 200 OK)**: `InvestorProfitDto`

```json
{
  "investorId": 1,
  "sales": [
    {
      "saleId": 10,
      "saleDate": "2026-10-15T14:30:00",
      "allocationId": 3,
      "quantity": 30,
      "saleAmount": 750000,
      "matchedAcquisitionAmount": 360000,
      "matchedCostAmount": 12000,
      "saleFee": 1500,
      "saleTax": 0,
      "saleOtherCost": 0,
      "preTaxProfit": 376500,
      "afterTaxProfit": 376500
    }
  ],
  "totalSaleAmount": 750000,
  "totalPreTaxProfit": 376500,
  "totalAfterTaxProfit": 376500
}
```

**에러 응답:**
- 존재하지 않는 Investor: **404 Not Found**
- 매도 건이 없는 투자자: **200 OK** (sales 빈 배열, 합계 0)

**응답 필드 설명:**
- `sales`: 해당 투자자의 ACTIVE Sale 각각에 대한 ProfitDto 목록 (saleDate 오름차순, 동일 시 id 오름차순)
- `totalPreTaxProfit` / `totalAfterTaxProfit`: 전체 매도 건의 세전/세후 순수익 합계 (잔차 허용, 단순 합산)

### DTO 정의

#### ProfitDto (id 없음)

```
필드: saleId(Long), saleDate(LocalDateTime), allocationId(Long), quantity(Long),
      saleAmount(Long), matchedAcquisitionAmount(Long), matchedCostAmount(Long),
      saleFee(Long), saleTax(Long), saleOtherCost(Long),
      preTaxProfit(Long), afterTaxProfit(Long)
```

- **id 필드 없음** — Profit은 파생값이므로(D-P-001)
- 모든 금액 필드는 Long (원 단위)
- null 허용 필드(fee/tax/otherCost)는 0으로 기본값 처리

#### InvestorProfitDto

```
필드: investorId(Long), sales(List<ProfitDto>),
      totalSaleAmount(Long), totalPreTaxProfit(Long), totalAfterTaxProfit(Long)
```

### 에러 응답 형식

```json
{
  "error": "NOT_FOUND",
  "message": "해당 매도 건을 찾을 수 없습니다."
}
```

**주요 에러 코드:**
- `NOT_FOUND`: 매도 건 또는 투자자를 찾을 수 없음 (CANCELLED 포함)
- `VALIDATION_ERROR`: 경로 파라미터 검증 실패 (예: null 값)

---

## 모듈 소유권

| 컴포넌트 | 패키지 | 소유 |
|---------|--------|------|
| ProfitController | `controller.profit` | 이 Epic |
| ProfitCalculationService | `service.profit` | 이 Epic |
| ProfitDto | `dto.profit` | 이 Epic |
| InvestorProfitDto | `dto.profit` | 이 Epic |
| ProfitCalculator (유틸리티) | `util.profit` 또는 `service.profit.calculator` | 이 Epic |
| SaleRepository | `repository.sale` | Epic 8 (기존) |
| CostRepository | `repository.cost` | Epic 9 (기존) |
| AllocationRepository | `repository.allocation` | Epic 7 (기존) |
| SubscriptionRepository | `repository.subscription` | Epic 6 (기존) |
| IpoRepository | `repository.ipo` | Epic 5 (기존) |

---

## 의존성

- **JPA/Hibernate**: 기존 spring-boot-starter-data-jpa 재사용 (신규 라이브러리 없음)
- **기존 Repository 재사용**: SaleRepository, CostRepository, AllocationRepository, SubscriptionRepository, IpoRepository — 모두 이미 구현됨
- **추가 라이브러리 불필요**: 캐시 라이브러리, 락 라이브러리, 외부 파싱 라이브러리 등이 필요 없다
- **Lombok**: 기존 프로젝트에서 사용하는 경우 DTO에 적용

---

## 리스크

1. **복잡한 참조 체인 성능**: Sale → Allocation → Subscription → Cost/IPO 경유 조회에서 JOIN이 깊어질 수 있다. N+1 방지를 위해 배치 조회 또는 JOIN FETCH를 적용하되, 구현 단계에서 EXPLAIN 검증이 필요하다.
2. **데이터 입력 누락**: Allocation.allocationPrice와 IPO.fixedPrice가 모두 null이면 대응취득가액이 0이 되어 손익이 과대 계상된다. 이는 사용자 데이터 입력 누락 상태이며, 자동 보정 로직을 두지 않는다(D-P-003).
3. **안분 잔차**: 분할 매도 누적 시 배정금액/비용 총합과 원 단위로 어긋나는 잔차가 발생하며, 이를 재분배하지 않는다(D-P-003). 투자자별 합계에서도 동일한 잔차가 유지된다.
4. **CANCELLED 참조 관계**: Allocation/Subscription이 CANCELLED되어도 연결된 Sale·Cost 기록은 보존되지만, 수익 계산 시 ACTIVE 기준만 사용한다. 잘못된 필터링 시 CANCELLED 데이터가 포함될 수 있다.
5. **선택 필드 null 처리**: Sale.fee/tax/otherCost는 null 허용 필드이므로, 모든 계산 경로에서 null → 0 변환이 정확히 수행되어야 한다. 하나의 필터링 누락으로 전체 계산이 달라질 수 있다.
6. **읽기 전용 보장**: 이 Epic의 API는 GET만 제공하지만, 향후 다른 엔드포인트가 추가될 때 읽기 전용임을 명확히 문서화하고 컨트리뷰터 가이드에 명시해야 한다.

---

## 비고

- **Flyway 마이그레이션**: 이 Epic은 새로운 DB 테이블을 만들지 않으므로 Flyway 마이그레이션이 필요 없다.
- **도메인 엔티티**: Profit 엔티티가 없으므로 domain 계층에 새 클래스를 추가하지 않는다.
- **Repository**: ProfitRepository가 없으므로 기존 5개 Repository의 메서드를 조합하여 사용한다.
- **동시성 제어**: 읽기 전용 계산 API이므로 낙관적/비관적 락, 분산 락을 도입하지 않는다.
- **세율/공제 자동 계산**: 세율·공제 자동 계산 로직, 세법 정책 엔진, 세율 테이블을 설계하지 않는다(D-P-002). Sale.tax는 사용자가 입력한 실제 세액을 그대로 사용한다.
