# 대시보드 집계 API — 세부 기능 분해 (dashboard-aggregation-api breakdown)

> **Epic**: Epic 12 (대시보드 집계 API, dashboard-aggregation-api)
> **분야**: 백엔드 (Java 21 + Spring Boot + PostgreSQL) — backend 워크스페이스
> **작성일**: 2026-09-22
> **API 계약 출처**: docs/spec/dashboard-aggregation-api.md (AC-001~013, D-D-001~007), docs/architecture/dashboard-aggregation-api.md
> **의존 관계**: Epic 10 (profit-calculation) 구현 완료 및 main 머지 완료 — **ProfitCalculator가 이 Epic의 핵심 의존성이므로, SF-1부터 착수하기 전에 Epic 10이 backend에 머지된 상태여야 한다.**

## 드리프트 점검(2026-09-28, 최초 점검)

(1) **엔티티/API 계약 일치 확인**: breakdown이 참조하는 Epic 10 ProfitCalculator 메서드(calculateAcquisitionAmount, calculateMatchedCost, calculatePreTaxProfit, calculateAfterTaxProfit, calculateAllocationRatio)는 profit-calculation spec의 SF-1 정의와 일치함. Epic 11의 소유권 검증 원칙(D-Q-006, 인증된 Account가 소유한 Investor 데이터만 반환)도 investment-query-api spec과 이 breakdown의 소유권 검증 헬퍼 설계가 일치함.

(2) **선행조건 충족 상태(최초 점검 시점)**: Epic 8(sale-management) 완전히 병합됨. Epic 9(cost-recording) SF-1만 병합, SF-4까지 진행 중. Epic 10(profit-calculation)은 아직 병합되지 않음(backend에 profitcalculation 패키지 없음) — breakdown의 게이트 조건 "Epic 10 머지 완료"는 여전히 미충족이라 SF-1 착수 불가. Epic 11(investment-query-api)은 SF-1 병합, SF-2 진행 중.

## 드리프트 재점검(2026-09-28, 같은 날 후속 — Epic 10 SF-1~2 병합 반영)

