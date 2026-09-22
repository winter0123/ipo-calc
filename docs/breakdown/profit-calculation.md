# 수익 계산 — 세부 기능 분해 (profit-calculation breakdown)

> **Epic**: Epic 10 (수익·수익률 계산, profit-calculation)
> **분야**: 백엔드 (Java 21 + Spring Boot + PostgreSQL) — backend 워크스페이스
> **작성일**: 2026-09-22
> **API 계약 출처**: docs/spec/profit-calculation.md (AC-001~013, D-P-001~005), docs/architecture/profit-calculation.md
> **의존 관계**: Epic 5 (IPO) + Epic 6 (subscription) + Epic 7 (allocation) + Epic 8 (sale) 구현 완료 필요 — Sale/Cost/Allocation/Subscription/IPO 엔티티 및 Repository가 선행되어야 함. **특히 Epic 9 (cost-recording)가 main에 머지된 뒤에야 CostRepository가 존재하므로, SF-2/SF-3 컴파일이 가능한 시점 = Epic 8·9 모두 머지 완료**

---

## 이 Epic의 특이사항 (분해 전에 반드시 인지)

1. **자체 테이블/엔티티가 없다.** Sale(Epic 8) → Allocation(Epic 7) → Subscription(Epic 6) → Cost(Epic 9)/IPO(Epic 5)를 JOIN해 수익을 on-demand로 계산하는 **읽기 전용 계산 서비스**다. domain 계층에 Profit 엔티티나 ProfitRepository를 만들지 않고, 기존 5개 Repository(SaleRepository, CostRepository, AllocationRepository, SubscriptionRepository, IpoRepository)를 조합한다 (architecture.md "구성 컴포넌트").
2. **Flyway 마이그레이션이 없다.** 이 Epic은 DB 스키마 변경이 전혀 없으므로, Epic 8/9의 breakdown(5개 SF, 엔티티→등록→조회→수정→취소 CRUD 구조)과 달리 쓰기 API도 없고 마이그레이션도 없다. 대신 **기반 계층 → 단일 계산 API → 집계 API** 순서로 3개 SF로 나눈다.
3. **실현손익(Realized P&L)만 계산한다.** 이 Epic은 사용자가 실제 매도(Sale)한 건을 기반으로 수익을 계산한다. mark-to-market이나 현재 가격 대비 미실현 손익(unrealized P&L)은 범위 밖이다 (D-P-004). "분할 매도 시 안분"은 하나의 배정금액을 여러 매도 건에 나누어 배정하는 것이지, 미실현 이익과 무관하다.
4. **SF-1도 구현 착수 게이트(Epic 8·9 머지)를 따른다.** 기술적으로 SF-1(DTO/에러/ProfitCalculator)은 Sale/Cost 엔티티를 직접 참조하지 않지만, ProfitCalculator의 메서드 시그니처가 CostRepository의 반환 타입에 묶여 있고, 전체 모듈이 `com.vibeaiteam.profitcalculation` 패키지로 하나의 브랜치에서 개발되므로 **Epic 8·9가 머지된 뒤에 한 번에 착수**한다. breakdown은 미리 확정해 두되, Subticket 생성·SF-1 브랜치 생성은 게이트 이후에 한다.

---

## 세부 기능(SF) 개요

총 **3개 SF**로 분해한다. 순차 의존 관계이며 각 SF는 PR 하나로 리뷰 가능한 크기다.

```
SF-1 (기반 계층: 응답 DTO 2종, 에러 프레임워크, ProfitCalculator 계산 유틸리티)
  → SF-2 (단일 매도건 수익 계산 API  GET /sales/{saleId}/profit)
  → SF-3 (투자자별 전체 수익 목록 API  GET /investors/{investorId}/profits)
```

| SF | 내용 | 커버 AC | 선행 SF |
|---|---|---|---|
| SF-1 | 기반 계층 (ProfitDto, InvestorProfitDto, 에러 404 handler, ProfitCalculator) | 직접 커버하지 않음 — 인프라를 만든다. AC-013(읽기 전용)은 SF-2/SF-3 테스트로 검증 | 없음 (첫 번째 SF) |
| SF-2 | `GET /sales/{saleId}/profit` — 단일 매도건 수익 계산 (대응취득가액, 안분 비용, 세전/세후 순수익) | AC-001, AC-002, AC-003, AC-004, AC-005, AC-006, AC-007, AC-008, AC-009, AC-013 | SF-1 |
| SF-3 | `GET /investors/{investorId}/profits` — 투자자별 전체 수익 목록 + 합계 (N+1 방지 배치 조회) | AC-010, AC-011, AC-012, AC-008, AC-013 | SF-1, SF-2 |

