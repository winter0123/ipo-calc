# 아키텍처 설계: 투자 내역 조회 API (investment-query-api, Epic 11)

## 아키텍처 개요 (읽기 전용 조회 서비스)

이 Epic은 **읽기 전용 조회 서비스**이다. DB에 새로운 테이블이나 엔티티를 만들지 않으며, 기존 Subscription/Allocation/Sale/Investor/SecuritiesAccount/IPO Repository를 조합해 투자 내역을 조회한다(D-Q-001, D-Q-006).

레이어 구조는 Profit Calculation 아키텍처와 동일하게 따른다:

```
controller → service → domain → repository
```

다만 이 Epic은 **쓰기 엔티티가 없으므로** domain 계층에 InvestmentHistory 엔티티가 없다. 기존 Repository를 조합하여 참조 데이터를 가져오고, Service에서 가공(remainingQuantity 계산, Sales 필터링 등)을 수행한 뒤 DTO로 응답한다.

### 구성 컴포넌트

- `controller.investmenthistory`: `InvestmentHistoryController` — HTTP GET 요청 처리
- `service.investmenthistory`: `InvestmentHistoryQueryService` — 조회 로직 오케스트레이션
- `dto.investmenthistory`: `InvestmentHistoryDto`, `AllocationSummaryDto`, `SaleSummaryDto` — 응답 DTO
- `domain`: InvestmentHistory 도메인 엔티티 없음
- `repository`: InvestmentHistoryRepository 없음 — 기존 `SubscriptionRepository`, `AllocationRepository`, `SaleRepository`, `InvestorRepository`, `SecuritiesAccountRepository`, `IpoRepository`를 조합 사용

### 참조 엔티티 (기존)

| 엔티티 | 소유 Epic | 이 Epic에서 사용하는 필드 |
|--------|----------|------------------------|
| Subscription | Epic 6 | id, subscribedAt, investorId, ipoId, subscriptionQuantity, subscriptionStatus |
| Allocation | Epic 7 | id, subscriptionId, allocatedQuantity, allocationPrice, allocationStatus |
| Sale | Epic 8 | id, allocationId, quantity, amount, fee, tax, otherCost, status, soldAt |
| Investor | Epic 2 | id, accountId |
| SecuritiesAccount | Epic 4 | id, investorId |
| IPO | Epic 5 | id, fixedPrice, name |

### DTO 구조 (중첩 구조)

```
InvestmentHistoryDto
├── Subscription 필드 (id, subscribedAt, ipoId, ipoName, ipoFixedPrice, subscriptionQuantity, subscriptionStatus)
├── AllocationSummaryDto (nullable) — id, allocatedQuantity, allocationPrice, allocationStatus
├── List<SaleSummaryDto> — id, quantity, amount, fee, tax, otherCost, status, soldAt
└── remainingQuantity (Long, nullable, derived)
```

---

## 조회 서비스 설계

### InvestmentHistoryQueryService

단일 서비스 메서드로 전체 조회와 단건 조회를 담당하는 읽기 전용 서비스. 트랜잭션 경계는 `@Transactional(readOnly = true)`로 선언한다.

#### `InvestmentHistoryQueryService.getHistory(Long investorId, Long securitiesAccountId, Long ipoId, int page, int size): Page<InvestmentHistoryDto>`

전체 조회 메서드. 각 단계에서 어떤 Repository 메서드를 호출하는지:

**1단계 — 인증 및 소유권 검증:**
1. `SecurityUtils.getCurrentAccountId()`로 현재 인증된 Account ID 획득
2. 필터 파라미터에 대한 소유권 재검증:
   - `investorId` 지정 시 → `investorRepository.findById(investorId)`로 조회 후, 해당 Investor의 `accountId`가 현재 Account와 일치하는지 확인. 불일치 시 403 Forbidden
   - `securitiesAccountId` 지정 시 → `securitiesAccountRepository.findById(securitiesAccountId)`로 조회 후, 해당 Account의 `accountId`가 현재 Account와 일치하는지 확인. 불일치 시 403 Forbidden
   - `ipoId` 지정 시 → 해당 IPO에 연결된 Subscription 중 요청자 Account 소유인 것이 있는지 확인. `subscriptionRepository.findByIpoIdAndInvestorAccountId(ipoId, currentAccountId)`로 소유권 재검증. 없으면 403 Forbidden

