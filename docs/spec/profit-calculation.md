# 수익 계산 기능 명세 (profit-calculation, Epic 10)

## 개요
단일 매도 건 또는 투자자 단위의 **실현 손익(금액)**을 조회 시점에 계산해 반환하는 읽기 전용 기능을 제공한다.
수익(Profit)은 DB에 저장하지 않는 **파생값**이며, 별도의 Profit 테이블이나 CalculationTrace 같은 계산 이력 엔티티를 두지 않는다(D-P-001).
조회 시점에 Sale/Cost/Allocation/IPO 레코드를 조합해 on-demand로 계산하며, 계산 근거는 이미 각 레코드 자체가 무엇을 참조하는지로 추적 가능하다(D-P-001).
이 기능은 쓰기 API가 아니며 결과를 데이터베이스에 저장하지 않는다(D-P-006).

계산 기본 원칙(D-P-002 / D-P-004):
- **세금은 `Sale.tax`를 그대로 사용**한다. 세율·공제 자동 계산 로직, 세법 정책 엔진, 세율 테이블은 만들지 않는다.
- **연환산 수익률(XIRR 등)은 이 Epic 범위 밖**이다. 세전/세후 실현 손익(금액)까지만 계산한다.

> **Epic 8/9와의 책임 경계**: 수익 공식의 각 항목은 에픽별로 이미 확정된 필드를 그대로 사용한다(D-SL-007, D-C-001).
> `매도금액(Sale.amount) − 대응취득가액 − 대응취득비용(Cost) − 매도수수료(Sale.fee) − 세금(Sale.tax) − 기타비용(Sale.otherCost) = 순수익`
> 취득 비용 항목은 Epic 9의 Cost, 매도 수수료/세금/기타 비용 항목은 Epic 8의 Sale 필드에 각각 대응한다.

## 용어 정의
- **Profit**: 순수익(파생값). 저장하지 않으며 조회 시점에 계산해 응답한다(D-P-001). ID가 없는 DTO로 응답한다.
- **파생값(derived value)**: DB에 저장되지 않고, 계산에 쓰는 원천 레코드(Sale/Cost 등)가 변경되면 다음 조회 시 자동으로 다시 계산되어 갱신되는 값
- **Sale**: 매도 기록 엔티티 (Epic 8, D-SL-007) — `amount`(매도금액), `quantity`(매도수량, 주), `fee`/`tax`/`otherCost`(선택), `status`(ACTIVE/CANCELLED)
- **Allocation**: 배정 결과 엔티티 (Epic 7) — `allocatedQuantity`(배정수량, 주), `allocationPrice`(배정단가, 선택)
- **IPO**: 공모주 기준정보 엔티티 — `fixedPrice`(확정공모가, 선택 — 확정 전 null)
- **취득가액 기준(배정금액)**: `Allocation.allocatedQuantity × allocationPrice` 로 산정하는 단일 취득가액 기준 (D-P-005)
- **대응취득가액**: 특정 매도 건에 대응하는 취득가액 (`Allocation.allocationPrice × Sale.quantity`, D-P-003)
- **대응취득비용**: 특정 매도 건에 안분된 취득 비용 합계 (Cost 안분, D-P-003)
- **안분 비율**: `Sale.quantity / Allocation.allocatedQuantity`
- **잔차**: 분할 매도 누적 시 반올림 때문에 배정금액/비용 총합과 원 단위로 어긋나는 차이. 그대로 허용한다(D-P-003)

## 계산 공식 및 안분 로직 (D-P-003, D-P-005)

### 1. 취득가액 기준
취득가액은 **배정금액 단일 기준**을 MVP 기본값으로 사용한다(D-P-005).
`배정 총 취득가액 = Allocation.allocatedQuantity × Allocation.allocationPrice`

### 2. 해당 매도의 대응취득가액 (안분)
```
대응취득가액 = Allocation.allocationPrice × Sale.quantity
```
- `Allocation.allocationPrice`가 null이면 참조 IPO의 `fixedPrice`로 대체한다 (D-P-003):
  `대응취득가액 = IPO.fixedPrice × Sale.quantity` (참조 경로: Sale → Allocation → Subscription → IPO)
- 둘 다 null이면 취득가액 산정 불가 상태로 보고 0으로 계산한다 (엣지케이스 참고)

