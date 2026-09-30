# 아키텍처 설계: 대시보드 집계 API (dashboard-aggregation-api, Epic 12)

## 아키텍처 개요 (읽기 전용 집계 서비스)

이 Epic은 **읽기 전용 집계 서비스**이다. Investment Query API(Epic 11)와 동일하게 DB에 새로운 테이블·엔티티를 만들지 않으며, 기존 Sale/Allocation/Subscription/Investor/SecuritiesAccount/IPO Repository를 조합해 파생 집계값을 on-demand로 계산한다(D-D-002). 모든 계산은 조회 시점에 수행하고 결과를 저장·캐싱하지 않는다.

레이어 구조는 Profit Calculation(Epic 10) / Investment Query(Epic 11) 아키텍처와 동일하게 따른다:

```
controller → service → domain → repository
```

복합해서 집계 연산은 Service 레벨에서 수행하고, domain 계층에는 이 Epic 전용 엔티티를 두지 않는다. 기존 도메인 엔티티를 읽기 전용으로 참조하며, 계산 규칙은 Epic 10의 `ProfitCalculator`에 위임한다(D-D-006).

### 구성 컴포넌트

- `controller.dashboard`: `DashboardController` — HTTP GET 요청 처리, `unit` 파라미터 검증
- `service.dashboard`: `DashboardAggregationService` — 집계 로직 오케스트레이션(그룹화·합산·수익률 계산)
- `dto.dashboard`: `SummaryDto`, `InvestorAggregationDto`, `SecuritiesFirmAggregationDto`, `PeriodAggregationDto` — 응답 DTO (중첩/기본형 구조 단순)
- `domain`: dashboard 전용 도메인 엔티티 없음
- `repository`: dashboard 전용 Repository 없음 — 기존 `SaleRepository`, `AllocationRepository`, `SubscriptionRepository`, `InvestorRepository`, `SecuritiesAccountRepository`, `IpoRepository` 조합 사용
- **Epic 10 `ProfitCalculator` 재사용**: 각 Sale의 세전/세후순수익 산출을 위임 (신규 수익 공식 없음, D-D-006)

### 참조 엔티티 (기존, 읽기 전용)

| 엔티티 | 소유 Epic | 이 Epic에서 사용하는 필드 |
|--------|----------|------------------------|
| Sale | Epic 8 | id, allocationId, quantity, amount, fee, tax, otherCost, status, saleDate |
| Allocation | Epic 7 | id, subscriptionId, allocatedQuantity, allocationPrice, allocationStatus |
| Subscription | Epic 6 | id, investorId, ipoId |
| Investor | Epic 2 | id, name (소속 SecuritiesFirm 연결 기준) |
| SecuritiesAccount | Epic 4 | id, investorId, securitiesFirmId |
| SecuritiesFirm | Epic 4 | id, name |
| IPO | Epic 5 | id, fixedPrice (allocationPrice null 시 대체 단가) |
| Cost | Epic 9 | id, subscriptionId, amount, status (안분 취득비용 계산용) |

### DTO 구조 (단순 플랫 구조)

```
SummaryDto
├── totalInvestmentAmount (Long)
├── totalPreTaxProfit (Long)
├── totalAfterTaxProfit (Long)
├── totalProfitRate (Double)
└── participationCount (Integer)

InvestorAggregationDto = SummaryDto 필드 + investorId (Long), investorName (String)
SecuritiesFirmAggregationDto = SummaryDto 필드 + securitiesFirmId (Long), securitiesFirmName (String)
PeriodAggregationDto = SummaryDto 필드 + period (String, "YYYY-MM" | "YYYY")
```

공통 집계 5필드가 4개 DTO에 걸쳐 반복되므로, 필요 시 공통 보조 타입(예: 내부 집계용 `AggregationValue` 기록 구조)을 두어 중복을 흡수할 수 있다. 응답 DTO 자체는 spec의 JSON 계약을 그대로 유지한다(AC-011).

---