**2단계 — Subscription 페이지 조회:**
3. `subscriptionRepository.findAllByFilters(investorId, securitiesAccountId, ipoId, page, size)` — 필터와 페이지네이션을 적용한 Subscription 목록 조회
   - 정렬: `subscribedAt DESC`
   - 페이지네이션: `page`/`size` offset/limit
   - `size > 100`이면 Controller에서 400 Bad Request

**3단계 — N+1 방지를 위한 배치 조회:**
4. 조회된 Subscription 목록에서 `subscriptionIds` 추출
5. **배치 조회(Allocation)** — `allocationRepository.findBySubscriptionIdIn(subscriptionIds)` — Allocation 목록을 일괄 조회 → `Map<Long, Allocation>` (key: subscriptionId)
6. **배치 조회(Sale)** — `saleRepository.findByAllocationIdInAndStatus(allocationIds, "ACTIVE")` — ACTIVE Sale 목록을 일괄 조회 → `Map<Long, List<Sale>>` (key: allocationId)

**4단계 — DTO 조합:**
7. 각 Subscription에 대해:
   - `allocationMap.get(subscription.getId())`로 Allocation 조회 (없으면 null)
   - Allocation이 있으면 `saleMap.get(allocation.getId())`로 Sale 목록 조회
   - Allocation이 없으면 빈 리스트
   - `remainingQuantity` 계산: Allocation이 null이면 null, 아니면 `allocatedQuantity - Σ(ACTIVE Sale.quantity)`
   - `InvestmentHistoryDto` 구성

**5단계 — 페이지 메타데이터 및 반환:**
8. `PageImpl<InvestmentHistoryDto>`로 페이지 메타데이터(totalElements, totalPages) 포함하여 반환

---

#### `InvestmentHistoryQueryService.getHistoryDetail(Long subscriptionId): InvestmentHistoryDto`

단건 조회 메서드. 각 단계에서 어떤 Repository 메서드를 호출하는지:

**1단계 — Subscription 조회:**
1. `subscriptionRepository.findById(subscriptionId)` — 존재하지 않으면 404 Not Found

**2단계 — 소유권 검증:**
2. `SecurityUtils.getCurrentAccountId()`로 현재 Account ID 획득
3. Subscription의 Investor의 `accountId`가 현재 Account와 일치하는지 확인 → `investorRepository.findById(subscription.getInvestorId())` → `accountId` 비교. 불일치 시 403 Forbidden

**3단계 — Allocation 조회:**
4. `allocationRepository.findBySubscriptionId(subscriptionId)` — 1:1 조회, 없으면 null

**5단계 — Sale 조회:**
5. Allocation이 있으면 → `saleRepository.findByAllocationIdAndStatus(allocation.getId(), "ACTIVE")` — ACTIVE Sale 목록 조회
   - Allocation이 없으면 빈 리스트

**6단계 — remainingQuantity 계산 및 DTO 구성:**
7. `remainingQuantity` = Allocation이 null이면 null, 아니면 `allocatedQuantity - Σ(ACTIVE Sale.quantity)`
8. `InvestmentHistoryDto` 구성 및 반환

---

## N+1 방지 전략 (배치 조회)

Subscription 목록을 페이지네이션으로 가져온 뒤, 각 Subscription마다 개별적으로 Allocation/Sale을 조회하면 N+1 문제가 발생한다. 이 Epic에서는 **Profit Calculation 아키텍처(Epic 10)에서 사용한 배치 조회 패턴**을 재사용한다.

### 적용 흐름 (전체 조회 시)

```
Step 1: Subscription 페이지 조회 (N건)
   ↓
Step 2: subscriptionIds 목록 추출
   ↓
Step 3: Allocation 배치 조회 — allocationRepository.findBySubscriptionIdIn(subscriptionIds)
   → Map<Long, Allocation> (key: subscriptionId)
   ↓
Step 4: allocationIds 목록 추출
   ↓
Step 5: Sale 배치 조회 — saleRepository.findByAllocationIdInAndStatus(allocationIds, "ACTIVE")
   → Map<Long, List<Sale>> (key: allocationId)
   ↓
Step 6: Map으로 조합하여 DTO 구성 (재조회 없음)
```

