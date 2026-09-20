# 대시보드 집계 API 명세 (dashboard-aggregation-api, Epic 12)

## 1. 개요 및 용어 정의

### 1.1 개요

본 Epic은 투자자의 실현수익을 기반으로 한 대시보드 집계 API를 제공한다. 모든 집계값은 **조회 시점에 on-demand 계산**되며 DB에 저장하지 않는다(파생값 원칙, D-D-002). 집계 계산은 **Epic 10의 수익 계산 로직(ProfitCalculator)을 그대로 재사용**하며, 새로운 수익 공식을 만들지 않는다(D-D-006).

**계정 소유권 범위(Master 수정)**: 이 프로젝트는 Epic 1부터 계정별 인증·인가를 갖춘 멀티 계정 서비스이며, 모든 대시보드 엔드포인트는 **로그인한 Account가 소유한 Investor의 데이터만** 집계 대상으로 삼는다(Epic 11 D-Q-006과 동일 원칙). 전체 계정을 대상으로 하는 관리자용 집계는 이 Epic 범위 밖이다.

### 1.2 핵심 결정 요약 (D-D-001~007)

| 결정 | 내용 |
|------|------|
| D-D-001 | 총투자금은 `배정금액(allocatedQuantity × allocationPrice)` 단일 기준 |
| D-D-002 | 집계는 저장하지 않는 파생값. 별도 이력/메타데이터 테이블 불필요 |
| D-D-003 | 안분 잔차 재분배 없음. 각 매도 건 값을 그대로 합산 |
| D-D-004 | 미매도 주식/미실현손익은 범위 밖. 실현수익만 집계 |
| D-D-005 | 기간별 귀속은 `Sale.saleDate` 기준 |
| D-D-006 | Epic 10의 계산 로직 재사용. 새로운 수익 공식 없음 |
| D-D-007 | 집계 범위: 전체/투자자별/증권사별/월별/연도별 5종만 |

### 1.3 용어 정의

- **집계(Aggregation)**: 여러 Sale/Allocation/Cost 레코드를 그룹화해 합산한 파생값. 저장하지 않으며 on-demand 계산(D-D-002).
- **총투자금**: 집계 대상 Allocation의 `allocatedQuantity × allocationPrice` 합계 (D-D-001).
- **총세전수익**: 집계 대상 모든 Sale의 세전순수익 합산 (Epic 10 공식 재사용).
- **총세후수익**: 집계 대상 모든 Sale의 세후순수익 합산 (Epic 10 공식 재사용).
- **총수익률**: `총세후수익 / 총투자금 × 100` (단위: %, 소수점 둘째 자리까지).
- **참여건수**: 집계 대상 ACTIVE Sale 건수.
- **기간별 귀속**: `Sale.saleDate`의 연-월/연 단위로 그룹화 (D-D-005).
- **집계 범위**: 전체, 투자자별, 증권사별, 월별, 연도별 5종만 제공 (D-D-007). 계좌별/공모주별은 제외.
- **ID**: 모든 엔티티 ID는 `Long` 타입 사용.

### 1.4 Epic 10 계산 로직 재사용 관계

본 Epic은 별도의 수익 계산 로직을 포함하지 않는다. 집계 시 각 Sale에 대해 **Epic 10의 `ProfitCalculator` 로직을 호출**해 세전/세후 순수익을 산출한 뒤, 그룹화하여 합산한다.

Epic 10 계산 공식 (재사용):
```
대응취득가액 = Allocation.allocationPrice × Sale.quantity
              (allocationPrice가 null이면 IPO.fixedPrice로 대체)
대응취득비용 = ROUND( Σ(ACTIVE Cost.amount) × Sale.quantity / Allocation.allocatedQuantity )
세전순수익 = Sale.amount − 대응취득가액 − 대응취득비용 − Sale.fee − Sale.otherCost
세후순수익 = 세전순수익 − Sale.tax
```

---

## 2. API 엔드포인트 목록

모든 엔드포인트는 **GET**이며, 계산 결과를 저장하지 않는다(D-D-002, D-D-006).

### 2.1 GET /dashboard/summary — 전체 집계

전체 실현수익 집계를 반환한다.

**응답 (성공 — 200 OK):**
```json
{
  "totalInvestmentAmount": 50000000,
  "totalPreTaxProfit": 12500000,
  "totalAfterTaxProfit": 11250000,
  "totalProfitRate": 22.50,
  "participationCount": 15
}
```

