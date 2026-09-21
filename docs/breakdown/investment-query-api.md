# 투자 내역 조회 API — 세부 기능 분해 (investment-query-api breakdown)

> **Epic**: Epic 11 (투자 내역 조회 API)
> **분야**: 백엔드 (Java 21 + Spring Boot + PostgreSQL) — backend 워크스페이스
> **작성일**: 2026-09-21
> **API 계약 출처**: docs/spec/investment-query-api.md (AC-001~009, D-Q-001~006), docs/architecture/investment-query-api.md
> **의존 관계**: Epic 6 (subscription-management) 구현 완료(머지됨) + **Epic 7 (allocation-management)·Epic 8 (sale-management)이 backend에 머지된 뒤에 착수** — Subticket 생성·SF-1 착수 시점에 Git Manager가 backend의 Epic 7/8 머지 여부를 먼저 확인한다.

---

## 이 Epic의 특이사항 (분해 전에 반드시 인지)

1. **자체 테이블/엔티티가 없다.** Subscription(Epic 6) → Allocation(Epic 7) → Sale(Epic 8)을 JOIN해 하나의 응답으로 합치는 **순수 읽기 전용 집계 API**다. domain 계층에 새 엔티티·InvestmentHistoryRepository를 만들지 않고, 기존 6개 Repository(Subscription/Allocation/Sale/Investor/SecuritiesAccount/Ipo)를 조합한다 (architecture.md "구성 컴포넌트").
2. **Flyway 마이그레이션이 없다.** 기존 스키마(V7__create_subscriptions_table.sql까지 존재)에 변경을 가하지 않는다.
3. **조회 시에도 계정 소유권 검증이 필수다 (AC-005, D-Q-006).** Epic 7/8(글로벌 기준정보, 소유권 검증 없음)이나 Epic 9(등록 시에만 검증하고 조회 시에는 relaxed)와 달리, 투자 내역은 개인(계정 소유) 데이터이므로 **읽기 레벨에서부터** Subscription → Investor → Account 체인으로 소유권을 강제한다. 이 Epic이 소유권 검증을 어떻게 판단했는지는 아래 "계정 소유권 검증 관련 결정" 표에 상세히 기록했다.
4. **실제 SF 착수는 Epic 7·8 backend 머지가 선행 조건이다.** 이 Epic의 모든 코드가 Allocation/Sale 엔티티와 그 Repository를 참조하므로, Epic 7·8이 머지되기 전에는 컴파일·실행·테스트가 불가능하다. breakdown(이 문서)은 지금 작성해 두되, **실제 구현 착수 가능 시점 = Epic 7, 8이 둘 다 main에 머지된 시점**으로 명확히 못박아 둔다 (아래 "구현 착수 게이트" 절).

---

## 세부 기능(SF) 개요

총 **3개 SF**로 분해한다. 순차 의존 관계이며 각 SF는 PR 하나로 리뷰 가능한 크기다. Epic 7/8/9의 CRUD 5단계(SF-1 엔티티 → SF-2 등록 → SF-3 목록/상세 → SF-4 수정 → SF-5 취소) 구조에는 쓰기 기능이 없어 이 구조를 그대로 따르지 않는다. 대신 **"기반 계층 → 전체 조회 → 단건 상세"** 순서로 나누며, 다른 Epic의 SF-1(엔티티/마이그레이션)에 대응하는 자리는 "조회 기반 계층"이 차지한다.

```
SF-1 (기반 계층: 응답 DTO·에러 프레임워크·소유권 검증 헬퍼·조회 메서드 확장)
  → SF-2 (투자 내역 전체 조회 API  GET /investment-history)
  → SF-3 (투자 내역 단건 상세 API  GET /investment-history/{subscriptionId})
```