### 구체적 Repository 메서드

| 단계 | Repository 메서드 | 설명 |
|------|-------------------|------|
| Subscription 조회 | `subscriptionRepository.findAllByFilters(..., Pageable)` | 필터 + 페이지네이션 |
| Allocation 배치 | `allocationRepository.findBySubscriptionIdIn(List<Long> subscriptionIds)` | IN 쿼리로 일괄 조회 |
| Sale 배치 | `saleRepository.findByAllocationIdInAndStatus(List<Long> allocationIds, "ACTIVE")` | IN 쿼리 + 상태 필터 |

### 대안 (JOIN FETCH)

단건 조회(`getHistoryDetail`)의 경우 이미 N:1 관계이므로 N+1 문제가 발생하지 않는다. 전체 조회에서도 페이지네이션이 적용되므로 Subscription 건수가 제한적이고, 배치 조회로 충분히 성능을 확보할 수 있다. 추후 데이터량 증가 시 `@EntityGraph`를 활용한 JOIN FETCH로 전환할 수 있다.

---

## 소유권 검증 설계

### SecurityUtils 재사용

Investor 관리(Epic 2)와 증권계좌 관리(Epic 4)에서 이미 정의된 `util.security.SecurityUtils`를 재사용한다.

```java
// 현재 인증된 Account ID 획득 (Account.id는 Long IDENTITY — 프로젝트 전역 일관성)
Long currentAccountId = SecurityUtils.getCurrentAccountId();
```

`SecurityUtils`는 `SecurityContextHolder`에서 인증된 Account의 ID를 추출하는 헬퍼이다.

### 재검증 로직 위치

소유권 검증은 **Service 레벨**에서 수행된다:

1. **전체 조회(`getHistory`)**:
   - `investorId` 파라미터가 지정되면 → `Investor` 조회 후 `accountId` 비교 → 불일치 시 403
   - `securitiesAccountId` 파라미터가 지정되면 → `SecuritiesAccount` 조회 후 `accountId` 비교 → 불일치 시 403
   - `ipoId` 파라미터가 지정되면 → 해당 IPO에 연결된 Subscription 중 현재 Account 소유 것이 있는지 확인 → 없으면 403

2. **단건 조회(`getHistoryDetail`)**:
   - Subscription 조회 후 → 해당 Subscription의 Investor의 `accountId`와 현재 Account 비교 → 불일치 시 403

### 필터별 소유권 검증 방향

| 필터 | 검증 방식 | 실패 시 |
|------|-----------|---------|
| `investorId` | `investorRepository.findById(investorId).accountId == currentAccountId` | 403 |
| `securitiesAccountId` | `securitiesAccountRepository.findById(securitiesAccountId).accountId == currentAccountId` | 403 |
| `ipoId` | `subscriptionRepository.existsByIpoIdAndInvestorAccountId(ipoId, currentAccountId)` | 403 |
| 미지정 | 현재 Account가 소유한 Investor의 Subscription만 조회 (자동 필터링) | — |

### 기본 동작 (필터 미지정)

필터를 지정하지 않으면, 현재 인증된 Account가 소유한 Investor에 속한 Subscription만 자동으로 조회된다. 이는 `subscriptionRepository.findByInvestorAccountId(currentAccountId, pageable)`로 구현한다.

---

## API 설계 (엔드포인트/요청/응답/에러)

### 엔드포인트 목록

모두 읽기 전용 GET이며, 인증된 Account가 소유한 Investor 데이터만 조회 가능하다(D-Q-006).

### 1. 투자 내역 전체 조회

- **GET** `/investment-history`
- **쿼리 파라미터**:
  - `investorId` (Long, 선택) — 투자자 ID로 필터. 소유권 재검증
  - `securitiesAccountId` (Long, 선택) — 증권 계좌 ID로 필터. 소유권 재검증
  - `ipoId` (Long, 선택) — IPO ID로 필터. 소유권 재검증
  - `page` (int, 기본 0) — 페이지 인덱스 (0-based)
  - `size` (int, 기본 20) — 페이지 크기. 최대 100

**응답 (성공 — 200 OK)**: `Page<InvestmentHistoryDto>`