최초 점검 이후 같은 세션에서 Epic 9 전체 완료(#128 close), Epic 10 SF-1(PR #152)·SF-2(PR #155) 병합, Epic 11 SF-2(PR #153) 병합이 추가로 발생했다. 게이트 재평가 결과는 다음과 같다.

- **여전히 미충족**: breakdown의 착수 게이트는 "Epic 10 **전체** 완료(SF-3까지) 후 main 머지"다. 현재 Epic 10은 SF-3(`GET /investors/{investorId}/profits`, 투자자별 전체 수익 목록)가 backend 팀장에게 막 배정되어 진행 중이며 아직 병합되지 않았다. 따라서 Epic 12 SF-1 착수는 계속 대기 상태이고, 이 breakdown 문서와 코드 작업은 시작하지 않는다.
- **ProfitCalculator 계약 재확인**: Epic 10 SF-1(PR #152)이 병합되며 `calculateAcquisitionAmount`/`calculateMatchedCost`/`calculatePreTaxProfit`/`calculateAfterTaxProfit`/`calculateAllocationRatio` 시그니처가 실제 코드로 고정됐다. SF-2(PR #155)의 `GET /sales/{saleId}/profit` 구현도 이 메서드들을 그대로 사용해 breakdown이 가정한 계약과 일치함을 코드 레벨에서 확인함(별도 코드 변경 필요 없음).
- **Epic 11 SF-3 의존과는 무관**: 이 Epic의 게이트는 Epic 10만 해당하며 Epic 11 SF-3(투자 내역 단건 상세)과는 직접 의존이 없다. Epic 11 진행 상태는 이 breakdown에 영향을 주지 않는다.
- **다음 재평가 시점**: backend 팀장이 Epic 10 SF-3를 병합 완료로 보고하면, 그 시점에 이 게이트를 다시 확인하고 즉시 SF-1 Subticket을 생성한다.

(3) **roadmap.md 8절 사용자 결정 영향**: Epic 12 집계 로직에 영향 없음 — 취소된 청약 투자내역 표시 결정은 Epic 11 조회 API에 영향을 주지만, 대시보드 집계는 CANCELLED Sale을 자체적으로 제외하므로 무관. apiFetch #134는 프론트엔드 에러 처리 결정으로 백엔드 집계 로직과 무관. breakdown의 가정은 여전히 유효함.

---

## 이 Epic의 특이사항 (분해 전에 반드시 인지)

1. **자체 테이블/엔티티가 없다.** Sale(Epic 8) → Allocation(Epic 7) → Subscription(Epic 6) → Cost(Epic 9)/IPO(Epic 5) → Investor(Epic 2)/SecuritiesAccount(Epic 4)/SecuritiesFirm(Epic 4)를 조합해 파생 집계값을 on-demand로 계산하는 **읽기 전용 집계 API**다. domain 계층에 dashboard 전용 엔티티를 만들지 않고, 기존 Repository를 조합한다 (architecture.md "구성 컴포넌트").
2. **Flyway 마이그레이션이 없다.** 이 Epic은 DB 스키마 변경이 전혀 없으므로, 기존 마이그레이션 파일(V7__create_subscriptions_table.sql까지)에 변경을 가하지 않는다.
3. **Epic 10의 ProfitCalculator를 그대로 재사용한다.** 이 Epic은 별도의 수익 계산 로직을 포함하지 않으며, 모든 세전/세후 순수익 산출은 Epic 10의 `ProfitCalculator`를 통해 수행한다 (D-D-006). 따라서 **Epic 10이 main에 머지된 뒤에 이 Epic SF-1을 착수해야 한다.**
4. **4개 엔드포인트가 모두 동일한 공통 데이터 준비(`loadCalculatedSales()`)를 공유한다.** 전체 ACTIVE Sale 조회 → 배치 조회(N+1 방지) → ProfitCalculator 호출 → 그룹핑·합산의 흐름이 4개 엔드포인트 모두 동일하므로, Service 레벨에서 공통 헬퍼 메서드로 추출한다.
5. **계정 소유권 검증이 필수다 (AC-013, D-Q-006).** 모든 대시보드 엔드포인트는 **로그인한 Account가 소유한 Investor의 데이터만** 집계 대상으로 삼는다. `SecurityUtils.getCurrentAccountId()`로 현재 Account ID를 얻고, Sale → Allocation → Subscription → Investor 경유로 계정 스코프를 강제한다 (Epic 11 D-Q-006과 동일 원칙).

---

## 세부 기능(SF) 개요

총 **4개 SF**로 분해한다. 순차 의존 관계이며 각 SF는 PR 하나로 리뷰 가능한 크기다. 패턴은 Profit Calculation(Epic 10) / Investment Query(Epic 11)과 동일하게 **"기반 계층 → 개별 엔드포인트"** 순서로 나눈다.

```
SF-1 (기반 계층: 응답 DTO 4종, 에러 프레임워크, 소유권 검증 헬퍼, 공통 데이터 준비 헬퍼, Repository 조회 메서드 확장)
  → SF-2 (전체 집계 API  GET /dashboard/summary)
  → SF-3 (투자자별·증권사별 집계 API  GET /dashboard/by-investor, GET /dashboard/by-securities-firm)
  → SF-4 (기간별 집계 API  GET /dashboard/by-period?unit=month/year)
```

| SF | 내용 | 커버 AC | 선행 SF |
|---|---|---|---|
| SF-1 | 기반 계층 (SummaryDto·InvestorAggregationDto·SecuritiesFirmAggregationDto·PeriodAggregationDto, 에러 handler, 소유권 검증 헬퍼, `loadCalculatedSales()` 공통 헬퍼, Repository 조회 메서드 확장, ProfitCalculator 연동 준비) | 직접 커버하지 않음 — 인프라를 만든다. AC-013(계정 격리) 등은 SF-2~4 테스트로 검증 | 없음 (단 **구현 정책상 Epic 10 머인 게이트를 받음**) |
| SF-2 | `GET /dashboard/summary` — 전체 실현수익 집계 (총투자금, 총세전/세후수익, 수익률, 참여건수) | AC-001, AC-006, AC-007, AC-009, AC-010, AC-011, AC-012, AC-013 | SF-1 + **Epic 10 머지** |
| SF-3 | `GET /dashboard/by-investor`, `GET /dashboard/by-securities-firm` — 투자자별/증권사별 그룹핑 집계 | AC-002, AC-003, AC-009, AC-010, AC-011, AC-013 | SF-1 + **Epic 10 머지** |
| SF-4 | `GET /dashboard/by-period?unit=month/year` — 기간별 그룹핑 집계 (unit 파라미터 검증 포함) | AC-004, AC-005, AC-008, AC-009, AC-010, AC-011 | SF-1 + **Epic 10 머지** |

---

## SF-1: 기반 계층 — 응답 DTO, 에러 프레임워크, 소유권 검증 헬퍼, 공통 데이터 준비, Repository 메서드 확장

**범위**: 이 Epic의 "SF-1"이다. 다른 Epic의 SF-1(엔티티/Repository/Migration)과 달리 **테이블·엔티티를 만들지 않고**, 이후 SF가 의존할 공통 기반을 구축한다: ① 응답 DTO 4종, ② 에러 처리 프레임워크(400/403/404 + handler), ③ **소유권 검증 헬퍼**(AC-013, D-Q-006), ④ **공통 데이터 준비 헬퍼(`loadCalculatedSales()`)** — 4개 엔드포인트 모두가 공유하는 핵심 메서드, ⑤ 기존 Repository에 조회 메서드 확장, ⑥ Epic 10 `ProfitCalculator` 연동 준비.

**커버하는 AC**: 직접 커버하지 않고 인프라를 만든다. 소유권 검증 헬퍼와 `loadCalculatedSales()`가 AC-013(계정 격리)과 AC-009(CANCELLED 제외)의 로직을 담당하며, 실제 검증은 SF-2~4의 엔드포인트 테스트로 확인한다.

**선행 SF**: 없음 (첫 번째 SF). **단, 구현 착수 게이트로 Epic 10(main 머지)을 받는다** — ProfitCalculator의 메서드 시그니처가 SF-1에서 직접 호출되기 때문.

**산출물 파일 경로** (architecture.md "모듈 소유권" 참고. 신규 모듈 루트 패키지는 `com.vibeaiteam.dashboard`):
- `dto/dashboard/SummaryDto.java` — 전체 집계 응답 DTO. 필드: totalInvestmentAmount(Long), totalPreTaxProfit(Long), totalAfterTaxProfit(Long), totalProfitRate(Double), participationCount(Integer)
- `dto/dashboard/InvestorAggregationDto.java` — 투자자별 집계 응답 DTO. SummaryDto 필드 + investorId(Long), investorName(String)
- `dto/dashboard/SecuritiesFirmAggregationDto.java` — 증권사별 집계 응답 DTO. SummaryDto 필드 + securitiesFirmId(Long), securitiesFirmName(String)
- `dto/dashboard/PeriodAggregationDto.java` — 기간별 집계 응답 DTO. SummaryDto 필드 + period(String, "YYYY-MM" | "YYYY")
- `controller/dashboard/DashboardController.java` — 클래스 주석 + 메서드 주석 한글 신설 (AGENTS.md 15장). `unit` 파라미터 검증 로직 포함 (AC-008, AC-005)
- `service/dashboard/DashboardAggregationService.java` — **핵심 서비스**. `loadCalculatedSales()` 공통 헬퍼 + 4개 집계 메서드(`getSummary`, `getByInvestor`, `getBySecuritiesFirm`, `getByPeriod`). `@Transactional(readOnly = true)`
- `service/dashboard/ownership/DashboardOwnershipVerifier.java` — **소유권 검증 헬퍼**. `SecurityUtils.getCurrentAccountId()`로 현재 Account ID를 얻고, Sale이 속한 Investor의 Account와 비교한다. 불일치 시 403 처리. **SF-2~4가 반드시 이 헬퍼를 경유하도록 강제** (AC-013)
- `exception/dashboard/DashboardExceptionHandler.java` — `@RestControllerAdvice`로 400/403/404 응답을 `{ "error": "...", "message": "..." }` 형식으로 변환. 기존 모듈의 예외 핸들러 패턴을 따른다
- `repository/sale/SaleRepository.java` **확장**(Epic 8 소유, 이 Epic에서 기여): `findAllByStatusAndInvestorAccountId("ACTIVE", currentAccountId)` — 현재 계정 소유 ACTIVE Sale만 조회 (Master 수정: 계정 간 데이터 격리)
- `repository/allocation/AllocationRepository.java` **확장**(Epic 7 소유): `findBySubscriptionIdIn(List<Long>)` — 배치 조회용
- `repository/cost/CostRepository.java` **확장**(Epic 9 소유): `findBySubscriptionIdInAndStatus(List<Long>, "ACTIVE")` — 배치 조회용
- `repository/investor/InvestorRepository.java` **확장**(Epic 2 소유): `findByInvestorIdIn(List<Long>)` — 투자자 이름 매핑용 배치 조회
- `repository/securitiesaccount/SecuritiesAccountRepository.java` **확장**(Epic 4 소유): `findByIdIn(List<Long>)` — 증권사 매핑용 배치 조회
- `repository/securitiesfirm/SecuritiesFirmRepository.java` **확장**(Epic 4 소유): `findByIdIn(List<Long>)` — 증권사 이름 매핑용
- `repository/subscription/SubscriptionRepository.java` **확장**(Epic 6 소유): 기존 메서드로 충분한지 확인, 부족 시 read-only 메서드 추가
- `repository/ipo/IpoRepository.java` **확장**(Epic 5 소유): 기존 메서드로 충분한지 확인

**특이사항 — 공통 데이터 준비 헬퍼(`loadCalculatedSales()`) 설계 (이 Epic에서 가장 주의가 필요한 부분)**:
- 전체 ACTIVE Sale을 **한 번만** 로드하므로, 4개 엔드포인트가 각각 중복 조회를 하지 않는다
- 내부 흐름:
  1. `saleRepository.findAllByStatusAndInvestorAccountId("ACTIVE", currentAccountId)` — 현재 계정 소유 ACTIVE Sale 조회
  2. 배치 조회 — `allocationRepository.findBySubscriptionIdIn(...)`, `costRepository.findBySubscriptionIdInAndStatus(...)`, `investorRepository.findByInvestorIdIn(...)`, `securitiesFirmRepository.findByIdIn(...)` (N+1 방지)
  3. 각 Sale에 대해 `profitCalculator.calculate(...)` 호출 → 세전/세후순수익 산출
  4. 반환: `List<CalculatedSale>` (Sale + 세전순수익 + 세후순수익 + investorId + securitiesFirmId + saleDate가 포함된 계산용 기록 목록)
- `getByPeriod`의 `unit` 파라미터 검증은 Controller에서 수행 (month/year 외 값 → 400)
- 이 헬퍼가 올바르게 구현되어야 4개 엔드포인트 모두 정확한 결과를 반환하므로, SF-1의 단위 테스트(ProfitCalculator 호출 경로 검증)를 반드시 작성한다

---

## SF-2: 전체 집계 API — GET /dashboard/summary

**범위**: 전체 실현수익 집계 엔드포인트를 구현한다. 현재 계정 소유 ACTIVE Sale 전부에 대해 `loadCalculatedSales()`를 호출하고, 결과를 단일 그룹으로 묶어 SummaryDto를 구성한다. 가장 단순한 엔드포인트이며, 4개 엔드포인트 중 첫 번째로 구현된다.

**커버하는 AC**:
- AC-001: 전체 집계 정상 — totalInvestmentAmount, totalPreTaxProfit, totalAfterTaxProfit, totalProfitRate, participationCount 정확히 반환
- AC-006: 매도 기록 없는 경우 — 모든 필드 0인 빈 집계 반환
- AC-007: 총투자금 0일 때 — totalProfitRate를 0.0 반환 (0으로 나눔 방지)
- AC-009: CANCELLED 데이터 제외 — CANCELLED Sale이 집계에서 완전히 제외
- AC-010: 읽기 전용·저장 없음 — 응답 후 DB 변경 없음
- AC-011: ID 타입 Long 확인 — 모든 숫자 필드가 Long으로 반환
- AC-012: 잔차 없이 단순 합산 — 각 Sale의 세전/세후순수익을 단순 합산
- AC-013: 계정 간 데이터 격리 — 현재 Account 소유 데이터만 반영

**선행 SF**: SF-1 (필수). **백엔드 선행 조건: Epic 10(main 머지)** — ProfitCalculator가 SF-2에서 직접 호출되기 때문.

**산출물 파일 경로**:
- `controller/dashboard/DashboardController.java` — `GET /dashboard/summary` 메서드 추가 (클래스 주석 + 메서드 주석 한글 — AGENTS.md 15장)
- `service/dashboard/DashboardAggregationService.java` — `getSummary(): SummaryDto` 메서드 추가. `loadCalculatedSales()` 호출 후 전체를 단일 그룹으로 집계. `totalInvestmentAmount` = Σ(Allocation.allocationQuantity × allocationPrice), `totalPreTaxProfit` = Σ(세전순수익), `totalAfterTaxProfit` = Σ(세후순수익), `totalProfitRate` = totalAfterTaxProfit / totalInvestmentAmount × 100 (총투자금 0이면 0.0), `participationCount` = ACTIVE Sale 건수

**특이사항**:
- **totalInvestment금 기준**: spec D-D-001에 따라 `Allocation.allocatedQuantity × allocationPrice` 배정금액 기준. 단, allocationPrice가 null이면 Epic 10의 대응취득가액 룰(IPO.fixedPrice 대체)에 따라 ProfitCalculator가 처리. 총투자금과 매도 건별 손익의 기준이 다를 수 있으므로 DTO·테스트에서 명확히 구분
- **데이터 무존재 시**: 모든 필드 0인 SummaryDto 반환 (AC-006). 별도의 404 없음
- **`@Transactional(readOnly = true)`**: Service 메서드에 선언

---

## SF-3: 투자자별·증권사별 집계 API — GET /dashboard/by-investor, GET /dashboard/by-securities-firm

**범위**: 투자자별과 증권사별 그룹핑 집계 엔드포인트를 구현한다. `loadCalculatedSales()`로 얻은 계산용 기록 목록을 `Sale → Investor` 기준과 `Sale → Investor → SecuritiesFirm` 기준으로 각각 그룹핑하고, 그룹별 5필드 집계값을 산출한다. 두 엔드포인트는 같은 Service 메서드 패턴을 따르되, 그룹 키만 다르다.

**커버하는 AC**:
- AC-002: 투자자별 집계 정상 — 각 투자자별로 정확히 한 항목, investorId 오름차순 정렬
- AC-003: 증권사별 집계 정상 — 각 증권사별로 정확히 한 항목, securitiesFirmId 오름차순 정렬
- AC-009: CANCELLED 데이터 제외
- AC-010: 읽기 전용·저장 없음
- AC-011: ID 타입 Long 확인
- AC-013: 계정 간 데이터 격리

**선행 SF**: SF-1 (필수). **백엔드 선행 조건: Epic 10(main 머지)**.

**산출물 파일 경로**:
- `controller/dashboard/DashboardController.java` — `GET /dashboard/by-investor`, `GET /dashboard/by-securities-firm` 메서드 추가 (메서드 주석 한글)
- `service/dashboard/DashboardAggregationService.java` — `getByInvestor(): List<InvestorAggregationDto>`, `getBySecuritiesFirm(): List<SecuritiesFirmAggregationDto>` 메서드 추가. `loadCalculatedSales()` 결과를 각각 investorId / securitiesFirmId 키로 그룹핑. 그룹별 `aggregate` 헬퍼 재사용. investorId/securitiesFirmId 오름차순 정렬
- `service/dashboard/DashboardAggregationService.java` 내부 **private `aggregate` 헬퍼 메서드**: `List<CalculatedSale>`과 그룹 키 추출 함수를 받아, 5필드 집계값을 계산하는 공통 메서드. SF-2의 `getSummary`와 SF-4의 `getByPeriod`가 이 헬퍼를 재사용

**특이사항**:
- **증권사 귀속 기준**: Investor의 소속(SecuritiesAccount.securitiesFirmId) 기준. Investor ↔ SecuritiesFirm은 현재 1:1이므로 그룹 키에 모호성이 없다 (architecture.md)
- **Investor/SecuritiesFirm 이름 매핑**: `loadCalculatedSales()`에서 배치 조회로 가져온 Investor/SecuritiesFirm 정보를 Map으로 결합해 DTO에 이름을 세팅
- **데이터 무존재 시**: 빈 배열(`[]`) 반환 (AC-002/003). 별도의 404 없음
- **그룹핑 키 불일치 리스크**: Investor와 SecuritiesAccount가 1:N 관계가 되는 미래 변경이 생기면 증권사 그룹 키 정의가 모호해질 수 있으므로, 그 시점에 귀속 규칙을 재확정해야 한다 (architecture.md 리스크 4)

---

## SF-4: 기간별 집계 API — GET /dashboard/by-period?unit=month/year

**범위**: 기간별 그룹핑 집계 엔드포인트를 구현한다. `unit` 파라미터(month/year)에 따라 Sale.saleDate를 `"YYYY-MM"` 또는 `"YYYY"` 문자열로 그룹핑하고, 기간별 5필드 집계값을 산출한다. 4개 엔드포인트 중 가장 복잡한 파라미터 검증 로건을 포함한다.

**커버하는 AC**:
- AC-004: 월별 집계 정상 — `period`가 `"YYYY-MM"` 형식, saleDate 기준 그룹핑
- AC-005: 연도별 집계 정상 — `period`가 `"YYYY"` 형식, saleDate 기준 그룹핑
- AC-008: 기간 파라미터 유효성 검사 — unit이 month/year가 아니거나 누락 시 400 Bad Request
- AC-009: CANCELLED 데이터 제외
- AC-010: 읽기 전용·저장 없음
- AC-011: ID 타입 Long 확인

**선행 SF**: SF-1 (필수). **백엔드 선행 조건: Epic 10(main 머지)**.

**산출물 파일 경로**:
- `controller/dashboard/DashboardController.java` — `GET /dashboard/by-period` 메서드 추가 (메서드 주석 한글, `unit` 파라미터 검증). `unit` 누락/비허용값 → 400 Bad Request (`{ "error": "BAD_REQUEST", "message": "unit은 month 또는 year여야 합니다." }`)
- `service/dashboard/DashboardAggregationService.java` — `getByPeriod(String unit): List<PeriodAggregationDto>` 메서드 추가. `unit` 검증(Controller에서 먼저 수행) 후 `loadCalculatedSales()` 결과를 `saleDate` 기준 그룹핑. `unit=month`이면 `"YYYY-MM"`, `unit=year`이면 `"YYYY"` 문자열. period 오름차순 정렬. 데이터가 없는 기간은 포함하지 않음

**특이사항**:
- **`unit` 파라미터 검증 위치**: Controller에서 명시적으로 month/year만 허용하고, 그 외는 400을 던지도록 한다 (AC-008). Service에서는 검증된 값만 받는다고 가정
- **기간 그룹핑 키**: `Sale.saleDate` 기준. LocalDate의 `toString()`을 이용해 `"YYYY-MM"` 또는 `"YYYY"` 문자열 생성. 문자열 정렬로 연·월 자연정렬
- **유효 기간만 반환**: 데이터가 없는 기간은 `by-period` 응답에 포함하지 않음 (AC-004/005)
- **`loadCalculatedSales()` 재사용**: SF-2와 동일한 공통 데이터 준비를 사용하므로, 별도 조회가 필요 없다

---

## 구현 착수 게이트 (중요 — Epic 10 머지 전에는 SF 착수 금지)

이 Epic은 아래 조건이 **만족**된 뒤에만 실제 구현(SF-1부터)을 시작한다. breakdown 문서는 미리 확정해 두지만, Subticket 생성·워커 배분은 게이트가 열린 뒤에 한다.

```
[게이트] Epic 10 (profit-calculation) 이 backend에서
          구현 완료되어 main에 머지되었는가?
    ─ 완료 → SF-1 착수 가능 (Git Manager가 gh를 이용해 머지 확인 후 진행)
    ─ 아니면 → 대기 (SF-1도 착수하지 않는다)
```

- **왜 SF-1도 묶어 두는가**: 기술적으로 SF-1(DTO/에러/소유권 헬퍼/공통 데이터 준비)은 ProfitCalculator를 직접 참조하지 않을 수 있지만, `ProfitCalculator`의 메서드 시그니처와 반환값이 SF-1의 `loadCalculatedSales()` 설계를 결정하며, 전체 모듈이 `com.vibeaiteam.dashboard` 패키지로 하나의 브랜치에서 개발되므로 **Epic 10이 머지된 뒤에 한 번에 착수**한다. breakdown은 미리 확정해 두되, Subticket 생성·워커 배정은 게이트 이후에 한다
- **Epic 10 머인 확인 후 첫 순서**: backend 워크스페이스에서 브랜치 `winter0123/dashboard-aggregation-api`를 `origin/main` 기준으로 신설 → SF-1부터 순차 구현 → 각 SF는 PR 후 main 머지 (12.4-1 기능 단위 병합)
- **이 Epic의 계정 소유권 검증(AC-013)**: 모든 엔드포인트가 `SecurityUtils.getCurrentAccountId()` 기준으로 현재 Account 소유 데이터만 조회. `loadCalculatedSales()`의 `findAllByStatusAndInvestorAccountId("ACTIVE", currentAccountId)`가 핵심 필터이며, 이는 Epic 11의 D-Q-006 패턴과 동일

---

## 의존성 요약

```
[게이트] Epic 10 (profit-calculation) backend 머지 완료
   ↓
SF-1 (기반 계층: DTO 4종, 에러 handler, 소유권 검증 헬퍼, loadCalculatedSales(), Repository 확장)
   └→ SF-2 (GET /dashboard/summary) ── 전체 집계, 5필드 합산 ★ 가장 단순
        └→ SF-3 (GET /dashboard/by-investor, /dashboard/by-securities-firm) ── entity 그룹핑, 이름 매핑
             └→ SF-4 (GET /dashboard/by-period?unit=month/year) ── 기간 그룹핑, unit 파라미터 검증 ★ 가장 복잡한 파라미터
```

**가장 주의가 필요한 부분**:
1. **`loadCalculatedSales()` 공통 헬퍼 설계(SF-1)** — 4개 엔드포인트 모두가 이 메서드에 의존한다. 배치 조회(N+1 방지)가 올바르게 구현되지 않으면 모든 엔드포인트의 성능이 저하된다. `saleRepository.findAllByStatusAndInvestorAccountId` + `findBySubscriptionIdIn`/`findByIdIn` 배치 조회로 반드시 N+1을 방지해야 한다
2. **계정 소유권 검증(AC-013)** — `loadCalculatedSales()`의 `findAllByStatusAndInvestorAccountId("ACTIVE", currentAccountId)`가 첫 번째 필터이지만, 파생 그룹핑 과정에서 타 계정 데이터가 섞이지 않는지도 확인해야 한다. 전체 흐름에서 Account ID 기반 필터가 누락되지 않도록 검증한다
3. **`unit` 파라미터 검증(AC-008)** — Controller에서 month/year 외 값과 누락을 명시적으로 400으로 처리해야 한다. 검증이 누락되면 기본 처리(예: year 취급)로 흘러갈 수 있다
4. **ProfitCalculator 의존성(계약 안정성)** — 이 Epic의 정확성은 Epic 10 계산기가 전부 좌우한다. Epic 10의 메서드 시그니처·반환값이 바뀌면 이 Epic의 `loadCalculatedSales()`가 깨진다. Epic 10과의 계약(입력 Sale, 출력 세전/세후순수익)을 명확히 고정하고, 단위 테스트 시 동일 입력을 사용한다 (architecture.md 리스크 2)

---

## 참고

- **문서 의존성**: `docs/spec/dashboard-aggregation-api.md`, `docs/architecture/dashboard-aggregation-api.md`
- **금지사항**: 코드/엔티티/DB 스키마 작성 금지 — 이 문서는 "무엇을 어떤 순서로 만들지"를 정하는 계획 문서. 코드 작성은 각 SF가 착수(구현 게이트 통과)할 때 별도로 진행된다
- **금지사항**: Flyway 마이그레이션 작성 금지 — 이 Epic은 새 DB 테이블이 없으므로 필요 없다 (architecture.md 비고)
- **금지사항**: 새 수익 공식 설계 금지 — 모든 세전/세후 순수익 산출은 Epic 10의 ProfitCalculator를 통해 수행 (D-D-006). 이 Epic은 그룹핑·합산만 수행한다
- **모듈 소유권 경계**: DashboardController/DashboardAggregationService/DTO는 이 Epic이 소유. Sale/Allocation/Subscription/Investor/SecuritiesAccount/SecuritiesFirm/Cost/Ipo Repository는 각각 Epic 8/7/6/2/4/4/9/5가 소유하므로 **기존 Repository에 대한 read 메서드 추가가 필요한 경우, 해당 Epic의 워커와 협의**해야 한다
- **읽기 전용 보장**: GET 엔드포인트만 제공, `@Transactional(readOnly = true)`, 락 없음, Flyway 없음, 캐시 없음 (D-D-002)
- **검증 명령**: `./gradlew test checkstyleMain checkstyleTest` — 완료 조건(Definition of Done)에 포함 (AGENTS.md 15장)
- **참고 선례**: `docs/breakdown/investment-query-api.md` (Epic 11), `docs/breakdown/profit-calculation.md` (Epic 10) — 같은 "읽기 전용 집계 API" 구조, 3~4 SF 분해 패턴, N+1 방지 배치 조회 전략
- **아키텍처 참고**: 집계 방식은 **애플리케이션 레벨 그룹화(방식 2)** 채택. Epic 10 ProfitCalculator의 안분 계산·null 처리·잔차 정책·CANCELLED 제외 규칙을 그대로 재사용 (architecture.md "집계 전략" 참고)