---

## SF-1: 기반 계층 — 응답 DTO, 에러 프레임워크, ProfitCalculator

**범위**: 이 Epic의 "SF-1"이다. 다른 Epic의 SF-1(엔티티/Repository/Migration)과 달리 **테이블·엔티티를 만들지 않고**, 이후 SF가 의존할 공통 기반을 구축한다: ① 응답 DTO 2종(ProfitDto, InvestorProfitDto), ② 에러 처리 프레임워크(404 handler), ③ **ProfitCalculator** 계산 유틸리티(이 Epic의 핵심 로직). 기존 Repository는 건드리지 않는다 — architecture.md "모듈 소유권" 표의 신규 컴포넌트 4종만 생성한다.

**커버하는 AC**: 직접 커버하지 않고 인프라를 만든다. 소유권 검증 헬퍼가 AC-005의 로직을 담당하며, 실제 검증은 SF-2/SF-3의 엔드포인트 테스트로 확인한다. SF-1 자체의 테스트는 ProfitCalculator의 단위 테스트(AC-003, AC-004, AC-005, AC-006, AC-007 시나리오 각각)로 검증 가능하다.

**선행 SF**: 없음 (첫 번째 SF)

**산출물 파일 경로** (architecture.md "모듈 소유권" 참고. 신규 모듈 루트 패키지는 `com.vibeaiteam.profitcalculation` — cost-recording이 `com.vibeaiteam.costrecording`을 쓴 것과 동일한 명명 스타일):

- `dto/profit/ProfitDto.java` — 단일 매도건 수익 응답 DTO (id 없음). 필드: saleId(Long), saleDate(LocalDateTime), allocationId(Long), quantity(Long), saleAmount(Long), matchedAcquisitionAmount(Long), matchedCostAmount(Long), saleFee(Long), saleTax(Long), saleOtherCost(Long), preTaxProfit(Long), afterTaxProfit(Long). **모든 금액 필드는 Long (원 단위)** (D-P-005의 반올림 정책). null 허용 필드(fee/tax/otherCost)는 0으로 기본값 처리.
- `dto/profit/InvestorProfitDto.java` — 투자자별 수익 목록 응답 DTO. 필드: investorId(Long), sales(List<ProfitDto>), totalSaleAmount(Long), totalPreTaxProfit(Long), totalAfterTaxProfit(Long).
- `controller/profit/exception/ProfitNotFoundException.java` — 404 Not Found 예외. 존재하지 않는 Sale/Investor, CANCELLED 상태의 Sale을 404로 처리.
- `controller/profit/exception/ProfitExceptionHandler.java` — `@RestControllerAdvice`로 404 응답을 `{ "error": "NOT_FOUND", "message": "..." }` 형식으로 변환. 기존 모듈의 SubscriptionExceptionHandler 패턴을 따른다.
- `service/profit/calculator/ProfitCalculator.java` — ** stateless 유틸리티 클래스**. 메서드:
  - `calculateAcquisitionAmount(Long allocationPrice, Long fixedPrice, Long saleQuantity)`: 대응취득가액 계산 — `allocationPrice × saleQuantity` (allocationPrice null이면 fixedPrice으로 대체, 둘 다 null이면 0)
  - `calculateMatchedCost(Long totalActiveCost, Long saleQuantity, Long allocatedQuantity)`: 대응취득비용 계산 — `ROUND(totalActiveCost × saleQuantity / allocatedQuantity)` (방어적: allocatedQuantity=0이면 0)
  - `calculatePreTaxProfit(Long saleAmount, Long acquisitionAmount, Long matchedCost, Long fee, Long otherCost)`: 세전순수익 = saleAmount − acquisitionAmount − matchedCost − fee − otherCost (null-safe, null→0)
  - `calculateAfterTaxProfit(Long preTaxProfit, Long tax)`: 세후순수응 = preTaxProfit − tax (null→0)
  - `calculateAllocationRatio(Long saleQuantity, Long allocatedQuantity)`: 안분 비율 r = saleQuantity / allocatedQuantity (방어적 처리 포함)