## 집계 서비스 설계

### DashboardAggregationService

읽기 전용 서비스. 트랜잭션 경계는 `@Transactional(readOnly = true)`로 선언한다. 락은 도입하지 않는다(실패 금지 항목).

**공통 데이터 준비 흐름 (핵심 설계 포인트):**

집계 엔드포인트 4종(전체/투자자별/증권사별/기간별)은 모두 동일한 전처리를 거친다:

```
Step 1: 현재 계정 소유 ACTIVE Sale 조회 — saleRepository.findAllByStatusAndInvestorAccountId("ACTIVE", currentAccountId) (SecurityUtils.getCurrentAccountId() 기준, Master 수정)
Step 2: Sale→Allocation 연관 데이터를 배치 조회로 구성 (N+1 방지)
Step 3: 각 Sale에 대해 Epic 10 ProfitCalculator 호출 → 세전/세후순수익 산출
Step 4: 집계 대상 Sale별 안분 투자금(`allocationPrice × Sale.quantity`) 합산(총투자금) 계산
Step 5: 그룹핑(누락 시 "단일 그룹") → 그룹별 집계값 산출 → DTO 구성
```

위 전처리는 엔드포인트 간 중복이 크므로 **private 헬퍼 메서드로 추출**한다. 예:

```
private List<CalculatedSale> loadCalculatedSales()
  // ACTIVE Sale 전부 조회 + 배치로 연관 데이터 로드 + ProfitCalculator 적용
  // 반환: Sale + 세전순수익 + 세후순수익 + investKey(투자자/증권사/기간)가 포함된 계산용 기록 목록

private AggregationValue aggregate(List<CalculatedSale> sales, Function<CalculatedSale, Long> investorKey)
  // 그룹 내 집계 5필드 계산 (안분 투자금 합, 수익 합, 수익률, 참여건수=서로 다른 배정 수)
```

`loadCalculatedSales()`가 전체 ACTIVE Sale을 한 번만 로드하므로, 4개 메서드가 각각 중복 조회를 하지 않는다.

#### 1. `DashboardAggregationService.getSummary(): SummaryDto`

**호출 Repository:**
1. `saleRepository.findAllByStatusAndInvestorAccountId("ACTIVE", currentAccountId)` — 현재 계정 소유 ACTIVE Sale 조회 (Master 수정: 계정 간 데이터 격리)
2. 배치 조회 — `allocationRepository.findBySubscriptionIdIn(subscriptionIds)`, `subscriptionRepository.findByInvestorIdIn(investorIds)` 등 (설명: 전체 Sale→Allocation 참조와 소속 정보를 IN 쿼리로 일괄 로드)
3. `ProfitCalculator` — 공통 전처리에서 일괄 호출

**집계 절차:**
- `loadCalculatedSales()`로 계산 목록 구성 (전체 Sale이 단일 그룹)
- `totalInvestmentAmount` = Σ(집계 대상 Sale의 `allocationPrice × Sale.quantity`, 배정금액의 매도 수량 안분) (D-D-001, 신규 대체 단가 룰은 ProfitCalculator에 위임)
- `totalPreTaxProfit` = Σ(각 Sale의 세전순수익), `totalAfterTaxProfit` = Σ(세후순수익) (D-D-003, 잔차 재분배 없음)
- `totalProfitRate` = `totalAfterTaxProfit / totalInvestmentAmount × 100`, 총투자금 0이면 `0.0` (AC-007)
- `participationCount` = ACTIVE Sale 건수
- `SummaryDto` 반환. 데이터가 없으면 모든 필드 0인 빈 집계 반환 (AC-006)

#### 2. `DashboardAggregationService.getByInvestor(): List<InvestorAggregationDto>`

**호출 Repository:** `loadCalculatedSales()`의 공통 조회 + `investorRepository.findByInvestorIdIn(ids)` (ID→이름 매핑용 배치 조회)