### 3. 해당 매도의 대응취득비용 (Cost 안분)
취득 비용도 취득가액과 **동일 비율로 안분**한다(D-P-003).
```
안분 비율 r = Sale.quantity / Allocation.allocatedQuantity
대응취득비용 = ROUND( Σ(ACTIVE Cost.amount) × r )
```
- 합산 대상: `Allocation.subscriptionId`와 일치하는 **ACTIVE 상태의 Cost** (CANCELLED 제외)
- 각 매도 건은 **독립적으로 원 단위로 반올림**하며, 분할 매도 누적 시 합계가 배정금액·비용 총합과 원 단위로 어긋나는 잔차는 그대로 허용한다(D-P-003)

### 4. 순수익 계산
```
세전순수익 = Sale.amount − 대응취득가액 − 대응취득비용 − Sale.fee − Sale.otherCost
세후순수익 = 세전순수익 − Sale.tax
```
- `Sale.fee` / `Sale.tax` / `Sale.otherCost`는 선택 필드이므로 null이면 0으로 간주한다
- `Sale.amount`는 매도금액(사용자 입력값)을 그대로 사용하며 시스템이 재계산하지 않는다

## API 엔드포인트 목록
> 모두 읽기 전용 GET이며, 계산 결과를 저장하지 않는다(D-P-001, D-P-006).

### 1. 특정 매도건 수익 계산
- **GET** `/sales/{saleId}/profit`
- **경로 파라미터**: saleId (Long 타입)
- **응답** (성공 — 200 OK):
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
  - `matchedAcquisitionAmount`: 대응취득가액 (D-P-003)
  - `matchedCostAmount`: 대응취득비용 (D-P-003)
  - `preTaxProfit`: 세전순수익 = saleAmount − matchedAcquisitionAmount − matchedCostAmount − saleFee − saleOtherCost
  - `afterTaxProfit`: 세후순수익 = preTaxProfit − saleTax
  - Profit은 파생값이므로 **id 필드를 포함하지 않는다** (D-P-001)
- **응답** (실패 - 존재하지 않는 Sale 또는 CANCELLED 상태의 Sale): 404 Not Found
- 비고: 이 계산 결과는 저장되지 않으며, 추후 Sale/Cost가 수정되면 다음 조회에서 자동으로 다시 계산된다

### 2. 투자자별 전체 수익 목록
- **GET** `/investors/{investorId}/profits`
- **경로 파라미터**: investorId (Long 타입)
- **응답** (성공 — 200 OK):
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
  - `sales`: 해당 투자자의 **ACTIVE 상태 Sale** 각각에 대해 1번 엔드포인트와 동일한 항목으로 계산한 목록 (조회 경로: Sale → Allocation → Subscription → Investor)
  - `totalPreTaxProfit` / `totalAfterTaxProfit`: 전체 매도 건의 세전/세후 순수익 합계 (잔차는 허용하되 합계 자체는 각 매도 건 결과를 단순 합산)
  - 정렬 순서: `saleDate` 오름차순, 동일 saleDate인 경우 `id` 오름차순
- **응답** (실패 - 존재하지 않는 Investor): 404 Not Found
- 비고: 매도 건이 없으면 `sales`는 빈 배열, 합계는 0으로 응답한다

## Acceptance Criteria
**AC-001**: 단일 매도건 수익 계산 성공
- Given: ACTIVE 상태의 Sale이 존재하고, 배정금액/비용 기준 데이터가 준비되어 있음
- When: GET /sales/{saleId}/profit 요청이 전송됨
- Then: 200 OK 응답이 반환되고, saleAmount − matchedAcquisitionAmount − matchedCostAmount − saleFee − saleOtherCost − saleTax = afterTaxProfit인 유효한 Profit 값이 반환됨

**AC-002**: 세금 차감 계산 (Sale.tax 반영)
- Given: tax가 0이 아닌 값으로 입력된 Sale이 존재함
- When: GET /sales/{saleId}/profit 요청이 전송됨
- Then: afterTaxProfit = preTaxProfit − Sale.tax 로 계산되고, Sale.tax가 그대로 차감됨 (세율/공제 계산 없음 — D-P-002)

**AC-003**: 분할 매도 시 취득가액 안분 계산
- Given: 배정수량 100인 Allocation에 두 건의 매도(30주, 70주)가 등록되어 있고 allocationPrice가 12000원임
- When: 30주 매도 건의 GET /sales/{saleId}/profit 요청이 전송됨
- Then: matchedAcquisitionAmount = 12000 × 30 = 360000으로 계산됨 (D-P-003)

**AC-004**: 분할 매도 시 Cost 안분 계산
- Given: 대응 청약에 ACTIVE Cost 합계 40000원이 있고, 배정수량 100에 대한 매도 30주 건이 존재함
- When: GET /sales/{saleId}/profit 요청이 전송됨
- Then: matchedCostAmount = ROUND(40000 × 30 / 100) = 12000으로 계산됨 (D-P-003)