- `totalInvestmentAmount`: Long. 모든 집계 대상 Allocation의 배정금액 합계 (D-D-001)
- `totalPreTaxProfit`: Long. 전체 ACTIVE Sale의 세전순수익 합산 (잔차 허용, 각 매도 건 단순 합산)
- `totalAfterTaxProfit`: Long. 전체 ACTIVE Sale의 세후순수익 합산
- `totalProfitRate`: Double. `총세후수익 / 총투자금 × 100`. 총투자금이 0이면 0.0
- `participationCount`: Integer. 집계 대상 ACTIVE Sale 건수

**응답 (실패):** 없음. 항상 200 OK (데이터가 없으면 모든 값이 0인 빈 집계 반환)

### 2.2 GET /dashboard/by-investor — 투자자별 집계 목록

투자자별 실현수익 집계 목록을 반환한다.

**응답 (성공 — 200 OK):**
```json
[
  {
    "investorId": 1,
    "investorName": "김철수",
    "totalInvestmentAmount": 20000000,
    "totalPreTaxProfit": 5000000,
    "totalAfterTaxProfit": 4500000,
    "totalProfitRate": 22.50,
    "participationCount": 5
  }
]
```

- `investorId`: Long
- `investorName`: String
- 나머지 필드는 2.1과 동일 의미, 해당 투자자 기준 집계값

**응답 (실패):** 없음. 데이터가 없으면 빈 배열 반환

**정렬**: `investorId` 오름차순

### 2.3 GET /dashboard/by-securities-firm — 증권사별 집계 목록

증권사별 실현수익 집계 목록을 반환한다.

**응답 (성공 — 200 OK):**
```json
[
  {
    "securitiesFirmId": 101,
    "securitiesFirmName": "키움증권",
    "totalInvestmentAmount": 30000000,
    "totalPreTaxProfit": 8000000,
    "totalAfterTaxProfit": 7200000,
    "totalProfitRate": 24.00,
    "participationCount": 10
  }
]
```

- `securitiesFirmId`: Long
- `securitiesFirmName`: String
- 나머지 필드는 2.1과 동일 의미, 해당 증권사 기준 집계값

**응답 (실패):** 없음. 데이터가 없으면 빈 배열 반환

**정렬**: `securitiesFirmId` 오름차순

### 2.4 GET /dashboard/by-period — 기간별 집계 목록

기간별 실현수익 집계 목록을 반환한다. `unit` 파라미터로 월별/연도별을 구분한다.

**요청 파라미터:**
| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| unit | String | 예 | `month` 또는 `year` |

**응답 (성공 — 200 OK, unit=month):**
```json
[
  {
    "period": "2026-10",
    "totalInvestmentAmount": 15000000,
    "totalPreTaxProfit": 4000000,
    "totalAfterTaxProfit": 3600000,
    "totalProfitRate": 24.00,
    "participationCount": 6
  }
]
```

**응답 (성공 — 200 OK, unit=year):**
```json
[
  {
    "period": "2026",
    "totalInvestmentAmount": 50000000,
    "totalPreTaxProfit": 12500000,
    "totalAfterTaxProfit": 11250000,
    "totalProfitRate": 22.50,
    "participationCount": 15
  }
]
```

- `period`: String. `unit=month`이면 `"YYYY-MM"` 형식, `unit=year`이면 `"YYYY"` 형식. 기간 귀속은 `Sale.saleDate` 기준 (D-D-005)
- 나머지 필드는 2.1과 동일 의미, 해당 기간 기준 집계값

**응답 (실패):**
- `unit`이 `month` 또는 `year`가 아니면 400 Bad Request
- `unit` 파라미터 누락 시 400 Bad Request

**정렬**: `period` 오름차순

**유효 기간**: 데이터가 존재하는 기간만 반환 (빈 배열이 아닌, 실제 데이터가 있는 기간만 나열)

---

## 3. Acceptance Criteria