**특이사항 — 계산 정밀도/반올림 (SF-1에서 결정 지점)**:
- **반올림은 `Math.round` (소수 첫째 자리에서 반올림 → Long)** — spec 엣지케이스 "반올림 정책" (D-P-005): `대응취득비용 = ROUND( Σ(ACTIVE Cost.amount) × r )`는 `Math.round(totalActiveCost * ratio)`로 구현. 이는 `long * double / long` 연산 후 `long`으로 반올림하는 것이며, **각 매도 건 단위로 독립 반올림**한다.
- **합계(totalPreTaxProfit/totalAfterTaxProfit)는 단순 합산** — SF-3에서 각 매도 건의 이미 반올림된 `Long` 값을 더하기만 하며, 추가 반올림은 없다. 잔차가 누적되어도 그대로 허용 (D-P-003, AC-007).
- **세금/수수료 공식은 그대로 사용** — 세율·공제 자동 계산 없음 (D-P-002). `Sale.tax`는 사용자 입력 실제 세액을 그대로 차감.

---

## SF-2: 단일 매도건 수익 계산 API (GET /sales/{saleId}/profit)

**범위**: 단일 매도건의 순수익을 계산하는 엔드포인트를 구현한다. Controller, Service, ProfitCalculator 호출 조합을 포함한다. 핵심은 **참조 체인 탐색** (Sale → Allocation → Subscription → Cost/IPO)과 **안분 비율 계산**이다.

**커버하는 AC**:
- AC-001: 단일 매도건 수익 계산 성공
- AC-002: 세금 차감 계산 (Sale.tax 반영)
- AC-003: 분할 매도 시 취득가액 안분 계산 (allocationPrice × Sale.quantity)
- AC-004: 분할 매도 시 Cost 안분 계산 (ROUND(Σ(ACTIVE Cost.amount) × r))
- AC-005: allocationPrice가 없는 경우 fixedPrice 대체 (IPO.fixedPrice)
- AC-006: Cost가 없는 경우 계산 (matchedCostAmount = 0)
- AC-007: 잔여 허용 (각 매도 건 독립 반올림, 재분배 안 함)
- AC-008: 취소(CANCELLED) 데이터 제외 (CANCELLED Sale → 404, CANCELLED Cost 제외)
- AC-009: 존재하지 않는 Sale 조회 실패 (404)
- AC-013: 읽기 전용·저장 없음 (조회 후 DB 변경 없음)

**선행 SF**: SF-1 (ProfitDto, ProfitNotFoundException, ProfitCalculator 필수)

**산출물 파일 경로**:
- `controller/profit/ProfitController.java` — `GET /sales/{saleId}/profit`. 클래스 주석 + 메서드 한글 주석 신설 (AGENTS.md 15장). 404 응답 처리.
- `service/profit/ProfitCalculationService.java` — `calculateForSale(Long saleId): ProfitDto`. `@Transactional(readOnly = true)`.
- `repository/sale/SaleRepository.java` — **기존 Epic 8 소유, 이 Epic에서 수정하지 않는다**. `findById(saleId)` 호출.
- `repository/allocation/AllocationRepository.java` — **기존 Epic 7 소유**.
- `repository/subscription/SubscriptionRepository.java` — **기존 Epic 6 소유**.
- `repository/cost/CostRepository.java` — **기존 Epic 9 소유**. `findBySubscriptionIdAndStatus(subscriptionId, "ACTIVE")` 호출.
- `repository/ipo/IpoRepository.java` — **기존 Epic 5 소유**.

**계산 흐름 (architecture.md 47~58행과 동일, SF-2에서 구현)**:
```
1. Sale 조회 — saleRepository.findById(saleId) → 없으면 404 (ACTIVE만, CANCELLED도 404)
2. Allocation 조회 — allocationRepository.findById(sale.getAllocationId())
3. Subscription 조회 — subscriptionRepository.findById(allocation.getSubscriptionId())
4. IPO 조건부 조회 — allocation.getAllocationPrice()가 null이면 ipoRepository.findById(subscription.getIpoId())
5. 대응취득가액 = ProfitCalculator.calculateAcquisitionAmount(allocationPrice, fixedPrice, saleQuantity)
6. ACTIVE Cost 조회 — costRepository.findBySubscriptionIdAndStatus(subscriptionId, "ACTIVE") → Σ(amount)
7. 대응취득비용 = ProfitCalculator.calculateMatchedCost(totalActiveCost, saleQuantity, allocatedQuantity)
8. 세전순수익 = ProfitCalculator.calculatePreTaxProfit(saleAmount, acquisitionAmount, matchedCost, fee, otherCost)
9. 세후순수익 = ProfitCalculator.calculateAfterTaxProfit(preTaxProfit, tax)
10. ProfitDto 구성 및 반환 (id 없음, 저장 없음)
```