| SF | 내용 | 커버 AC | 선행 조건 |
|---|---|---|---|
| SF-1 | 기반 계층 (DTO 3종, 에러 3종+handler, 소유권 검증 헬퍼, Subscription/Investor/SecuritiesAccount/Ipo Repository 조회 메서드 확장) | AC-005의 검증 헬퍼 구현(SF-2/3에서 행단위 검증) | 없음 (기술적으로 Epic 7/8 불필요). 단 구현 정책상 전체가 Epic 7·8 머지 게이트를 함께 받음 |
| SF-2 | `GET /investment-history` — 필터 3종·소유권 재검증, 페이지네이션, Allocation/Sale 배치 조회, DTO 조합 | AC-001,002,003,004,006,008,009 + AC-005(전체 조회 파트) | SF-1 + **Epic 7·8 머지** |
| SF-3 | `GET /investment-history/{subscriptionId}` — exists 404, 소유권 403, 단건 DTO 조합 | AC-007 + AC-005(단건 파트) | SF-2 + **Epic 7·8 머지** |

---

## SF-1: 기반 계층 — 응답 DTO, 에러 프레임워크, 소유권 검증 헬퍼, 조회 메서드 확장

**범위**: 이 Epic의 "SF-1"이다. 다른 Epic의 SF-1(엔티티/Repository/마이그레이션)과 달리 **테이블·엔티티를 만들지 않고**, 이후 SF가 의존할 공통 기반을 구축한다: ① 응답 DTO 3종(중첩 구조), ② 에러 처리 프레임워크(403/404/400 + handler), ③ **소유권 검증 헬퍼**(이 Epic의 핵심 차별점, AC-005), ④ 기존 SubscriptionRepository·InvestorRepository·SecuritiesAccountRepository·IpoRepository에 조회용 메서드 확장. Allocation/Sale 모듈은 건드리지 않는다(그 Repository·엔티티는 Epic 7/8이 소유).

**커버하는 AC**: 직접 커버하지 않고 인프라를 만든다. 소유권 검증 헬퍼가 AC-005의 로직을 담당하며, 실제 검증은 SF-2(전체 조회)와 SF-3(단건)의 엔드포인트 테스트로 확인한다.

**선행 SF**: 없음 (첫 번째 SF)