**AC-005**: allocationPrice가 없는 경우 fixedPrice 대체
- Given: Allocation.allocationPrice가 null이고, 해당 청약이 참조하는 IPO의 fixedPrice가 11000원임
- When: GET /sales/{saleId}/profit 요청이 전송됨
- Then: matchedAcquisitionAmount = 11000 × Sale.quantity로 계산됨 (D-P-003 대체 규칙)

**AC-006**: Cost가 없는 경우 계산
- Given: 대응 청약에 등록된 Cost가 없는 Sale이 존재함
- When: GET /sales/{saleId}/profit 요청이 전송됨
- Then: matchedCostAmount = 0으로 계산되고, 손익 계산에 영향을 주지 않음

**AC-007**: 잔차 허용
- Given: 분할 매도가 여러 건 존재하고 각 매도 건이 독립적으로 반올림되어 배정금액/비용 총합과 원 단위로 어긋남
- When: 각 매도 건의 수익을 개별 계산함
- Then: 잔차를 재분배/보정하지 않고 각 매도 건의 계산값을 그대로 반환함 (D-P-003)

**AC-008**: 취소(CANCELLED) 데이터 제외
- Given: CANCELLED 상태의 Cost와 CANCELLED 상태의 Sale이 존재함
- When: 수익 계산 요청이 전송됨
- Then: CANCELLED Cost는 matchedCostAmount에 포함되지 않고, CANCELLED Sale은 투자자별 목록에서 제외되며 단일 조회 시 404로 처리됨

**AC-009**: 존재하지 않는 Sale 조회 실패
- Given: 존재하지 않는 saleId가 제공됨
- When: GET /sales/{saleId}/profit 요청이 전송됨
- Then: 404 Not Found 응답이 반환됨

**AC-010**: 투자자별 수익 목록 조회 성공
- Given: 한 투자자에 여러 ACTIVE Sale이 존재함
- When: GET /investors/{investorId}/profits 요청이 전송됨
- Then: 200 OK 응답이 반환되고, 각 매도 건의 Profit 목록과 세전/세후 합계가 포함됨

**AC-011**: 존재하지 않는 Investor 조회 실패
- Given: 존재하지 않는 investorId가 제공됨
- When: GET /investors/{investorId}/profits 요청이 전송됨
- Then: 404 Not Found 응답이 반환됨

**AC-012**: 매도 건이 없는 투자자 조회 성공
- Given: 투자자는 존재하지만 매도 기록이 없음
- When: GET /investors/{investorId}/profits 요청이 전송됨
- Then: 200 OK 응답이 반환되고, sales는 빈 배열이며 합계는 0임

**AC-013**: 읽기 전용·저장 없음 확인
- Given: 수익 계산 API를 호출할 수 있는 상태임
- When: GET /sales/{saleId}/profit 및 GET /investors/{investorId}/profits 요청이 전송됨
- Then: 응답 후 데이터베이스에 추가·변경되는 레코드가 없음 (D-P-001, D-P-006)