**특이사항 — 가장 주의가 필요한 부분**:
- **CANCELLED Sale → 404**: spec "취소(CANCELLED) 데이터 제외" (AC-008). `findById`로 조회한 Sale의 status가 CANCELLED이면 404로 처리. 이는 "존재하지 않는 Sale"과 동일한 응답이다.
- **null-safe 금액 처리**: `Sale.fee`/`Sale.tax`/`Sale.otherCost`가 null이면 ProfitCalculator 내에서 0으로 처리. 응답에서는 항상 숫자(Long)로 반환.
- **allocationPrice null → IPO.fixedPrice 대체**: D-P-003의 대체 규칙. 둘 다 null이면 대응취득가액 = 0 (엣지케이스: 사용자 데이터 입력 누락, 자동 보정 안 함).
- **배정수량 0 방어**: `Allocation.allocatedQuantity`가 0인 경우 안분 비율 계산에서 나눗셈 오류 방지 (방어적 0 반환). 실제 데이터에서는 D-SL-003 검증으로 발생하지 않지만(매도 누적 ≤ 배정수량), 방어 코드는 필수.

---

## SF-3: 투자자별 전체 수익 목록 API (GET /investors/{investorId}/profits)

**범위**: 투자자의 모든 ACTIVE Sale에 대해 SF-2의 계산을 반복하고, 합계와 정렬을 적용하는 엔드포인트를 구현한다. 핵심은 **N+1 쿼리 방지**를 위한 배치 조회와 **합산 정책**이다.

**커버하는 AC**:
- AC-010: 투자자별 수익 목록 조회 성공 (여러 ACTIVE Sale, 합계 포함)
- AC-011: 존재하지 않는 Investor 조회 실패 (404)
- AC-012: 매도 건이 없는 투자자 조회 성공 (sales 빈 배열, 합계 0)
- AC-008: 취소(CANCELLED) 데이터 제외 (목록에서 CANCELLED Sale 제외)
- AC-013: 읽기 전용·저장 없음

**선행 SF**: SF-1 (ProfitDto, InvestorProfitDto, ProfitCalculator 필수), SF-2 (단일 계산 흐름 — SF-3는 SF-2의 로직을 재사용)

**산출물 파일 경로**:
- `controller/profit/ProfitController.java` — `GET /investors/{investorId}/profits` 추가 (메서드 한글 주석)
- `service/profit/ProfitCalculationService.java` — `calculateForInvestor(Long investorId): InvestorProfitDto` 추가
- `repository/sale/SaleRepository.java` — **기존 Epic 8 소유**. `findByInvestorIdAndStatus(investorId, "ACTIVE")` 또는 커스텀 JPQL 쿼리 필요 (Sale → Allocation → Subscription → Investor 3단계 JOIN).
- `repository/allocation/AllocationRepository.java`, `repository/subscription/SubscriptionRepository.java`, `repository/cost/CostRepository.java`, `repository/ipo/IpoRepository.java` — **기존 소유**. 배치 조회용 `findByIdIn()` 등 기존 메서드 활용.

**계산 흐름 (architecture.md 60~78행과 동일)**:
```
1. Investor 조회 — investorRepository.findById(investorId) → 존재하지 않으면 404
   (InvestorRepository는 Epic 2가 소유 — 기존 메서드 재사용)
2. ACTIVE Sale 목록 조회 — saleRepository.findByInvestorIdAndStatus(investorId, "ACTIVE")
   (Sale → Allocation → Subscription → Investor 3단계 JOIN)
3. 배치 조회 (N+1 방지) — 조회된 Sale에서 allocationId/subscriptionId 집합 추출:
   - allocationRepository.findByIdIn(allocationIds) — Map<allocationId, Allocation>
   - subscriptionRepository.findByIdIn(subscriptionIds) — Map<subscriptionId, Subscription>
   - costRepository.findBySubscriptionIdInAndStatus(subscriptionIds, "ACTIVE") — Map<subscriptionId, List<Cost>>
   - ipoRepository.findByIdIn(ipoIds) — Map<ipoId, IPO> (allocationPrice가 null인 경우만)
4. 각 Sale마다 SF-2의 단일 계산 흐름(ProfitCalculator 호출)을 적용
5. totalSaleAmount / totalPreTaxProfit / totalAfterTaxProfit 합산 (단순 합산, 추가 반올림 없음)
6. 정렬 — saleDate 오름차순, 동일 saleDate인 경우 id 오름차순
7. InvestorProfitDto 구성 및 반환 (저장 없음)
```