### AC-001: 전체 집계 정상 (GET /dashboard/summary)
- **Given**: ACTIVE Sale이 여러 건 존재하고, 각각에 대응하는 Allocation/Cost 데이터가 준비되어 있음
- **When**: GET `/dashboard/summary` 요청이 전송됨
- **Then**: 200 OK 응답이 반환되고, `totalInvestmentAmount`는 모든 집계 대상 Allocation의 배정금액 합, `totalPreTaxProfit`/`totalAfterTaxProfit`은 각 Sale의 세전/세후순수익을 Epic 10 공식으로 계산한 뒤 단순 합산한 값, `totalProfitRate`는 `totalAfterTaxProfit / totalInvestmentAmount × 100`, `participationCount`는 ACTIVE Sale 건수가 정확히 반환됨

### AC-002: 투자자별 집계 정상 (GET /dashboard/by-investor)
- **Given**: 여러 투자자에게 각각 ACTIVE Sale이 존재함
- **When**: GET `/dashboard/by-investor` 요청이 전송됨
- **Then**: 200 OK 응답이 반환되고, 각 투자자 항목에 대해 해당 투자자의 ACTIVE Sale만 그룹화된 집계값이 포함되며, 투자자별로 정확히 한 항목씩 존재함

### AC-003: 증권사별 집계 정상 (GET /dashboard/by-securities-firm)
- **Given**: 여러 증권사에 소속된 투자자에게 각각 ACTIVE Sale이 존재함
- **When**: GET `/dashboard/by-securities-firm` 요청이 전송됨
- **Then**: 200 OK 응답이 반환되고, 각 증권사 항목에 대해 해당 증권사 소속 투자자의 ACTIVE Sale만 그룹화된 집계값이 포함되며, 증권사별로 정확히 한 항목씩 존재함

### AC-004: 월별 집계 정상 (GET /dashboard/by-period?unit=month)
- **Given**: 서로 다른 월에 판매된 ACTIVE Sale이 존재함
- **When**: GET `/dashboard/by-period?unit=month` 요청이 전송됨
- **Then**: 200 OK 응답이 반환되고, 각 항목의 `period`가 `"YYYY-MM"` 형식이며, `Sale.saleDate` 기준으로 올바르게 그룹화됨. 동일 월의 Sale은 하나의 항목으로 합산됨

### AC-005: 연도별 집계 정상 (GET /dashboard/by-period?unit=year)
- **Given**: 서로 다른 연도에 판매된 ACTIVE Sale이 존재함
- **When**: GET `/dashboard/by-period?unit=year` 요청이 전송됨
- **Then**: 200 OK 응답이 반환되고, 각 항목의 `period`가 `"YYYY"` 형식이며, `Sale.saleDate` 기준으로 올바르게 그룹화됨

### AC-006: 매도 기록 없는 경우 (0 반환)
- **Given**: ACTIVE Sale이 하나도 존재하지 않음 (모든 Allocation에 Sale이 없거나 모두 CANCELLED)
- **When**: GET `/dashboard/summary`, `/dashboard/by-investor`, `/dashboard/by-securities-firm`, `/dashboard/by-period?unit=month`, `/dashboard/by-period?unit=year` 요청이 전송됨
- **Then**: `/dashboard/summary`는 모든 필드가 0인 객체(`totalInvestmentAmount: 0`, `totalPreTaxProfit: 0`, `totalAfterTaxProfit: 0`, `totalProfitRate: 0.0`, `participationCount: 0`)를 반환. 나머지 엔드포인트는 빈 배열(`[]`)을 반환

### AC-007: 수익률 계산 — 총투자금 0일 때 처리
- **Given**: ACTIVE Sale은 존재하지만 대응하는 Allocation의 배정금액이 0인 경우 (또는 총투자금이 0인 경우)
- **When**: GET `/dashboard/summary` 요청이 전송됨
- **Then**: `totalProfitRate`가 `0.0`으로 반환됨 (0으로 나눔 방지). `totalPreTaxProfit`/`totalAfterTaxProfit`은 실제 계산값이 그대로 반환됨

### AC-008: 기간 파라미터 유효성 검사
- **Given**: GET `/dashboard/by-period` 요청이 전송됨
- **When**: `unit` 파라미터가 `month` 또는 `year`가 아닌 값이거나 누락됨
- **Then**: 400 Bad Request 응답이 반환됨

### AC-009: CANCELLED 데이터 제외
- **Given**: CANCELLED 상태의 Sale이 존재함
- **When**: 모든 대시보드 집계 API 요청이 전송됨
- **Then**: CANCELLED Sale은 집계에서 완전히 제외됨 (참여건수에 카운트되지 않고, 수익 합산에 포함되지 않으며, 배정금액 합산에도 영향을 주지 않음)