**집계 절차:**
- `loadCalculatedSales()` 결과를 `Sale → Investor` 기준으로 그룹핑 (`InvestorAggregationDto.investorId` 키)
- 그룹별로 5필드 집계값 산출 (공통 `aggregate` 헬퍼 재사용)
- 그룹마다 investorId 오름차순 정렬 후 반환 (AC-002)
- 데이터 없으면 빈 배열 반환

#### 3. `DashboardAggregationService.getBySecuritiesFirm(): List<SecuritiesFirmAggregationDto>`

**호출 Repository:** `loadCalculatedSales()`의 공통 조회 + `securitiesFirmRepository.findBySecuritiesFirmIdIn(ids)` (ID→이름 매핑용 배치 조회)

**집계 절차:**
- `loadCalculatedSales()` 결과를 `Sale → Investor → SecuritiesFirm` 기준으로 그룹핑 (Investor가 소속된 증권사 기준, `securitiesFirmId` 키)
- 그룹별 5필드 집계값 산출
- securitiesFirmId 오름차순 정렬 후 반환 (AC-003)
- 데이터 없으면 빈 배열 반환

**주의:** 증권사 귀속은 Investor의 소속(SecuritiesAccount.securitiesFirmId)을 기준으로 한다. Investor ↔ SecuritiesFirm은 1:1(현재 범위)이므로 그룹 키에 모호성이 없다.

#### 4. `DashboardAggregationService.getByPeriod(String unit): List<PeriodAggregationDto>`

**호출 Repository:** `loadCalculatedSales()`의 공통 조회만 사용. 추가 매핑 조회 불필요.

**집계 절차:**
- `unit` 검증: Service에서 `month`/`year` 외 값과 null을 확인 → 검증 실패 시 예외를 던지고 Controller가 400으로 변환 (AC-008)
- `loadCalculatedSales()` 결과를 `Sale.saleDate` 기준으로 그룹핑 (D-D-005):
  - `unit=month`: `saleDate` → `"YYYY-MM"` 문자열
  - `unit=year`: `saleDate` → `"YYYY"` 문자열
- 그룹별 5필드 집계값 산출
- period 오름차순 정렬(문자열 정렬로 연·월 자연정렬) 후 반환 (AC-004, AC-005)
- 유효 기간만 반환 (데이터 없는 기간은 포함하지 않음)

### ProfitCalculator 재사용 방식 (구체적)

Epic 10의 `ProfitCalculator`는 매도 건 하나에 대해 아래 항목을 제공하는 서비스/계산기로 가정한다. 이 Epic은 이 **계산 결과만 그룹핑·합산에 사용**하고, 공식·안분·잔차·null·CANCELLED 처리 규칙을 전부 Epic 10에 위임한다(D-D-006).

| Epic 10 항목 | 이 Epic의 용도 |
|---|---|
| 매도 건별 세전순수익 | `totalPreTaxProfit` 합산 원천 |
| 매도 건별 세후순수익 | `totalAfterTaxProfit` 합산 원천 |
| 대응취득가액(안분) 계산 | 총투자금·수익률 기반에 간접 기여 (ProfitCalculator 내부) |
| 대응취득비용(안분) 계산 | 세전순수익 기반 (ProfitCalculator 내부) |
| Sale.fee/tax/otherCost null→0 처리 | 공통 전처리에서 위임 |
| CANCELLED Sale 제외 | `findAllByStatusAndInvestorAccountId("ACTIVE", currentAccountId)` + ProfitCalculator 일관 규칙 |

- `loadCalculatedSales()`는 각 Sale에 대해 `profitCalculator.calculate(...)`를 호출해 세전/세후순수익을 얻고, 계산 결과와 함께 Sale을 보관한다.
- 총투자금은 spec(D-D-001)에 따라 배정금액을 매도 수량 비율로 안분한 몫(`allocationPrice × Sale.quantity`) 기준이므로, ProfitCalculator 출력에 총투자금이 없으면 Service에서 직접 합산한다(단가 대체 규칙은 Epic 10의 대응취득가액 룰을 따르되, 배정금액 자체는 Allocation.allocationPrice 기준). 단, **신규 공식 설계 금지** 원칙 위에 누적.