**특이사항 — 가장 주의가 필요한 부분**:
- **N+1 방지 (배치 조회)**: architecture.md가 "MVP에서는 방안 A(배치 조회)가 더 명확하다"고 결정한 대로, `findByIdIn()`으로 일괨 조회 후 Map 결합. JOIN FETCH 방안(B)도 검토 가능하지만, Cost가 다대다 관계라 JOIN FETCH로 포함하기 어려워 배치 조회 방식을 채택.
- **합산 정책**: `totalPreTaxProfit` / `totalAfterTaxProfit`는 **각 매도 건의 이미 반올림된 Long 값을 단순 합산**한다. 추가 반올림이나 잔여 재분배는 없다 (AC-007, D-P-003).
- **매도 건이 없는 경우**: 200 OK, `sales: []`, 합계 0으로 응답 (AC-012). SF-1에서 만든 InvestorProfitDto로 처리.
- **정렬**: `saleDate ASC, id ASC` (spec 1.2절). SF-2의 DB 정렬이 아니라 **서비스 계층에서 정렬**해야 한다(Sale → Allocation → Subscription 참조 체인에서 saleDate는 Sale 테이블에 있으므로 DB 정렬도 가능하지만, 3단계 JOIN 쿼리에서 정렬이 누락될 위험이 있어 서비스 레이어 추가 정렬로 방어).

---

## 계산 정밀도/반올림 관련 결정

| 항목 | 결정 | 적용 SF/위치 | 근거 |
|------|------|-------------|------|
| 금액 타입 | **원 단위 Long** | SF-1 (ProfitDto, InvestorProfitDto 모든 금액 필드) | spec 엣지케이스 "반올림 정책" (D-P-005) |
| 안분 비용 반올림 | **`Math.round` (소수 첫째 자리 반올림 → Long)** | SF-1 `ProfitCalculator.calculateMatchedCost` 내 | spec 공식: `ROUND( Σ(ACTIVE Cost.amount) × r )` (D-P-003, AC-004) |
| 반올림 단위 | **개별 매도 건** | SF-2 (단일 계산), SF-3 (각 Sale 반복) | "각 매도 건 단위로 독립 반올림" (D-P-003, AC-007) |
| 잔여(잔차) 처리 | **허용 (재분배/보정 안 함)** | SF-2, SF-3 | "분할 매도 누적 시...잔여는 그대로 허용" (D-P-003, AC-007) |
| 합계 계산 | **단순 합산 (추가 반올림 없음)** | SF-3 `calculateForInvestor` | "각 매도 건 결과를 단순 합산" (spec 1.2, AC-010) |
| 세금 처리 | **Sale.tax를 그대로 차감** | SF-1 `ProfitCalculator.calculateAfterTaxProfit` | "세율·공제 자동 계산 없음" (D-P-002, AC-002) |
| null 금액 처리 | **null → 0** | SF-1 `ProfitCalculator` (null-safe) | "null이면 0으로 간주" (spec 엣지케이스) |
| 배정수량 0 방어 | **0 반환 (나눗셈 오류 방지)** | SF-1 `ProfitCalculator.calculateAllocationRatio` | "방어적 처리" (spec 엣지케이스, D-SL-003가 실제 데이터에서 방지하지만 방어 코드 필수) |
| 실현/미실현 구분 | **실현 손익만 (매도 기반)** | SF-2, SF-3 (전부 Sale 기반) | "단일 매도 건 또는 횐자 단위의 실현 손익" (spec 개요). 미실현 손익(mark-to-market)은 D-P-004에 의해 범위 밖 |

---

## 의존성 요약