**산출물 파일 경로** (architecture.md "모듈 소유권" 참고. 신규 모듈 루트 패키지는 `com.vibeaiteam.investmentqueryapi` — cost-recording이 `com.vibeaiteam.costrecording`을 쓴 것과 동일한 명명 스타일):
- `dto/investmenthistory/InvestmentHistoryDto.java` — 응답 1건(row) DTO. 필드: id, subscribedAt, ipoId, ipoName, ipoFixedPrice, subscriptionQuantity, subscriptionStatus, allocation(AllocationSummaryDto, nullable), sales(List<SaleSummaryDto>), remainingQuantity(Long, nullable)
- `dto/investmenthistory/AllocationSummaryDto.java` — id, allocatedQuantity, allocationPrice(nullable), allocationStatus
- `dto/investmenthistory/SaleSummaryDto.java` — id, quantity, amount, fee, tax, otherCost, status, soldAt
- `exception/InvestmentQueryForbiddenException.java`, `InvestmentQueryNotFoundException.java`, `InvestmentQueryValidationException.java`, `InvestmentQueryExceptionHandler.java` — 참고: 기존 모듈의 SubscriptionExceptionHandler 패턴을 그대로 따른다. 403 응답은 spec의 `{ "error": "FORBIDDEN", "message": "해당 데이터에 접근 권한이 없습니다." }`
- `service/investmenthistory/InvestmentHistoryOwnershipVerifier.java` — **소유권 재검증 헬퍼**. `SecurityContextHolder`(기존 `com.vibeaiteam.accountauthentication.util.security.SecurityUtils.getCurrentAccountId()`)로 현재 Account ID를 얻고, 필터 파라미터 3종(investorId/securitiesAccountId/ipoId)과 Subscription 단건의 소유권을 검증한다. 실패 시 InvestmentQueryForbiddenException(403)
- `repository/subscription/SubscriptionRepository.java` **확장**(Epic 6 모듈, read-only 메서드 추가 — Epic 6 워커는 SF-3 PR #105 머지로 이미 해제되어 충돌 없음):
  - `existsByIpoIdAndInvestorAccountId(Long ipoId, Long currentAccountId)` — ipoId 필터 소유권 재검증용 (architecture.md)
  - `findByInvestorAccountId(Long currentAccountId, Pageable)` — 필터 미지정 시 기본 조회 (architecture.md 183행)
- `repository/investor/InvestorRepository.java`·`repository/securitiesaccount/SecuritiesAccountRepository.java`·`repository/ipo/IpoRepository.java` — 소유권 재검증에 필요한 `findById` 등 **기존 메서드로 충분한지 확인**. 부족하면 이 SF에서 최소한의 read-only 메서드만 추가한다

**특이사항 — 소유권 검증 헬퍼의 검증 절차 (이 Epic에서 가장 주의가 필요한 부분)**:
- **investorId 필터**: `investorRepository.findById(investorId)`로 조회 → `Investor.accountId != currentAccountId`면 403
- **securitiesAccountId 필터**: 로컬 `SecuritiesAccount` 엔티티는 `accountId` 컬럼이 **없고 `investorId`만** 있다(백엔드 실제 코드 확인: investorId, securitiesFirmId, accountNumber, accountName, isDefault, status). 따라서 architecture.md의 "accountId 비교" 표기는 **SecuritiesAccount → Investor(accountId) 2홉**으로 해석해야 한다 — `investorRepository.findById(securitiesAccount.getInvestorId()).accountId`와 currentAccountId 비교. 불일치 시 403
- **ipoId 필터**: `existsByIpoIdAndInvestorAccountId(ipoId, currentAccountId)`로 "현재 계정 소유 청약 중 해당 IPO 건이 하나라도 있는지" 확인, 없으면 403
- **단건(부분 조회)**: Subscription의 Investor의 accountId와 비교 (SF-3의 `getHistoryDetail`에서 재사용)
- **필터 미지정**: 검증 헬퍼는 건너뛰고, 결과 쿼리 자체가 `findByInvestorAccountId(currentAccountId)`로 계정 스코프를 강제한다

이 헬퍼를 SF-1에 먼저 격리하는 이유: AC-005(소유권 없는 데이터 접근 차단)가 이 Epic의 보안 핵심이고 architecture.md 리스크 2번이 "소유권 검증 누락 위험"이므로, 보안 로직을 하나의 독립 유닛으로 만들어 SF-2/3에서 양쪽 엔드포인트가 반드시 이 헬퍼를 경유하도록 강제한다.

---

## SF-2: 투자 내역 전체 조회 API (GET /investment-history)

**범위**: 전체 조회 엔드포인트를 구현한다. 필터(investorId/securitiesAccountId/ipoId, 각각 소유권 재검증), 기본 정렬(subscribedAt DESC, D-Q-003), 페이지네이션(page/size, size 최대 100), Allocation/Sale 배치 조회(N+1 방지), 중첩 DTO 조합, remainingQuantity 계산(D-Q-005)을 포함한다.

**커버하는 AC**: AC-001 (미배정 조회 — allocation=null, sales=[], remainingQuantity=null), AC-002 (배정만·미매도 — remainingQuantity=allocatedQuantity), AC-003 (부분매도 — remainingQuantity 계산), AC-004 (전량매도 — remainingQuantity=0), AC-005 (소유권 없는 데이터 접근 차단 — 전체 조회 파트, 검증 헬퍼 경유), AC-006 (페이지네이션 — size>100 400 포함), AC-008 (기본 정렬 subscribedAt DESC), AC-009 (investorId 필터 조회 + 소유권 재검증)

**선행 SF**: SF-1 (필수). **백엔드 선행 조건: Epic 7(allocation)·Epic 8(sale) main 머지** — Allocation/Sale 엔티티와 Repository 배치 메서드가 이 SF부터 참조되기 때문.

**산출물 파일 경로**:
- `controller/investmenthistory/InvestmentHistoryController.java` — `GET /investment-history`. 클래스 주석 + 메서드 주석 한글 신설 (AGENTS.md 15장). size>100·음수 page·size≤0 검증(400) 포함
- `service/investmenthistory/InvestmentHistoryQueryService.java` — `getHistory(Long investorId, Long securitiesAccountId, Long ipoId, int page, int size): Page<InvestmentHistoryDto>`. order: ① 검증 헬퍼로 필터 소유권 재검증 → ② Subscription 페이지 조회(subscribedAt DESC) → ③ Allocation/Sale 배치 조회 → ④ DTO 조합 → ⑤ PageImpl 반환. `@Transactional(readOnly = true)`
- `service/investmenthistory/InvestmentHistoryAssembler.java` — Subscription+Allocation(+Sales) → InvestmentHistoryDto 조합 공통 로직. 계산 규칙: remainingQuantity = (Allocation null → null) 아니면 `allocatedQuantity - Σ(ACTIVE Sale.quantity)` (D-Q-005, CANCELLED Sale 제외). **SF-3에도 재사용**하므로 SF-1에서 함께 두지 않고 여기(=첫 사용처)에서 생성
- `repository/allocation/AllocationRepository.java` **확장**(Epic 7가 생성한 모듈에 Epic 11이 기여): `findBySubscriptionIdIn(List<Long> subscriptionIds)` — 배치 조회 → `Map<Long, Allocation>` (key: subscriptionId)
- `repository/sale/SaleRepository.java` **확장**(Epic 8가 생성한 모듈에 Epic 11이 기여): `findByAllocationIdInAndStatus(List<Long> allocationIds, String status)` (`status = "ACTIVE"`) — 배치 조회 → `Map<Long, List<Sale>>` (key: allocationId)

**특이사항 — 반드시 구현에서 검증할 리스크 (architecture 수정 지점)**:
- **소유권 스코프가 결과 쿼리에 강제되어야 한다.** architecture.md의 흐름(필터 파라미터 개별 재검증 → `findAllByFilters` 조회)만 그대로 따르면, **ipoId만 필터로 지정된 경우** "현재 계정 소유 청약 중 해당 IPO 건이 1건 이상"만 확인되고(`existsByIpoIdAndInvestorAccountId`) 정작 조회 쿼리는 **해당 IPO에 걸린 타 계정의 Subscription까지 반환**하는 누출이 발생한다. 그래서 `subscriptionRepository.findAllByFilters(...)` 는 반드시 **계정 스코프(현재 Account가 소유한 Investor의 Subscription으로 한정)**를 쿼리 내부에서 강제해야 한다 — 예: `Subscription i JOIN Investor WHERE i.investor.accountId = :currentAccountId AND [선택 필터들]` 형태의 JPQL, 또는 조회 후 계정 소유 Investor ID 집합으로 필터. 검증 헬퍼의 1차 재검증만으로 "소유권 필터링이 끝났다"고 가정하지 않는다 (AC-005 위반 방지). 구현 시 AC-005 대상 시나리오(타 계정 청약이 ipoId 필터 결과에 나오지 않는지)를 반드시 테스트로 확인한다.
- **N+1 방지**: Subscription 페이지(N건)마다 Allocation/Sale을 개별 조회하지 않는다. subscriptionIds → `findBySubscriptionIdIn` 1회, allocationIds → `findByAllocationIdInAndStatus` 1회로 배치 조회 후 Map 결합 (architecture.md "N+1 방지 전략").
- **필드 매핑 (실제 엔티티 기준 정정)**: 응답의 `subscriptionQuantity` = `Subscription.quantity`, `ipoName` = `Ipo.ipoName`, `ipoFixedPrice` = `Ipo.fixedPrice` (architecture.md 29행이 Ipo의 필드를 `name`으로 적었지만 실제 엔티티는 `ipoName`; `name` 필드가 없다. 구현 시 architecture 표기와 상관없이 실제 필드 기준으로 매핑한다). `ipoFixedPrice`는 Allocation이 없어도 응답에 포함한다 (spec 2.1 — Subscription 조회 시점의 IPO 정보에서 가져옴, 리스크 7).
- **논리 삭제(deletedAt) 행 제외 — 단, 취소(status=CANCELLED) 청약의 포함 여부는 확인 필요**: 조회 쿼리는 `deletedAt IS NULL` 기본 필터(청약 조회가 쓰는 동일 패턴)로 논리 삭제된 행을 제외한다. 그러나 **status=CANCELLED(취소 상태) 청약이 투자 내역에 나타나야 하는지 여부는 spec에 명시가 없다** — 응답에 `subscriptionStatus`(ACTIVE/CANCELLED) 필드가 정의되어 있어 취소 청약이 포함될 가능성도 열려 있다. 구현 전 Planning(+사용자)에게 "취소된 청약도 투자 내역에 표시할지"를 확인한 뒤 SF-2 작업지시에 반영한다. (임의로 "제외한다"고 가정하지 않는다 — AGENTS.md 3.1)
- `depositAmount`/`memo`는 이 Epic의 응답에 포함하지 않는다 (spec DTO 필드 정의에 없음).

---

## SF-3: 투자 내역 단건 상세 조회 API (GET /investment-history/{subscriptionId})

**범위**: 단건 상세 조회 엔드포인트를 구현한다. subscriptionId 존재 검증(404), 소유권 검증(403), Allocation/Sale 조회, DTO 조합(SF-2의 InvestmentHistoryAssembler 재사용), remainingQuantity 계산을 포함한다. 응답은 `content` 없이 Subscription 객체 직접 반환 (spec 2.2).

**커버하는 AC**: AC-007 (존재하지 않는 ID 404), AC-005 (소유권 없는 데이터 접근 차단 — 단건 파트: 타 계정 소유 Subscription 조회 시 403)

**선행 SF**: SF-2 (필수 — Assembler·Controller·QueryService 기반 재사용). **백엔드 선행 조건: Epic 7·8 main 머지** (SF-2와 동일).

**산출물 파일 경로**:
- `controller/investmenthistory/InvestmentHistoryController.java` — `GET /investment-history/{subscriptionId}` 추가 (메서드 한글 주석 — AGENTS.md 15장)
- `service/investmenthistory/InvestmentHistoryQueryService.java` — `getHistoryDetail(Long subscriptionId): InvestmentHistoryDto` 추가. order: ① `subscriptionRepository.findById(subscriptionId)` 없으면 404 → ② 검증 헬퍼로 Subscription 소유권 검증(Subscription → Investor.accountId 비교), 불일치 403 → ③ `allocationRepository.findBySubscriptionId(subscriptionId)` (Epic 7 SF-3가 이미 만든 메서드 재사용, 없으면 null) → ④ Allocation 있으면 `saleRepository.findByAllocationIdAndStatus(allocationId, "ACTIVE")` (없으면 빈 리스트) → ⑤ Assembler로 DTO 조합
- `repository/sale/SaleRepository.java` **확장**: `findByAllocationIdAndStatus(Long allocationId, String status)` (`status = "ACTIVE"`) — Epic 8 SF-3의 `findByAllocationId`에 status 파라미터가 없으면 이 SF에서 추가. Aggregate가 있으면 재사용

**특이사항**:
- **단건 조회의 검증 순서: 404 → 403** — ① `findById(subscriptionId)`로 대상 row 조회(없으면 404) → ② 소유권 검증(타 계정 소유면 403) → ③ Allocation/Sale 조회 → ④ DTO 조합. (D-Q-006의 "소유권 검증이 조회보다 먼저, 403 우선"은 **필터 파라미터 대상** 규칙이다 — 단건 조회는 대상 row 자체가 있어야 소유권을 판단할 수 있으므로 404가 먼저 나올 수 있다.) 필터 조회와 단건 조회의 순서 규칙을 혼동하지 않도록 각각 구현한다.
- 단건 조회는 N:1·1:1 관계라 N+1 문제가 없다 (architecture.md "대안 (JOIN FETCH)").
- `GET /investment-history/{subscriptionId}`는 Epic 14(web-subscription-workflow) SF-3의 배정 등록 화면 "청약수량 참고 표시"가 사용하는 계약(응답에서 `subscriptionQuantity`)이므로, 응답 필드명 변경 시 frontend와 동시에 협의한다.

---

## 계정 소유권 검증 관련 결정 (Epic 7/8/9과 독립적으로 판단)

> spec AC-005·D-Q-006이 "인증된 Account가 소유한 Investor 데이터만 조회 가능"을 명시하므로, **"조회 시에도 소유권 검증"을 필수로 확정**했다. Epic 7/8("글로벌 기준정보 → 소유권 검증 자체가 없음")과 Epic 9("등록 시에만 검증, 조회 시 relaxed")의 결정을 그대로 가정하지 않고 Epic 11의 spec/architecture를 직접 확인해 판단했다 — 결과적으로 이 Epic은 **조회 계층까지 소유권을 재검증하는, Epic 9보다 더 엄격한 읽기 보안 모델**이다.

| 항목 | 결정 | 근거 |
|------|------|------|
| 계정 소유권 검증 필요 여부 | **필수 (조회 시에도)** | AC-005·D-Q-006: "소유권 없는 데이터 접근 차단", "인증된 Account가 소유한 Investor 데이터만 조회". 투자 내역은 계정 소유의 개인 금융 데이터이므로 Epic 7/8(글로벌 기준정보)과 달리 읽기 레벨에서부터 강제한다 |
| 검증 체인 | **Subscription → Investor → Account (accountId)** | Subscription은 Investor를 N:1로 FK 참조(investorId), Investor는 accountId를 FK로 참조. `SecurityUtils.getCurrentAccountId()`와 비교 (D-Q-006) |
| investorId 필터 재검증 | **필수 (403)** | `investorRepository.findById(investorId).accountId != currentAccountId` → 403. investorId로 타 계정 데이터 지정 차단 |
| securitiesAccountId 필터 재검증 | **필수 (403)** | SecuritiesAccount 엔티티에 accountId가 없고 investorId만 있으므로 **2홉**: SecuritiesAccount → Investor.accountId 비교. 불일치 시 403 (architecture의 "accountId 비교" 표기를 실제 엔티티 기준으로 해석) |
| ipoId 필터 재검증 | **필수 (403)** | `subscriptionRepository.existsByIpoIdAndInvestorAccountId(ipoId, currentAccountId)` — 현재 계정 소유 청약 중 해당 IPO 건이 없으면 403. **그리고** 결과 조회 쿼리에도 계정 스코프를 강제한다 (SF-2 특이사항의 누출 리스크) |
| 필터 미지정 시 기본 동작 | **계정 스코프 자동 필터링** | `findByInvestorAccountId(currentAccountId, Pageable)` — 현재 계정이 소유한 Investor의 Subscription만 반환 (architecture.md 183행) |
| 필터 대상 미존재 시 | **404 (단, 소유권 검증이 먼저 — 소유하지 않으면 403 우선)** | spec 엣지케이스: 존재하지 않는 investorId/securitiesAccountId/ipoId 필터는 404, 소유권 위반은 그 이전에 403 |
| 단건 조회 시 | **403 (타 계정 소유), 404 (미존재)** | spec 2.2 에러 응답: 존재하지 않는 subscriptionId → 404, 소유권 없는 데이터 접근 → 403. 검증 순서는 404 조회 → 403 소유권 |
| `SecurityUtils.getCurrentAccountId()` 사용 여부 | **사용함 (모든 조회 경로)** | 기존 SubscriptionService.register·SecuritiesAccountService·InvestorService와 동일한 계정 소유 데이터 검증 패턴. 신규 클래스 InvestmentQueryForbiddenException(+handler, 403) |
| 정렬·상태 필터 | `deletedAt IS NULL`, ACTIVE Sale만 | 논리 삭제 행 제외, CANCELLED Sale은 sales 배열·잔여수량에서 제외 (spec 엣지케이스). 취소(status=CANCELLED) 청약의 포함 여부는 spec에 명시 없음 → 구현 전 확인 필요 (SF-2 특이사항) |

---

## 구현 착수 게이트 (중요 — Epic 7·8 머지 전에는 SF 착수 금지)

이 Epic은 아래 조건이 **모두** 만족된 뒤에만 실제 구현(SF-1부터)을 시작한다. breakdown 문서는 지금 미리 확정해 두되, Subticket 생성·워커 배분은 이 게이트가 열린 뒤에 한다.

```
[게이트] Epic 7 (allocation-management) 와 Epic 8 (sale-management) 가 둘 다
          backend에서 구현 완료되어 main에 머지되었는가?
    ─ 완료 → SF-1 착수 가능 (Git Manager가 gh를 이용해 머지 확인 후 진행)
    ─ 아니면 → 대기 (SF-1도 착수하지 않는다)
```

- **왜 SF-1도 묶어 두는가**: 기술적으로 SF-1(DTO·에러·소유권 헬퍼·Subscription 조회 메서드)은 Allocation/Sale 코드를 참조하지 않아 Epic 7·8 없이도 컴파일될 수 있다. 그러나 Master 지시에 따라 **이 Epic 전체가 하나의 게이트**로 동작하며, SF-1을 먼저 뽑아두었다가 Epic 7·8이 늦어져 SI 규모가 분산되는 것을 막는다. Epic 7·8은 backend에서 이미 다음 차례의 착수 대상이므로 이 게이트는 짧게 닫힐 예정이다.
- **Epic 7·8 머지 확인 후 첫 순서**: backend 워크스페이스에서 브랜치 `winter0123/investment-query-api` 를 `origin/main` 기준으로 신설 → SF-1부터 순차 구현 → 각 SF는 PR 후 main 머지 (12.4-1 기능 단위 병합).
- **frontend(Epic 14)와의 연계**: 이 Epic의 SF-2(GET /investment-history)가 Epic 14 SF-2(청약 목록 화면)의 실데이터 연동·E2E 선행 조건이고, 이 Epic의 SF-3(GET /investment-history/{id})가 Epic 14 SF-3(배정 등록 화면 참고 표시)의 선행 조건이다 (docs/breakdown/web-subscription-workflow.md). 이 Epic을 머지 순서상 Epic 7·8 직후에 두면 frontend의 청약 워크플로우 진행을 앞당길 수 있다.

---

## 의존성 요약

```
[게이트] Epic 7, 8 backend 머지 완료
   ↓
SF-1 (기반 계층: DTO 3종·에러·소유권 검증 헬퍼·조회 메서드 확장)
   └→ SF-2 (전체 조회 API  GET /investment-history) ── 필터 재검증(3종) + 계정 스코프 누출 방지 ★ 가장 중요
        └→ SF-3 (단건 상세 API  GET /investment-history/{subscriptionId}) ── 404/403 순서 검증
```

- **가장 주의가 필요한 부분**: SF-2의 **소유권 스코프 누출 방지**다. 필터 파라미터 개별 재검증만 수행하면 ipoId 단독 필터에서 타 계정 청약이 섞일 수 있으므로, 결과 조회 쿼리 자체에 계정 스코프를 강제해야 한다(AC-005). 다음으로 SF-2의 N+1 방지 배치 조회(`findBySubscriptionIdIn`, `findByAllocationIdInAndStatus`)와 SF-3의 404→403 검증 순서가 까다롭다.
- **필드 매핑 오류 주의**: `subscriptionQuantity`=Subscription.quantity, `ipoName`=Ipo.ipoName, `ipoFixedPrice`=Ipo.fixedPrice, `securitiesAccountId` 소유권=Investor 2홉 경유 — 실제 엔티티 기준으로 구현하고 architecture.md의 단순 표기(예: Ipo.name)에 의존하지 않는다.
- **remainingQuantity**: on-demand 파생값(D-Q-005). Allocation이 null이면 null, 아니면 `allocatedQuantity - Σ(ACTIVE Sale.quantity)`. CANCELLED Sale 제외.

---

## 참고

- **문서 의존성**: `docs/spec/investment-query-api.md`, `docs/architecture/investment-query-api.md`
- **금지사항**: 코드/엔티티/DB 스키마 작성 금지 — 이 문서는 "무엇을 어떤 순서로 만들지"를 정하는 계획 문서. 이 Epic은 DB 스키마 변경(Flyway) 자체가 없음
- **기존 모듈 수정 범위**: SubscriptionRepository(Epic 6), Investor/SecuritiesAccount/Ipo Repository(Epic 2/4/5)에 read-only 메서드 추가 가능. Allocation/Sale Repository는 Epic 7/8이 만든 후 Epic 11이 배치 메서드를 기여. 다른 에픽이 소유한 모듈을 수정할 때는 그 모듈의 기존 패턴(deletedAt IS NULL 필터, 예외 클래스 명명)을 그대로 따른다
- **에러 응답 규격**: 403 `{ "error": "FORBIDDEN", ... }`, 404 `{ "error": "NOT_FOUND", ... }`, 400 `{ "error": "BAD_REQUEST", ... }` (spec 2.2/AC-006)
- **검증 명령**: `./gradlew test checkstyleMain checkstyleTest` — 완료 조건(Definition of Done)에 포함 (AGENTS.md 15장)
- **읽기 전용 보장**: GET 엔드포인트만 제공, `@Transactional(readOnly = true)`, 락 없음, Flyway 없음 (architecture.md 비고)