---

## 집계 전략 (애플리케이션 레벨 그룹화 vs DB 레벨 GROUP BY)

### 방식 1: DB 레벨 GROUP BY 쿼리

SQL에서 `GROUP BY`로 그룹 키(투자자/증권사/기간)별 `SUM`을 직접 산출하는 방식.

- **장점**:
  - DB에서 필요한 행만 집계·전송 → 네트워크·메모리 부담이 줄어든다
  - 인덱스 및 DB 집계 최적화 활용 가능
  - 데이터량이 커져도 쿼리 하나로 처리되어 확장에 유리
- **단점**:
  - 이 프로젝트의 집계 대상은 **단순 SUM이 아니라 Sale마다 별도로 계산해야 하는 파생값**이다. Epic 10의 세전순수익 = `Sale.amount − 대응취득가액 − 대응취득비용 − fee − otherCost`에서 대응취득가액·대응취득비용은 **Sale마다 안분 비율(수량 비율)로 개별 계산**된다. 이를 SQL `SUM()`으로 옮기면 (a) 안분 로직을 SQL로 재구현해야 하고(공식 중복 → D-D-006 위반 위험), (b) `ProfitCalculator`의 null 처리·잔차 정책·CANCELLED 규칙을 SQL에 맞춰 재정의해야 한다(유지보수 두 지점화). (c) 총투자금(Allocation 배정금액 합)은 또 다른 키/조인으로 계산되어 GROUP BY 지시문이 매우 복잡해진다.
  - 실제로는 단순 SUM이 아니므로 GROUP BY의 하드코딩 공식이 계산 로직과 이중으로 존재하게 된다.
  - 마이그레이션·인덱스 설계는 가능하지만 이 Epic은 애플리케이션 계층이 캐시되는 파생 로직을 우선순위로 둔다.

### 방식 2: 애플리케이션 레벨 그룹화 (선택)

전체 ACTIVE Sale을 조회한 뒤, Service가 메모리에서 그룹핑·합산하는 방식.

- **장점**:
  - Epic 10 `ProfitCalculator`를 **그대로 재사용** — 안분 계산·null 처리·잔차 정책이 한 곳(만)에 존재 (D-D-006 완전 준수)
  - 그룹 단위(전체/투자자/증권사/기간)를 바꿔도 공통 데이터 준비 단계는 동일 — 4개 엔드포인트 간 로직 재사용이 쉬움
  - 읽기 전용/비캐시 원칙과 자연스럽게 호환, 동적 그룹핑에 유연
  - DB 쿼리 복잡도·인덱스 설계 부담 없음
- **단점**:
  - 전체 ACTIVE Sale + 연관 Allocation/Cost를 한 번에 메모리에 로드
  - Sale 수가 매우 커지면(수십만 건 이상) 메모리·GC 부담 증가
  - 대량 데이터에서 배치 조회 N+1 방지가 필수 선결 조건

### 권장안 (이 프로젝트 규모 기준)

> **애플리케이션 레벨 그룹화(방식 2)를 채택한다.**

근거:
1. 이 Epic의 핵심 요구는 **새 수익 계산 공식을 만들지 않고 Epic 10 로직을 그대로 재사용**하는 것(D-D-006)이다. 안분 계산(Cost 안분 등)이 단순 SQL SUM으로는 옮겨지지 않으므로, SQL GROUP BY는 온전한 집계를 위해 결국 애플리케이션 로직의 복제본이 필요하게 된다.
2. MVP·개인 기록용 도구(Sale 규모가 수만 건 이하)에서는 애플리케이션 레벨 그룹화의 메모리 부담이 실질 문제가 되지 않는다. 읽기 전용 집계이므로 동시 호출 부하도 낮다(개인용).
3. on-demand 재계산(D-D-002) 원칙과 결합하면 캐시 없이 매번 전체 재계산하므로, DB GROUP BY의 성능 이점도 조회 빈도가 낮은 이 앱에서는 미미하다.
4. 데이터가 대량으로 커지는 미래 전환점이 온다면, 그 시점에 배치 조회 최적화 또는 GROUP BY 쿼리(with 애플리케이션 안분 로직 유지)를 재평가한다. 이는 설계 변경이 아니라 구현 최적화로 수용 가능하다.