```
[게이트] Epic 8 (sale) + Epic 9 (cost) backend 머지 완료
   ↓
SF-1 (기반 계층: ProfitDto, InvestorProfitDto, 404 handler, ProfitCalculator)
   └→ SF-2 (GET /sales/{saleId}/profit) ── 참조 체인 탐색 (Sale→Allocation→Subscription→Cost/IPO), 안분 비율 계산 ★ 가장 주의
   └→ SF-3 (GET /investors/{investorId}/profits) ── N+1 방지 배치 조회, 합계 단순 합산, 정렬

보조 의존성 (Epic 5/6/7/9의 기존 Repository 메서드 호출, 수정 불필요):
- Epic 5 (IpoRepository) — IPO.fixedPrice 대체 단가
- Epic 6 (SubscriptionRepository) — 배정→청약→투자자 참조 체인
- Epic 7 (AllocationRepository) — 배정금액/배정수량
- Epic 8 (SaleRepository) — 매도 금액/수량/수수료/세금
- Epic 9 (CostRepository) — 취득 비용 (ACTIVE 상태만)
```

**가장 주의가 필요한 부분**:
1. **SF-2의 안분 비율 계산과 반올림** — `대응취득비용 = ROUND(Σ(ACTIVE Cost) × Sale.quantity / Allocation.allocatedQuantity)`는 `Math.round`로 구현해야 정확한 반올림 동작을 얻는다. 잘못하면 행사할 위험이 있고, 각 매도 건이 독립 반올림되므로 잔여가 발생해도 재분배하지 않는다는 정책을 명확히 해야 한다 (AC-004, AC-007).
2. **SF-3의 N+1 방지** — 3단계 참조 체인(Sale→Allocation→Subscription→Investor)에서 전체 조회 시 N+1이 발생하지 않도록 `findByIdIn()` 배치 조회로 최적화해야 한다. JOIN FETCH 대안도 검토 가능하지만 Cost의 다대다 관계로 인해 복잡해 배치 조회가 명확하다 (architecture.md "N+1 쿼리 문제 방지 방향").
3. **CANCELLED 데이터 필터링** — CANCELLED Sale은 404 처리, CANCELLED Cost는 합산에서 제외, CANCELLED Allocation/Subscription은 계산 시 참조하지 않는다. 모든 조회 쿼리에 `status = "ACTIVE"` 필터가 누락되지 않도록 검증 필요 (AC-008).

---

## 참고

- **문서 의존성**: `docs/spec/profit-calculation.md`, `docs/architecture/profit-calculation.md`
- **금지사항**: 코드/엔티티/DB 스키마 작성 금지 — 이 문서는 "무엇을 어떤 순서로 만들지"를 정하는 계획 문서. 코드 작성은 각 SF가 착수(구현 게이트 통과)할 때 별도로 진행된다
- **금지사항**: Flyway 마이그레이션 작성 금지 — 이 Epic은 새 DB 테이블이 없으므로 필요 없다 (architecture.md 비고)
- **금지사항**: 세금/수수료 공식 조사 없이 임의 확정 금지 — spec/architecture에 이미 결정된 내용(D-P-002: `Sale.tax`를 그대로 사용, 세율 자동 계산 없음)만 반영한다
- **모듈 소유권 경계**: ProfitController/ProfitDto/ProfitCalculator/ProfitCalculationService는 이 Epic이 소유. Sale/Allocation/Subscription/Cost/Ipo Repository는 각각 Epic 8/7/6/9/5가 소유하므로 **기존 Repository에 대한 read 메서드 추가가 필요한 경우, 해당 Epic의 워커와 협의**해야 한다 (investment-query-api.md가 Epic 7/8 Repository에 배치 메서드를 기여한 것과 동일한 패턴 — 다만 이 Epic은 읽기 전용 조합이므로 가능한 한 기존 메서드만으로 해결할 예정)
- **읽기 전용 보장**: GET 엔드포인트만 제공, `@Transactional(readOnly = true)`, 락 없음, Flyway 없음, 캐시 없음 (D-P-001, D-P-006)
- **검증 명령**: `./gradlew test checkstyleMain checkstyleTest` — 완료 조건(Definition of Done)에 포함 (AGENTS.md 15장)
- **참고 선례**: `docs/breakdown/investment-query-api.md` (Epic 11) — 같은 "읽기 전용 집계 API" 구조, 3 SF 분해 패턴, N+1 방지 배치 조회 전략