```json
{
  "content": [
    {
      "id": 1,
      "subscribedAt": "2026-09-20T10:30:00",
      "ipoId": 101,
      "ipoName": "샘플테크",
      "ipoFixedPrice": 12000,
      "subscriptionQuantity": 100,
      "subscriptionStatus": "ACTIVE",
      "allocation": {
        "id": 5,
        "allocatedQuantity": 100,
        "allocationPrice": 12000,
        "allocationStatus": "ACTIVE"
      },
      "sales": [
        {
          "id": 20,
          "quantity": 30,
          "amount": 450000,
          "fee": 1500,
          "tax": 0,
          "otherCost": 0,
          "status": "ACTIVE",
          "soldAt": "2026-09-25T14:00:00"
        }
      ],
      "remainingQuantity": 50
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

**에러 응답**:
- `size > 100`: **400 Bad Request** — `{ "error": "BAD_REQUEST", "message": "size는 최대 100까지 허용됩니다." }`
- 필터 값이 DB에 존재하지 않음: **404 Not Found**
- 소유권 위반: **403 Forbidden** — `{ "error": "FORBIDDEN", "message": "해당 데이터에 접근 권한이 없습니다." }`
- 음수 page / 0 이하 size: **400 Bad Request**

**정렬**: `subscribedAt DESC` (기본 정렬, D-Q-003)

### 2. 투자 내역 단건 상세 조회

- **GET** `/investment-history/{subscriptionId}`
- **경로 파라미터**: `subscriptionId` (Long)

**응답 (성공 — 200 OK)**: `InvestmentHistoryDto` (content 없이 Subscription 객체 직접 반환)

```json
{
  "id": 1,
  "subscribedAt": "2026-09-20T10:30:00",
  "ipoId": 101,
  "ipoName": "샘플테크",
  "ipoFixedPrice": 12000,
  "subscriptionQuantity": 100,
  "subscriptionStatus": "ACTIVE",
  "allocation": {
    "id": 5,
    "allocatedQuantity": 100,
    "allocationPrice": 12000,
    "allocationStatus": "ACTIVE"
  },
  "sales": [
    {
      "id": 20,
      "quantity": 30,
      "amount": 450000,
      "fee": 1500,
      "tax": 0,
      "otherCost": 0,
      "status": "ACTIVE",
      "soldAt": "2026-09-25T14:00:00"
    }
  ],
  "remainingQuantity": 70
}
```

**에러 응답**:
- 존재하지 않는 subscriptionId: **404 Not Found** — `{ "error": "NOT_FOUND", "message": "해당 청약을 찾을 수 없습니다." }`
- 소유권 없는 데이터 접근: **403 Forbidden** — `{ "error": "FORBIDDEN", "message": "해당 데이터에 접근 권한이 없습니다." }`

### DTO 정의

#### InvestmentHistoryDto

```
필드: id(Long), subscribedAt(String), ipoId(Long), ipoName(String),
      ipoFixedPrice(Long, nullable), subscriptionQuantity(int), subscriptionStatus(String),
      allocation(AllocationSummaryDto, nullable),
      sales(List<SaleSummaryDto>),
      remainingQuantity(Long, nullable)
```

#### AllocationSummaryDto

```
필드: id(Long), allocatedQuantity(int), allocationPrice(Long, nullable), allocationStatus(String)
```

#### SaleSummaryDto

```
필드: id(Long), quantity(int), amount(Long), fee(Long), tax(Long), otherCost(Long),
      status(String), soldAt(String)