### N+1 방지 (투자 Query와 동일 패턴)

`loadCalculatedSales()` 내부에서 전체 ACTIVE Sale 조회 후 다음을 배치 조회로 처리한다(공통 헬퍼, 재조회 금지):

| 로드 대상 | Repository 메서드 | 용도 |
|---|---|---|
| 현재 계정 소유 ACTIVE Sale | `saleRepository.findAllByStatusAndInvestorAccountId("ACTIVE", currentAccountId)` | 집계 대상 (Master 수정: 계정 간 데이터 격리) |
| Allocation 배치 | `allocationRepository.findBySubscriptionIdIn(...)` | 배정금액·취득가액 계산 원천 |
| Cost 배치 | `costRepository.findBySubscriptionIdInAndStatus(..., "ACTIVE")` | 안분 취득비용 계산 원천 (ProfitCalculator 위임) |
| Investor 배치 | `investorRepository.findByInvestorIdIn(...)` | 투자자 그룹핑·이름 매핑 |
| SecuritiesFirm 배치 | `securitiesFirmRepository.findByIdIn(...)` | 증권사 그룹핑·이름 매핑 |

---

## API 설계 (엔드포인트/요청/응답/에러)

모두 읽기 전용 GET이며, 계산 결과를 저장·캐싱하지 않는다(D-D-002). spec(2장)의 JSON 계약을 아키텍처 관점에서 재정리한다.

### 엔드포인트 요약

| 메서드 | 경로 | 쿼리 파라미터 | 성공 | 실패 | 정렬 |
|---|---|---|---|---|---|
| GET | `/dashboard/summary` | 없음 | 200 — `SummaryDto` | 없음(항상 200, 빈 값이면 0 집계) | — |
| GET | `/dashboard/by-investor` | 없음 | 200 — `List<InvestorAggregationDto>` | 없음(빈 배열) | investorId ASC |
| GET | `/dashboard/by-securities-firm` | 없음 | 200 — `List<SecuritiesFirmAggregationDto>` | 없음(빈 배열) | securitiesFirmId ASC |
| GET | `/dashboard/by-period` | `unit` (필수) | 200 — `List<PeriodAggregationDto>` | 400 — unit 누락/비허용값 | period ASC |

### 요청 파라미터

- `unit(String, 필수)`: `month` 또는 `year`만 허용. 그 외 값·누락 시 400 Bad Request (AC-008)

### 응답 구조 (spec 2장 복원)

- **`SummaryDto`**: `totalInvestmentAmount(Long)`, `totalPreTaxProfit(Long)`, `totalAfterTaxProfit(Long)`, `totalProfitRate(Double)`, `participationCount(Integer)`
- **`InvestorAggregationDto`**: 위 필드 + `investorId(Long)`, `investorName(String)`
- **`SecuritiesFirmAggregationDto`**: 위 필드 + `securitiesFirmId(Long)`, `securitiesFirmName(String)`
- **`PeriodAggregationDto`**: 위 필드 + `period(String, "YYYY-MM" | "YYYY")`

예시 JSON은 spec 2.1~2.4에 정의된 것과 동일하므로 여기서 재현하지 않는다(단일 진실 원천은 spec).

### 에러 처리

| 상황 | HTTP | 응답 |
|---|---|---|
| `unit` 누락 / `month`·`year` 외 값 | 400 Bad Request | `{ "error": "BAD_REQUEST", "message": "unit은 month 또는 year여야 합니다." }` |
| 조회 성공(데이터 없음, by-* 계열) | 200 OK | 빈 배열 `[]` |
| 조회 성공(데이터 없음, summary) | 200 OK | 전 필드 0인 `SummaryDto` |