## 엣지케이스
- **Profit 엔티티 금지 (D-P-001)**: 별도의 Profit 테이블, CalculationTrace 등 계산 이력/출처 메타데이터 엔티티를 설계하지 않는다. 계산은 on-demand이며 근거는 원천 레코드 자체로 추적한다
- **세율·공제 자동 계산 금지 (D-P-002)**: `Sale.tax`는 사용자가 입력한 실제 세액이며, 세율 추정·자동 계산, 세법 정책 엔진, 세율 테이블을 어떤 형태로든 설계·구현하지 않는다
- **연환산 수익률 범위 밖 (D-P-004)**: XIRR 등 연환산 수익률, 기간 대비 수익률은 설계·계산하지 않는다. 세전/세후 실현 손익(금액)까지만 제공한다
- **취득가액 단일 기준 (D-P-005)**: 취득가액은 배정금액(allocatedQuantity × allocationPrice) 기준만 사용한다. 청약금액/증거금/환불금액 기준 선택 UI는 범위 밖이며, 해당 값들은 Subscription/Cost에 이미 보존되어 있어 손실되지 않는다. 세법 공식이 추후 확인되면 계산 로직만 교체 가능하도록 결합도를 낮게 유지한다
- **선택 필드 null 처리**: `Sale.fee`/`Sale.tax`/`Sale.otherCost`는 null 허용 필드로 null이면 0으로 간주하고, 응답에서 해당 필드를 항상 숫자로 반환한다
- **취득가액 산정 불가(양쪽 단가 null)**: `Allocation.allocationPrice`와 `IPO.fixedPrice`가 모두 null이면 대응취득가액을 0으로 계산하고 그 값을 그대로 응답한다. 이는 사용자 데이터 입력 누락 상태이며 손익이 과대 계상될 수 있음을 의미한다 — 자동 보정 로직을 두지 않는다
- **배정수량 0 방어**: `Allocation.allocatedQuantity`는 0 이상 허용이지만, 매도 등록 검증(D-SL-003)이 Σ(ACTIVE 매도수량) ≤ 배정수량을 보장하므로 실제 데이터에서 매도가 연결된 배정의 배정수량은 1 이상이다. 0인 경우에도 나눗셈 오류가 나지 않도록 안분 비율을 방어적으로 처리한다
- **과매도 미발생**: 누적 매도수량이 배정수량을 초과하는 Sale은 Epic 8 등록/수정 검증으로 발생하지 않으므로 이 Epic에서 별도 검증하지 않는다
- **CANCELLED 참조 관계**: Allocation/Subscription이 CANCELLED되어도 연결된 Sale·Cost 기록은 보존되지만(사실 기록), 수익 계산 시 참조 경로 상의 원천 데이터는 **ACTIVE 기준**으로만 사용한다. CANCELLED Sale은 단일 조회에서 404, 목록에서 제외한다
- **반올림 정책**: 모든 금액은 원 단위 Long이며, 안분 계산은 각 매도 건 단위로 독립 반올림한다. 분할 매도 누적 합계와 배정금액·비용 총합의 원 단위 잔차는 재배분하지 않는다(D-P-003)
- **동시성 제어 불필요**: 읽기 전용 계산 API이므로 낙관적/비관적 락, 분산 락을 도입하지 않는다
- **캐시/저장 없음**: 계산 결과를 캐싱하거나 저장하지 않는다. 원천 데이터가 수정되면 다음 조회에서 항상 재계산한다(D-P-001)

## 데이터 흐름
### Entity 참조 관계도
```
Investor (1) <---- (N) Subscription (1) <---- (1:1) Allocation (1) <---- (N) Sale
                      |                             |
                      |                             +----> IPO (Subscription.ipoId, fixedPrice 대체 단가)
                      +---- (N) Cost
```
- Sale은 Allocation을 N:1로 참조 (외래 키: allocationId — D-SL-006)
- Allocation은 Subscription을 정확히 1:1로 참조 (외래 키: subscriptionId — D-A-002)
- Cost는 Subscription을 N:1로 참조 (외래 키: subscriptionId — D-C-001)
- 수익 계산은 이 참조 체인을 따라 Sale → Allocation → Subscription → Cost/IPO 를 조합해 수행한다

### GET /sales/{saleId}/profit 계산 흐름
```
1. Sale 조회 (ACTIVE, 없으면 404)
2. Allocation 조회 (Sale.allocationId)
3. Subscription 조회 (Allocation.subscriptionId)
4. 참조 IPO 조회 (Subscription.ipoId — allocationPrice가 없을 때만 fixedPrice 사용)
5. 대응취득가액 = Allocation.allocationPrice × Sale.quantity
       (allocationPrice null → IPO.fixedPrice × Sale.quantity)
6. 대응취득비용 = ROUND( Σ(ACTIVE Cost.amount | subscriptionId) × (Sale.quantity / Allocation.allocatedQuantity) )
7. 세전순수익 = Sale.amount − 대응취득가액 − 대응취득비용 − Sale.fee − Sale.otherCost
8. 세후순수익 = 세전순수익 − Sale.tax
9. Profit DTO 응답 (id 없음, 저장 없음)
```

### GET /investors/{investorId}/profits 계산 흐름
```
1. Investor 조회 (없으면 404)
2. Investor 참조 경유로 ACTIVE Sale 전체 조회 (Sale → Allocation → Subscription → Investor)
3. 매 Sale마다 위 단일 계산 흐름(2~9)을 동일하게 적용해 Profit 목록 생성
4. totalSaleAmount / totalPreTaxProfit / totalAfterTaxProfit 합산 후 응답 (저장 없음)
```

### Cost → Profit 관계 (Epic 10 반영 완료)
- 취득 관련 비용의 합계가 수익 계산의 차감 항목으로 사용된다: `매도금액 − 취득 관련 비용 − 매도 수수료 − 세금 − 기타 비용 = 순수익`
- Cost 도메인 자체에는 수익 계산 로직을 두지 않는다 (D-C-005) — 이 Epic의 계산 서비스가 ACTIVE Cost를 조회해 안분한다
- 원천 레코드(판매·비용·배정·공모가)가 수정되더라도 저장된 Profit이 없으므로 별도 재계산 작업이 필요 없다 (D-P-001)