```

### 파생값 계산 규칙

- **remainingQuantity**: `Allocation.allocatedQuantity - Σ(ACTIVE Sale.quantity)` (D-Q-005)
  - Allocation이 null이면 → null
  - CANCELLED Sale은 합산에서 제외
  - Sale이 0건이면 → `allocatedQuantity`와 동일

---

## 모듈 소유권

| 컴포넌트 | 패키지 | 소유 |
|---------|--------|------|
| InvestmentHistoryController | `controller.investmenthistory` | 이 Epic |
| InvestmentHistoryQueryService | `service.investmenthistory` | 이 Epic |
| InvestmentHistoryDto | `dto.investmenthistory` | 이 Epic |
| AllocationSummaryDto | `dto.investmenthistory` | 이 Epic |
| SaleSummaryDto | `dto.investmenthistory` | 이 Epic |
| SubscriptionRepository | `repository.subscription` | Epic 6 (기존) |
| AllocationRepository | `repository.allocation` | Epic 7 (기존) |
| SaleRepository | `repository.sale` | Epic 8 (기존) |
| InvestorRepository | `repository.investor` | Epic 2 (기존) |
| SecuritiesAccountRepository | `repository.securitiesaccount` | Epic 4 (기존) |
| IpoRepository | `repository.ipo` | Epic 5 (기존) |
| SecurityUtils | `util.security` | Epic 2 (기존, 재사용) |

---

## 의존성

- **JPA/Hibernate**: 기존 `spring-boot-starter-data-jpa` 재사용 (신규 라이브러리 없음)
- **기존 Repository 재사용**: SubscriptionRepository, AllocationRepository, SaleRepository, InvestorRepository, SecuritiesAccountRepository, IpoRepository — 모두 이미 구현됨
- **SecurityUtils**: 기존 `util.security.SecurityUtils` 재사용 (추가 라이브러리 불필요)
- **추가 라이브러리 불필요**: 캐시, 락, 외부 파싱 라이브러리 등이 필요 없다
- **Lombok**: 기존 프로젝트에서 사용하는 경우 DTO에 적용

---

## 리스크

1. **페이징 + 배치 조회 조합 성능**: 페이지 내 Subscription 수가 많을 때, `findBySubscriptionIdIn` IN 쿼리가 대량 파라미터를 가질 수 있다. 일반적으로 페이지 크기 최대 100이므로 IN 파라미터 수는 제한적이나, EXPLAIN 검증이 필요하다.
2. **소유권 검증 누락 위험**: 모든 엔드포인트에서 필터별 소유권 재검증을 누락하지 않도록 주의 필요. `investorId`/`securitiesAccountId`/`ipoId` 각각에 대해 반드시 재검증 로직을 작성해야 한다(D-Q-006).
3. **남은 수량 정확성**: `remainingQuantity`는 on-demand 파생값으로, ACTIVE Sale 기준으로만 계산한다. CANCELLED Sale은 제외해야 하며, 필터링 누락 시 잔여수량이 정확하지 않게 된다.
4. **미배정 청약 처리**: Allocation이 null인 Subscription의 `sales`는 빈 배열, `remainingQuantity`는 null이어야 한다. NPE 방지를 위해 null 체크를 철저히 해야 한다.
5. **기본 정렬 보장**: `subscribedAt DESC` 정렬이 페이지네이션과 함께 일관되게 동작해야 한다. `Pageable` 정렬 명시적 지정이 필요하다.
6. **읽기 전용 보장**: 이 Epic의 API는 GET만 제공하지만, 향후 다른 엔드포인트가 추가될 때 읽기 전용임을 명확히 문서화한다.
7. **데이터 입력 누락**: Allocation이 없으면 `ipoFixedPrice`가 null일 수 있다. `Subscription.ipoId`를 통해 `IpoRepository.findById()`로 `fixedPrice`를 보조 조회하여 응답에 포함할 수 있으나, 이 Epic에서는 Spec에 따라 `ipoFixedPrice`는 Subscription 조회 시점에 포함된 IPO 정보에서 가져온다.

---

## 비고

- **Flyway 마이그레이션**: 이 Epic은 새로운 DB 테이블을 만들지 않으므로 Flyway 마이그레이션이 필요 없다.
- **도메인 엔티티**: InvestmentHistory 엔티티가 없으므로 domain 계층에 새 클래스를 추가하지 않는다.
- **Repository**: InvestmentHistoryRepository가 없으므로 기존 6개 Repository의 메서드를 조합하여 사용한다.
- **동시성 제어**: 읽기 전용 조회 API이므로 낙관적/비관적 락, 분산 락을 도입하지 않는다. 일관된 페이지네이션을 위해 쿼리 실행 시점의 스냅샷을 기준으로 한다.
- **Sale을 조회 기준으로 삼는 설계 금지**: 모든 조회는 Subscription을 기준(anchor)으로 한다. Sale 중심 조회는 미배정/미매도 기록을 누락시킨다(D-Q-001).
- **락 설계 금지**: 읽기 전용 API이므로 락을 도입하지 않는다.