### AC-010: 읽기 전용·저장 없음 확인
- **Given**: 대시보드 집계 API를 호출할 수 있는 상태임
- **When**: 모든 대시보드 집계 API 요청이 전송됨
- **Then**: 응답 후 데이터베이스에 추가·변경되는 레코드가 없음. 별도 이력/메타데이터 테이블에 쓰지 않음 (D-D-002)

### AC-011: ID 타입 Long 확인
- **Given**: 모든 집계 응답이 정상 반환됨
- **When**: 응답을 확인함
- **Then**: 모든 ID 필드(`investorId`, `securitiesFirmId`)가 Long 타입이며, `period` 문자열 필드를 제외한 모든 숫자 필드가 정수(Long)로 반환됨

### AC-012: 잔차 없이 단순 합산 확인
- **Given**: 분할 매도로 인해 안분 잔차가 발생하는 Sale이 여러 건 존재함
- **When**: 대시보드 집계 요청이 전송됨
- **Then**: 각 매도 건의 세전/세후순수익을 단순 합산한 값이 집계 결과가 됨. 잔차 재분배나 보정 로직이 없음 (D-D-003)

### AC-013: 계정 간 데이터 격리 확인 (Master 추가)
- **Given**: 계정 A와 계정 B가 각각 자신의 투자자로 서로 다른 ACTIVE Sale을 보유함
- **When**: 계정 A로 인증된 사용자가 임의의 대시보드 엔드포인트를 호출함
- **Then**: 응답에 포함된 모든 집계값(총투자금, 수익, 참여건수 등)은 계정 A가 소유한 Investor의 데이터만 반영하며, 계정 B의 데이터는 어떤 형태로도 포함되지 않음

---

## 4. 엣지케이스

- **매도 기록이 없는 투자자/증권사**: 투자자별/증권사별 집계에서 ACTIVE Sale이 없는 투자자/증권사는 결과에 포함되지 않음. 빈 배열만 반환
- **기록 없는 기간**: 집계 대상 ACTIVE Sale이 존재하지 않는 기간은 `by-period` 응답에 포함되지 않음
- **선택 필드 null 처리**: `Sale.fee`/`Sale.tax`/`Sale.otherCost`는 null 허용 필드이며, null이면 0으로 간주 (Epic 10 동일)
- **취득가액 산정 불가**: `Allocation.allocationPrice`와 `IPO.fixedPrice`가 모두 null이면 대응취득가액을 0으로 계산. 이는 사용자 데이터 입력 누락 상태이며 자동 보정하지 않음 (Epic 10 동일)
- **배정수량 0 방어**: `Allocation.allocatedQuantity`가 0인 경우 안분 비율을 방어적으로 처리하여 나눗셈 오류 방지 (Epic 10 동일)
- **총투자금 0**: 총투자금이 0인 경우 `totalProfitRate`를 0.0으로 반환하여 0으로 나누는 것을 방지
- **CANCELLED Sale/Allocation**: CANCELLED Sale은 집계에서 완전히 제외. CANCELLED Cost는 matchedCostAmount에 포함되지 않음 (Epic 10 동일)
- **동시성 제어 불필요**: 읽기 전용 집계 API이므로 낙관적/비관적 락, 분산 락을 도입하지 않음
- **캐시/저장 없음**: 계산 결과를 캐싱하거나 저장하지 않음. 원천 데이터가 수정되면 다음 조회에서 항상 재계산 (D-D-002)
- **잔차 허용**: 분할 매도 누적 합계와 배정금액·비용 총합의 원 단위 잔차는 재배분하지 않음 (D-D-003, Epic 10 동일)

---

## 5. 데이터 흐름

### 5.1 엔티티 참조 관계

```
Investor (1) <---- (N) Subscription (1) <---- (1:1) Allocation (1) <---- (N) Sale
                       |                             |
                       |                             +----> IPO (Subscription.ipoId, fixedPrice 대체 단가)
                       +---- (N) Cost
```

- Sale은 Allocation을 N:1로 참조 (외래 키: allocationId)
- Allocation은 Subscription을 정확히 1:1로 참조 (외래 키: subscriptionId)
- Cost는 Subscription을 N:1로 참조 (외래 키: subscriptionId)
- Investor는 Subscription을 통해 Sale에 간접 참조
- SecuritiesFirm는 Investor에 연결 (투자자 소속 증권사)

