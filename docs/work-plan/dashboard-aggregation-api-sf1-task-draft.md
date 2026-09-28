# 대시보드 집계 API SF-1 Task 초안 (dashboard-aggregation-api SF-1)

> **게이트 문구**: 이 초안은 Epic 10 SF-3가 main에 병합 확인된 뒤에만 backend 팀장에게 전달한다.

---

## 1. SF-1 범위 요약 (breakdown.md SF-1 섹션 인용)

**범위**: 이 Epic의 "SF-1"이다. 다른 Epic의 SF-1(엔티티/Repository/Migration)과 달리 **테이블·엔티티를 만들지 않고**, 이후 SF가 의존할 공통 기반을 구축한다:
- ① 응답 DTO 4종 (SummaryDto, InvestorAggregationDto, SecuritiesFirmAggregationDto, PeriodAggregationDto)
- ② 에러 처리 프레임워크 (400/403/404 + handler)
- ③ **소유권 검증 헬퍼** (AC-013, D-Q-006)
- ④ **공통 데이터 준비 헬퍼 (`loadCalculatedSales()`)** — 4개 엔드포인트 모두가 공유하는 핵심 메서드
- ⑤ 기존 Repository에 조회 메서드 확장
- ⑥ Epic 10 `ProfitCalculator` 연동 준비

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

**특이사항 — 공통 데이터 준비 헬퍼(`loadCalculatedSales()`) 설계**:
- 전체 ACTIVE Sale을 **한 번만** 로드하므로, 4개 엔드포인트가 각각 중복 조회를 하지 않는다
- 내부 흐름:
  1. `saleRepository.findAllByStatusAndInvestorAccountId("ACTIVE", currentAccountId)` — 현재 계정 소유 ACTIVE Sale 조회
  2. 배치 조회 — `allocationRepository.findBySubscriptionIdIn(...)`, `costRepository.findBySubscriptionIdInAndStatus(...)`, `investorRepository.findByInvestorIdIn(...)`, `securitiesFirmRepository.findByIdIn(...)` (N+1 방지)
  3. 각 Sale에 대해 `profitCalculator.calculate(...)` 호출 → 세전/세후순수익 산출
  4. 반환: `List<CalculatedSale>` (Sale + 세전순수익 + 세후순수익 + investorId + securitiesFirmId + saleDate가 포함된 계산용 기록 목록)
- `getByPeriod`의 `unit` 파라미터 검증은 Controller에서 수행 (month/year 외 값 → 400)
- 이 헬퍼가 올바르게 구현되어야 4개 엔드포인트 모두 정확한 결과를 반환하므로, SF-1의 단위 테스트(ProfitCalculator 호출 경로 검증)를 반드시 작성한다

---

## 2. backend 팀장 전달용 `orca orchestration task-create` 명령