- 데이터 무존재가 예외가 아니라 "빈 집계"를 반환하는 정상 응답이다(AC-006). 별도의 404는 존재하지 않는다.
- 에러 응답 형식은 Investment Query(Epic 11)의 `{ error, message }` 계약을 따른다.
- **인증/소유권 검증(Master 수정)**: 이 프로젝트는 Epic 1부터 계정별 인증·인가를 갖춘 멀티 계정 서비스이며, 한 Account가 여러 Investor(가족 등)를 소유할 수 있다(Epic 2). 따라서 이 Epic도 **다른 모든 Epic과 동일하게 로그인한 Account가 소유한 Investor의 데이터만 집계 대상으로 삼아야 한다** — "단일 사용자 가정"은 잘못된 전제이며 그대로 두면 계정 간 재무 데이터가 섞이거나 다른 계정의 데이터가 노출되는 보안 결함이 된다. `loadCalculatedSales()`는 `SecurityUtils.getCurrentAccountId()`로 얻은 Account ID를 기준으로 `saleRepository.findAllByStatusAndInvestorAccountId("ACTIVE", currentAccountId)`(Sale → Allocation → Subscription → Investor 경유 필터)처럼 **처음부터 현재 계정 소유 데이터만 조회**한다(Epic 11 D-Q-006과 동일 원칙). 전체 계정을 대상으로 하는 별도 관리자용 집계는 이 Epic 범위 밖이다.

---

## 모듈 소유권

| 컴포넌트 | 패키지 | 소유 |
|---------|--------|------|
| DashboardController | `controller.dashboard` | 이 Epic |
| DashboardAggregationService | `service.dashboard` | 이 Epic |
| SummaryDto | `dto.dashboard` | 이 Epic |
| InvestorAggregationDto | `dto.dashboard` | 이 Epic |
| SecuritiesFirmAggregationDto | `dto.dashboard` | 이 Epic |
| PeriodAggregationDto | `dto.dashboard` | 이 Epic |
| (내부 계산용 기록/집계 헬퍼) | `service.dashboard` (private) | 이 Epic |
| SaleRepository | `repository.sale` | Epic 8 (기존) |
| AllocationRepository | `repository.allocation` | Epic 7 (기존) |
| SubscriptionRepository | `repository.subscription` | Epic 6 (기존) |
| InvestorRepository | `repository.investor` | Epic 2 (기존) |
| SecuritiesAccountRepository | `repository.securitiesaccount` | Epic 4 (기존) |
| SecuritiesFirmRepository | `repository.securitiesfirm` | Epic 4 (기존) |
| CostRepository | `repository.cost` | Epic 9 (기존) |
| IpoRepository | `repository.ipo` | Epic 5 (기존) |
| ProfitCalculator | Epic 10 (기존, 재사용) | Epic 10 |
| SecurityUtils | `util.security` | Epic 2 (기존, 재사용) |

---

## 의존성

- **신규 라이브러리 불필요**: 캐시, 락, 유틸리티 등 추가 의존성 없음. 기존 `spring-boot-starter-data-jpa`만 사용한다.
- **Epic 10 `ProfitCalculator` 재사용(핵심 의존성)**: 세전/세후순수익 계산을 전부 위임. 이 Epic은 자신의 공식을 유지하지 않는다(D-D-006).
- **기존 Repository 조합**: Sale/Allocation/Subscription/Investor/SecuritiesAccount/SecuritiesFirm/Cost/Ipo Repository — 이미 구현됨.
- **Lombok**: 기존 프로젝트에서 사용하는 경우 DTO에 동일 적용 가능.
- **배치 조회 메서드 추가**: `findAllByStatusAndInvestorAccountId`, `findBySubscriptionIdIn`, `findByInvestorIdIn`, `findByIdIn`, `findBySubscriptionIdInAndStatus` 같은 IN/상태/계정 기반 메서드가 기존 Repository에 없으면 **기존 Repository에 조회 메서드만 추가**한다(신규 Repository/테이블 아님). 이는 도메인·DB 변경이 아닌 조회 인터페이스 확장이다.
- **SecurityUtils 재사용**: Epic 2에서 정의된 `util.security.SecurityUtils.getCurrentAccountId()`를 그대로 사용해 현재 계정을 식별한다(Epic 11 D-Q-006과 동일 패턴, 신규 유틸리티 불필요).