### 5.2 전체 집계 흐름 (GET /dashboard/summary)

```
1. 모든 ACTIVE Sale 조회
2. 각 Sale에 대해 Epic 10 ProfitCalculator 로직 호출:
   a. Sale → Allocation 조회 → 대응취득가액 계산
   b. Allocation → Subscription → ACTIVE Cost 조회 → 대응취득비용 계산
   c. 세전순수익 = Sale.amount − 대응취득가액 − 대응취득비용 − Sale.fee − Sale.otherCost
   d. 세후순수익 = 세전순수익 − Sale.tax
3. 총투자금 = Σ(Allocation.allocatedQuantity × Allocation.allocationPrice)
   (집계 대상 Allocation: ACTIVE Sale이 존재하는 Allocation만)
4. 총세전수익 = Σ(각 Sale의 세전순수익)
5. 총세후수익 = Σ(각 Sale의 세후순수익)
6. 총수익률 = 총세후수익 / 총투자금 × 100 (총투자금 0이면 0.0)
7. 참여건수 = ACTIVE Sale 건수
8. 결과 DTO 응답 (저장 없음)
```

### 5.3 투자자별/증권사별 집계 흐름 (GET /dashboard/by-investor, /dashboard/by-securities-firm)

```
1. 전체 ACTIVE Sale 조회 (2.2 전체 집계의 1단계와 동일)
2. 각 Sale에 대해 Epic 10 ProfitCalculator 로직 호출 (2.2의 2단계와 동일)
3. 그룹화:
   - by-investor: Sale → Investor별로 그룹핑
   - by-securities-firm: Sale → Investor → SecuritiesFirm별로 그룹핑
4. 각 그룹 내에서:
   a. totalInvestmentAmount = Σ(그룹 내 Allocation의 배정금액)
   b. totalPreTaxProfit = Σ(그룹 내 세전순수익)
   c. totalAfterTaxProfit = Σ(그룹 내 세후순수익)
   d. totalProfitRate = totalAfterTaxProfit / totalInvestmentAmount × 100
   e. participationCount = 그룹 내 ACTIVE Sale 건수
5. 그룹별 결과 리스트 응답 (저장 없음)
```

### 5.4 기간별 집계 흐름 (GET /dashboard/by-period)

```
1. 전체 ACTIVE Sale 조회 (위와 동일)
2. 각 Sale에 대해 Epic 10 ProfitCalculator 로직 호출 (위와 동일)
3. Sale.saleDate 기준 그룹화 (D-D-005):
   - unit=month: saleDate → "YYYY-MM" 문자열로 그룹핑
   - unit=year: saleDate → "YYYY" 문자열로 그룹핑
4. 각 그룹 내에서 5.3의 4단계(a~e) 동일 적용
5. period 오름차순 정렬 후 결과 리스트 응답 (저장 없음)
```

### 5.5 Epic 10 계산 로직 재사용 관계 명시

```
┌─────────────────────────────────────────────────────┐
│              Epic 12 대시보드 집계 API                 │
│                                                      │
│  ┌──────────────┐    ┌──────────────────────────┐   │
│  │  Endpoint    │───→│  Epic 10 ProfitCalculator │   │
│  │  (그룹화/합산)│    │  (매도 건별 세전/세후 계산) │   │
│  └──────────────┘    └──────────┬───────────────┘   │
│                                 │                    │
│                    ┌────────────▼────────────┐      │
│                    │  Sale / Allocation /     │      │
│                    │  Cost / IPO (원천 레코드) │      │
│                    └─────────────────────────┘      │
│                                 │                    │
│                    ┌────────────▼────────────┐      │
│                    │  파생값 집계 (저장 없음) │      │
│                    │  on-demand 계산 (D-D-002) │      │
│                    └─────────────────────────┘      │
└─────────────────────────────────────────────────────┘
```

- Epic 12는 **별도의 수익 계산 로직을 포함하지 않음**
- 모든 세전/세후 순수익 산출은 Epic 10의 `ProfitCalculator`를 통해 수행
- Epic 12는 그룹화(GROUP BY)와 합산(SUM)만 수행
- Epic 10의 계산 공식, 안분 로직, 잔차 처리, null 처리, CANCELLED 제외 등 모든 규칙이 그대로 적용됨