```bash
orca orchestration task-create \
  --task-title "Epic 12 SF-1: 대시보드 집계 API 기반 계층 (DTO 4종, 에러 프레임워크, 소유권 검증 헬퍼, loadCalculatedSales 공통 헬퍼, Repository 확장, ProfitCalculator 연동)" \
  --spec "
목적:
Epic 12(dashboard-aggregation-api) SF-1 기반 계층을 구현한다. 이후 SF-2~4(전체/투자자별/증권사별/기간별 집계 엔드포인트)가 의존할 공통 인프라를 완성한다. 테이블·엔티티·마이그레이션은 만들지 않는다.

범위 (In-scope):
1. 응답 DTO 4종 생성:
   - dto/dashboard/SummaryDto.java (totalInvestmentAmount:Long, totalPreTaxProfit:Long, totalAfterTaxProfit:Long, totalProfitRate:Double, participationCount:Integer)
   - dto/dashboard/InvestorAggregationDto.java (SummaryDto 필드 + investorId:Long, investorName:String)
   - dto/dashboard/SecuritiesFirmAggregationDto.java (SummaryDto 필드 + securitiesFirmId:Long, securitiesFirmName:String)
   - dto/dashboard/PeriodAggregationDto.java (SummaryDto 필드 + period:String[YYYY-MM|YYYY])
2. 에러 처리 프레임워크:
   - exception/dashboard/DashboardExceptionHandler.java (@RestControllerAdvice, 400/403/404 → {error, message} 형식, 기존 모듈 패턴 준수)
3. 소유권 검증 헬퍼:
   - service/dashboard/ownership/DashboardOwnershipVerifier.java
   - SecurityUtils.getCurrentAccountId()로 현재 Account ID 획득
   - Sale → Allocation → Subscription → Investor 경유로 계정 소유권 검증, 불일치 시 403
   - SF-2~4가 반드시 이 헬퍼를 경유하도록 설계
4. 공통 데이터 준비 헬퍼 loadCalculatedSales():
   - service/dashboard/DashboardAggregationService.java 내부 private 메서드
   - 1) saleRepository.findAllByStatusAndInvestorAccountId(\"ACTIVE\", currentAccountId) — 현재 계정 소유 ACTIVE Sale 조회
   - 2) 배치 조회: allocationRepository.findBySubscriptionIdIn, costRepository.findBySubscriptionIdInAndStatus, investorRepository.findByInvestorIdIn, securitiesFirmRepository.findByIdIn (N+1 방지)
   - 3) 각 Sale에 대해 ProfitCalculator 호출 → 세전/세후순수익 산출
   - 4) 반환: List<CalculatedSale> (Sale + 세전순수익 + 세후순수익 + investorId + securitiesFirmId + saleDate)
   - @Transactional(readOnly = true)
5. 기존 Repository 조회 메서드 확장 (각 Epic 소유 모듈에 read-only 메서드 추가):
   - SaleRepository: findAllByStatusAndInvestorAccountId(String status, Long accountId)
   - AllocationRepository: findBySubscriptionIdIn(List<Long>)
   - CostRepository: findBySubscriptionIdInAndStatus(List<Long>, String status)
   - InvestorRepository: findByInvestorIdIn(List<Long>)
   - SecuritiesAccountRepository: findByIdIn(List<Long>)
   - SecuritiesFirmRepository: findByIdIn(List<Long>)
   - SubscriptionRepository: 기존 메서드 확인 후 부족 시 최소 read-only 추가
   - IpoRepository: 기존 메서드 확인 후 부족 시 최소 read-only 추가
6. Controller 기반 클래스:
   - controller/dashboard/DashboardController.java (클래스 한글 주석, AGENTS.md 15장 준수)
7. Service 기반 클래스:
   - service/dashboard/DashboardAggregationService.java (loadCalculatedSales + 4개 집계 메서드 스텁, @Transactional(readOnly = true))
8. ProfitCalculator 연동 준비: Epic 10의 ProfitCalculator 메서드 시그니처에 맞춰 loadCalculatedSales() 내부에서 호출하도록 설계

비범위 (Out-of-scope):
- 실제 엔드포인트 구현 (SF-2: GET /dashboard/summary, SF-3: GET /dashboard/by-investor, /dashboard/by-securities-firm, SF-4: GET /dashboard/by-period)
- Flyway 마이그레이션 작성 (이 Epic은 DB 스키마 변경 없음)
- 새 수익 공식 설계 (모든 계산은 Epic 10 ProfitCalculator 위임, D-D-006)
- Epic 10 미병합 상태에서의 착수 (게이트: Epic 10 main 머지 확인 후만 착수)

읽을 파일 (필수):
- docs/spec/dashboard-aggregation-api.md (AC-001~013, D-D-001~007, API 계약)
- docs/architecture/dashboard-aggregation-api.md (모듈 소유권, loadCalculatedSales 설계, 집계 전략, N+1 방지 배치 조회 패턴)
- docs/breakdown/dashboard-aggregation-api.md (SF-1 상세, 이 문서)
- docs/breakdown/profit-calculation.md (선례: DTO/에러/계산기 분리 패턴, N+1 방지 배치 조회)
- docs/breakdown/investment-query-api.md (선례: 소유권 검증 헬퍼 분리 패턴, Repository 확장 협의 패턴)
- docs/guide-backend.md (검증 명령, 구현 규칙)

수정 가능 파일:
- dto/dashboard/SummaryDto.java (신규)
- dto/dashboard/InvestorAggregationDto.java (신규)
- dto/dashboard/SecuritiesFirmAggregationDto.java (신규)
- dto/dashboard/PeriodAggregationDto.java (신규)
- controller/dashboard/DashboardController.java (신규)
- service/dashboard/DashboardAggregationService.java (신규)
- service/dashboard/ownership/DashboardOwnershipVerifier.java (신규)
- exception/dashboard/DashboardExceptionHandler.java (신규)
- repository/sale/SaleRepository.java (기존 Epic 8 모듈에 메서드 추가)
- repository/allocation/AllocationRepository.java (기존 Epic 7 모듈에 메서드 추가)
- repository/cost/CostRepository.java (기존 Epic 9 모듈에 메서드 추가)
- repository/investor/InvestorRepository.java (기존 Epic 2 모듈에 메서드 추가)
- repository/securitiesaccount/SecuritiesAccountRepository.java (기존 Epic 4 모듈에 메서드 추가)
- repository/securitiesfirm/SecuritiesFirmRepository.java (기존 Epic 4 모듈에 메서드 추가)
- repository/subscription/SubscriptionRepository.java (기존 Epic 6 모듈에 메서드 추가 - 필요 시)
- repository/ipo/IpoRepository.java (기존 Epic 5 모듈에 메서드 추가 - 필요 시)

검증 명령 (docs/guide-backend.md 확인된 실제 명령):
cd backend
DB_PASSWORD=vibe_local_password ./gradlew test --rerun
DB_PASSWORD=vibe_local_password ./gradlew build

완료 조건 (Definition of Done):
1. 위 산출물 파일 14종이 모두 생성되고 컴파일됨 (./gradlew build 통과)
2. 모든 단위 테스트 통과 (./gradlew test --rerun 통과) — 특히:
   - loadCalculatedSales()가 ProfitCalculator를 올바르게 호출하는지 검증하는 단위 테스트
   - DashboardOwnershipVerifier가 계정 불일치 시 403을 던지는지 검증하는 단위 테스트
   - DTO 4종이 Jackson 직렬화/역직렬화되는지 검증
   - Repository 확장 메서드가 쿼리 실행되는지 검증 (Testcontainers 또는 @DataJpaTest)
3. checkstyleMain, checkstyleTest 통과 (Google Java Style 준수)
4. git diff --check 통과 (whitespace 오류 없음)
5. AC-013(계정 격리), AC-009(CANCELLED 제외) 로직이 loadCalculatedSales()와 소유권 검증 헬퍼에 구현되어 있음
6. 작업 로그(work-log)에 실제 실행 명령·결과·문제점 기록
7. 이 Task 완료 후 PR 생성하여 main 머지 (기능 단위 병합, 12.4-1 정책)
" \
  --parent <EPIC_12_TASK_ID> \
  --json
```