---

## 리스크

1. **전체 조회 메모리 부하**: 모든 엔드포인트가 전체 ACTIVE Sale + 연관 데이터를 메모리에 로드한다. 판매 기록 수가 몇만 건까지는 문제없으나, 데이터가 크게 늘어나면 배치 조회 최적화 또는 DB GROUP BY 전환을 재평가한다. MVP·개인용 규모에서는 허용 가능.
2. **ProfitCalculator 의존성(계약 안정성)**: 이 Epic의 정확성은 Epic 10 계산기가 전부 좌우한다. Epic 10에서 메서드 시그니처·반환값이 바뀌면 이 Epic의 `loadCalculatedSales()`가 깨진다. Epic 10과의 계약(입력 Sale, 출력 세전/세후순수익)을 명확히 고정하고, 단위 테스트 시 Epic 10 계산 경로와 동일 입력을 사용한다.
3. **총투자금 기준 정의 보존**: spec D-D-001(배정금액 단일 기준)과 ProfitCalculator의 대응취득가액 규칙(allocationPrice null → fixedPrice 대체)이 섞여 혼동 가능. 총투자금은 배정금액의 매도 수량 안분 기준, 매도 건별 손익은 ProfitCalculator 기준임을 DTO·테스트에서 명시적으로 고정한다.
4. **증권사/투자자 그룹핑 키 불일치**: 증권사 귀속은 Investor의 소속(SecuritiesAccount.securitiesFirmId) 기준. Investor와 SecuritiesAccount가 1:1이 아니게 되는 미래 변경이 생기면 그룹 키 정의가 모호해질 수 있어, 그 시점에 귀속 규칙을 재확정해야 한다.
5. **배치 조회 준비 누락**: 그룹핑·이름 매핑을 배치로 처리하지 않고 Sale마다 개별 조회하면 N+1 문제가 발생한다. `loadCalculatedSales()` 공통 헬퍼가 배치 조회를 강제하도록 구현하고 QA에서 쿼리 수를 확인한다.
6. **읽기 전용 보장(AC-010)**: GET만 제공하고 계산 결과를 저장·캐시하지 않는다. 향후 엔드포인트 확장 시 읽기 전용 원칙을 유지하도록 문서에 명시한다. 동시성 제어·락은 도입하지 않는다.
7. **`unit` 파라미터 검증 위치**: 검증 누락 시 400이 아닌 기본 처리(예: year 취급)로 흘러갈 수 있다. Controller에서 명시적으로 month/year만 허용하고, 그 외는 400을 던지도록 한다(AC-008).

---

## 비고

- **Flyway 마이그레이션**: 이 Epic은 새 DB 테이블을 만들지 않으므로 Flyway 마이그레이션 섹션이 없다.
- **도메인 엔티티**: dashboard 전용 엔티티가 없으며 domain 계층에 새 클래스를 추가하지 않는다.
- **Repository**: dashboard 전용 Repository가 없다. 기존 Repository의 조회 메서드만 확장한다.
- **집계 방식**: 애플리케이션 레벨 그룹화(방식 2) 채택. Epic 10 ProfitCalculator의 안분 계산·null 처리·잔차 정책·CANCELLED 제외 규칙을 그대로 재사용한다.
- **락 설계 금지**: 읽기 전용 집계이므로 락을 도입하지 않는다.