> **주의**: 위 명령에서 `<EPIC_12_TASK_ID>`는 실제 Epic 12 부모 Task ID로 치환해야 한다. `--parent` 옵션 값은 backend 팀장이 Epic 12 전체 Task를 생성한 뒤 확인하여 채운다. 실제 `task-create` 실행은 **Epic 10 SF-3 main 병합 확인 후** backend 팀장이 수행한다.

---

## 3. 검증 명령 (docs/guide-backend.md 실제 명령 그대로 인용)

```bash
cd backend
DB_PASSWORD=vibe_local_password ./gradlew test --rerun
DB_PASSWORD=vibe_local_password ./gradlew build
```

> 로컬 환경·Docker·DB 복구 절차와 실제 환경 변수 이름은 `docs/setup-history.md`를 진실 소스로 사용한다. 시크릿 값은 문서·커밋·work-log 어디에도 쓰지 않는다.

---

## 4. 참고 사항

- 이 문서는 **Task 초안**만 담으며, 실제 `task-create` 실행·워커 배정·구현은 Epic 10 SF-3 병합 확인 후 backend 팀장이 진행한다.
- SF-1이 완료되면 SF-2~4가 순차적으로 같은 브랜치(`winter0123/dashboard-aggregation-api`)에서 이어서 구현되며, 각 SF는 PR 단위로 main에 머지된다 (AGENTS.md 12.4-1).
- `loadCalculatedSales()`와 `DashboardOwnershipVerifier`는 SF-2~4의 **핵심 공통 의존성**이므로, SF-1 단계에서 단위 테스트로 철저히 검증해야 한다.
- Repository 확장 메서드 추가는 각 Epic 소유 모듈(Epic 8/7/9/2/4/6/5)에 read-only 조회 메서드만 추가하는 것이므로, 해당 모듈의 기존 패턴(`deletedAt IS NULL` 필터, 명명 규칙 등)을 준수한다.