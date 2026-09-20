# 투자 내역 조회 API 명세 (investment-query-api, Epic 11)

## 1. 개요

투자자(Investor)의 청약(Subscription) 기준으로 투자 내역을 조회하는 읽기 전용 API를 제공한다.

### 설계 근거: Subscription을 기준(anchor)으로 하는 이유 (D-Q-001)

기존 조사 문서에서 조회 기준을 **Sale(매도) 중심**으로 설계하려 했으나, 이는 실제 설계 결함이다. Sale을 기준(anchor)으로 조회하면 **아직 배정되지 않았거나 배정됐지만 매도하지 않은 청약(Subscription/Allocation)은 결과에 전혀 나타나지 않는다.** 사용자가 "내가 청약한 것 중 아직 안 판 것"을 확인하려는 요구(미매도 기록 표시)와 정면으로 모순된다.

따라서 모든 투자 내역 조회는 **Subscription 1건당 응답 1개 row**를 기본으로 하고, 그 안에 연결된 Allocation(nullable — 아직 배정 전이면 null)과 Sale 목록(0건 이상 배열)을 중첩(nested)으로 포함한다. 이렇게 하면 청약만 하고 아직 배정/매도되지 않은 기록도 자연스럽게 조회 결과에 포함된다.

### 용어 정의

- **Subscription (청약)**: 투자자가 IPO에 청약한 기록 엔티티. 투자 내역 조회의 **기본 단위(anchor)**.
- **Allocation (배정)**: 청약에 대한 배정 결과. 배정 수량(`allocatedQuantity`)과 배정 단가(`allocationPrice`)를 가진다. 아직 배정 전이면 null.
- **Sale (매도)**: 배정받은 주식의 매도 기록. `status`(ACTIVE/CANCELLED)와 `quantity`(매도수량)를 가진다.
- **Investor (투자자)**: 실제 투자 주체. Account가 소유한다.
- **SecuritiesAccount (증권 계좌)**: 투자자가 보유한 증권 계좌.
- **IPO**: 공모주 기준정보. `fixedPrice`(확정공모가)를 가진다.
- **잔여수량**: `Allocation.allocatedQuantity - Σ(ACTIVE Sale.quantity)`. Allocation이 없으면 null. 파생값으로 on-demand 계산 (D-Q-005).

## 2. API 엔드포인트 목록

모든 엔드포인트는 읽기 전용 GET이며, 인증된 Account가 소유한 Investor 데이터만 조회 가능하다 (D-Q-006).

---

### 2.1 투자 내역 전체 조회

```
GET /investment-history
```

#### 쿼리 파라미터

| 이름 | 타입 | 필수 | 기본값 | 설명 |
|------|------|------|--------|------|
| investorId | Long | 아니오 | — | 투자자 ID로 필터. 지정 시 소유권 재검증 (D-Q-006) |
| securitiesAccountId | Long | 아니오 | — | 증권 계좌 ID로 필터. 지정 시 소유권 재검증 (D-Q-006) |
| ipoId | Long | 아니오 | — | IPO ID로 필터. 지정 시 소유권 재검증 (D-Q-006) |
| page | int | 아니오 | 0 | 페이지 인덱스 (0-based), offset용 (D-Q-002) |
| size | int | 아니오 | 20 | 페이지 크기. 최대 100 (D-Q-002) |

#### 응답 (성공 — 200 OK)

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
        },
        {
          "id": 21,
          "quantity": 20,
          "amount": 310000,
          "fee": 1200,
          "tax": 0,
          "otherCost": 0,
          "status": "ACTIVE",
          "soldAt": "2026-09-26T11:00:00"
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

#### 필드 설명

**Subscription 필드 (row 상단):**
- `id` (Long): 청약 고유 ID
- `subscribedAt` (String, ISO 8601): 청약 일시. 기본 정렬 기준 (D-Q-003)
- `ipoId` (Long): 참조 IPO ID
- `ipoName` (String): IPO 이름
- `ipoFixedPrice` (Long, nullable): 확정공모가
- `subscriptionQuantity` (int): 청약 수량(주)
- `subscriptionStatus` (String): 청약 상태 (ACTIVE/CANCELLED)

**Allocation 객체 (nullable):**
- 배정이 완료되지 않은 청약의 경우 이 필드는 `null`
- `id` (Long): 배정 ID
- `allocatedQuantity` (int): 배정 수량(주)
- `allocationPrice` (Long, nullable): 배정 단가
- `allocationStatus` (String): 배정 상태 (ACTIVE/CANCELLED만 허용 — Epic 7 D-A-001)

**Sales 배열 (0건 이상):**
- 배정 후 매도된 기록만 포함
- 각 Sale은 `id`, `quantity`, `amount`, `fee`, `tax`, `otherCost`, `status`(ACTIVE/CANCELLED), `soldAt` 필드 포함
- CANCELLED 상태의 Sale은 포함하지 않음

**remainingQuantity (Long, nullable):**
- `Allocation.allocatedQuantity - Σ(ACTIVE Sale.quantity)` (D-Q-005)
- Allocation이 없으면(미배정) `null`

**페이지네이션 필드 (D-Q-002):**
- `page`: 요청된 페이지 인덱스
- `size`: 요청된 페이지 크기
- `totalElements`: 전체 Subscription 건수
- `totalPages`: 전체 페이지 수

---

### 2.2 투자 내역 단건 상세 조회

```
GET /investment-history/{subscriptionId}
```

#### 경로 파라미터

| 이름 | 타입 | 설명 |
|------|------|------|
| subscriptionId | Long | 조회할 Subscription ID |

#### 응답 (성공 — 200 OK)

전체 조회(2.1)의 단건 content 객체와 동일한 구조. `content` 없이 Subscription 객체 자체를 직접 반환:

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

#### 에러 응답 (D-Q-004)

- 존재하지 않는 subscriptionId: **404 Not Found**
  ```json
  { "error": "NOT_FOUND", "message": "해당 청약을 찾을 수 없습니다." }
  ```
- 소유권 없는 데이터 접근: **403 Forbidden**
  ```json
  { "error": "FORBIDDEN", "message": "해당 데이터에 접근 권한이 없습니다." }
  ```

## 3. Acceptance Criteria

### AC-001: 미배정 청약 조회 (아직 배정되지 않은 Subscription만 존재)

- **Given**: 투자자가 청약했으나 아직 Allocation이 배정되지 않은 Subscription이 존재함
- **When**: `GET /investment-history` 요청이 전송됨
- **Then**: 200 OK 응답이 반환되고, 해당 Subscription의 `allocation` 필드가 `null`이며, `sales` 배열이 빈 배열(`[]`), `remainingQuantity`가 `null`로 응답됨

### AC-002: 배정만 되고 미매도 조회 (Allocation은 있으나 Sale이 없는 경우)

- **Given**: Allocation이 존재하고 `allocatedQuantity`가 100이지만, ACTIVE Sale이 없는 Subscription이 존재함
- **When**: `GET /investment-history` 요청이 전송됨
- **Then**: 200 OK 응답이 반환되고, `allocation` 객체가 포함되며, `sales` 배열이 빈 배열(`[]`), `remainingQuantity`가 `100`(= `allocatedQuantity` - 0)으로 응답됨

### AC-003: 부분매도 조회 (Sale이 일부만 존재)

- **Given**: `allocatedQuantity`가 100인 Allocation에 대해 ACTIVE Sale 2건(각 30주, 20주)이 존재하는 Subscription이 있음
- **When**: `GET /investment-history` 요청이 전송됨
- **Then**: 200 OK 응답이 반환되고, `sales` 배열에 2건의 Sale이 포함되며, `remainingQuantity`가 `50`(= 100 - 30 - 20)으로 응답됨

### AC-004: 전량매도 조회 (Sale이 전량 매도된 경우)

- **Given**: `allocatedQuantity`가 100인 Allocation에 대해 ACTIVE Sale 1건(100주)이 존재하는 Subscription이 있음
- **When**: `GET /investment-history` 요청이 전송됨
- **Then**: 200 OK 응답이 반환되고, `sales` 배열에 1건의 Sale이 포함되며, `remainingQuantity`가 `0`(= 100 - 100)으로 응답됨

### AC-005: 소유권 없는 데이터 접근 차단

- **Given**: 인증된 Account가 소유하지 않은 Investor의 Subscription이 존재함 (또는 다른 Investor의 데이터에 해당하는 investorId/securitiesAccountId가 쿼리 파라미터로 지정됨)
- **When**: `GET /investment-history` 또는 `GET /investment-history/{subscriptionId}` 요청이 전송됨
- **Then**: **403 Forbidden** 응답이 반환됨. 응답 본문: `{ "error": "FORBIDDEN", "message": "해당 데이터에 접근 권한이 없습니다." }`
- **비고**: 명시적으로 `investorId` 쿼리 파라미터를 지정하더라도, 해당 investorId가 요청자 Account가 소유하는 Investor가 아니면 403 반환 (D-Q-006)

### AC-006: 페이지네이션 동작

- **Given**: 25건의 Subscription이 존재함
- **When**: `GET /investment-history?page=0&size=20` 요청이 전송됨
- **Then**: 200 OK 응답이 반환되고, `content`에 20건이 포함되며, `totalElements`가 25, `totalPages`가 2, `page`가 0, `size`가 20으로 응답됨
- **When**: `GET /investment-history?page=1&size=20` 요청이 전송됨
- **Then**: 200 OK 응답이 반환되고, `content`에 5건이 포함되며, `totalElements`가 25, `totalPages`가 2, `page`가 1, `size`가 20으로 응답됨
- **When**: `GET /investment-history?size=100` 요청이 전송됨 (최대치)
- **Then**: size가 100으로 적용됨
- **When**: `GET /investment-history?size=101` 요청이 전송됨 (최대 초과)
- **Then**: **400 Bad Request** 응답이 반환됨: `{ "error": "BAD_REQUEST", "message": "size는 최대 100까지 허용됩니다." }`

### AC-007: 존재하지 않는 ID 조회

- **Given**: 존재하지 않는 subscriptionId가 제공됨
- **When**: `GET /investment-history/{subscriptionId}` 요청이 전송됨
- **Then**: **404 Not Found** 응답이 반환됨: `{ "error": "NOT_FOUND", "message": "해당 청약을 찾을 수 없습니다." }`

### AC-008: 기본 정렬 — subscribedAt DESC

- **Given**: 여러 Subscription이 서로 다른 `subscribedAt`을 가짐
- **When**: `GET /investment-history` 요청이 전송됨 (정렬 파라미터 없이)
- **Then**: 응답 `content`가 `subscribedAt` 내림차순(최근 청약이 먼저)으로 정렬됨 (D-Q-003)

### AC-009: 필터 조합 — investorId 기준 조회

- **Given**: 인증된 Account가 소유한 Investor에 여러 Subscription이 있고, 그 중 일부만 특정 investorId에 연결됨
- **When**: `GET /investment-history?investorId=5` 요청이 전송됨
- **Then**: 해당 investorId에 속한 Subscription만 반환되며, 소유권이 재검증되어 요청자 Account 소유가 맞으면 200 OK, 아니면 403 Forbidden (D-Q-006)

## 4. 엣지케이스

- **미배정 청약 조회**: Allocation이 없는 Subscription은 `allocation` 필드가 `null`이고, `remainingQuantity`도 `null`로 응답됨 (D-Q-005, D-Q-001)
- **Sale이 0건인 Subscription**: `sales` 배열은 빈 배열(`[]`)로 응답되며, `remainingQuantity`는 `allocatedQuantity`와 동일함
- **CANCELLED Sale 제외**: Sale의 `status`가 `CANCELLED`인 경우 `sales` 배열에 포함되지 않으며, 잔여수량 계산에서 제외됨
- **소유권 필터 재검증**: 쿼리 파라미터로 `investorId`, `securitiesAccountId`, `ipoId`를 모두 지정하더라도, 각각에 대해 요청자 Account의 소유권이 재검증됨. 소유하지 않는 필터 대상이 하나라도 있으면 403 반환 (D-Q-006)
- **페이지네이션 경계**: `page`가 음수이거나 `size`가 0 이하이면 **400 Bad Request**. `size`가 100 초과이면 **400 Bad Request** (D-Q-002)
- **존재하지 않는 필터 참조**: 필터로 지정한 `investorId`/`securitiesAccountId`/`ipoId`가 DB에 존재하지 않으면 404 반환. 단, 소유권 검증은 먼저 수행된 후 조회하여, 소유하지 않으면 403이 우선
- **동시성**: 읽기 전용 API이므로 낙관적/비관적 락 불필요. 일관된 페이지네이션을 위해 쿼리 실행 시점의 스냅샷을 기준으로 함

## 5. 데이터 흐름

### Entity 참조 관계도

```
Account (1) <---- (N) Investor (1) <---- (N) Subscription (1) <---- (1:1) Allocation (1) <---- (N) Sale
                                                    |                    |
                                                    |                    +----> IPO (Subscription.ipoId, fixedPrice 대체 단가)
                                                    +---- (N) Cost
```

- **Subscription**은 **Investor**를 N:1로 참조 (외래 키: `investorId`)
- **Subscription**은 **IPO**를 N:1로 참조 (외래 키: `ipoId`)
- **Allocation**은 **Subscription**을 정확히 1:1로 참조 (외래 키: `subscriptionId`)
- **Sale**은 **Allocation**을 N:1로 참조 (외래 키: `allocationId`)
- **Cost**는 **Subscription**을 N:1로 참조 (외래 키: `subscriptionId`)

### GET /investment-history 조회 흐름 (전체 조회)

```
1. 인증 검증: 요청의 Account에서 인증된 Investor 목록 추출 (D-Q-006)
2. 쿼리 파라미터 소유권 재검증:
   - investorId 지정 시 → 해당 Investor가 요청자 Account 소유인지 확인
   - securitiesAccountId 지정 시 → 해당 Account가 요청자 Account 소유인지 확인
   - ipoId 지정 시 → 해당 IPO에 연결된 Subscription 중 요청자 소유 것인지 확인
   - 소유권 위반 시 → 403 Forbidden
3. Subscription 조회:
   - 필터 적용 (investorId / securitiesAccountId / ipoId)
   - 기본 정렬: subscribedAt DESC (D-Q-003)
   - 페이지네이션: page/size offset/limit (D-Q-002)
4. 각 Subscription별로 조회:
   a. Allocation 조회 (Subscription → Allocation, 1:1, 없으면 null)
   b. Sale 조회 (Allocation → Sale, ACTIVE만, 배정된 Allocation이 없으면 빈 배열)
   c. remainingQuantity 계산:
      - Allocation이 null이면 → null
      - Allocation이 있으면 → allocatedQuantity - Σ(ACTIVE Sale.quantity) (D-Q-005)
5. 페이지 메타데이터 계산 (totalElements, totalPages)
6. 응답 구성 및 반환
```

### GET /investment-history/{subscriptionId} 조회 흐름 (단건 조회)

```
1. Subscription 조회 (id = subscriptionId, 없으면 404)
2. 소유권 검증: 해당 Subscription의 Investor가 요청자 Account가 소유하는지 확인 (D-Q-006)
   - 소유하지 않으면 → 403 Forbidden
3. Allocation 조회 (Subscription → Allocation, 1:1, 없으면 null)
4. Sale 조회 (Allocation → ACTIVE Sale 목록, 배정 없으면 빈 배열)
5. remainingQuantity 계산 (D-Q-005)
6. 응답 구성 및 반환
```

### 전체 조회 시 Subscription 중심 조합 구조

```
┌─────────────────────────────────────────────────────┐
│                Subscription (1 row per)               │
│  id, subscribedAt, ipoId, ipoName, ipoFixedPrice,    │
│  subscriptionQuantity, subscriptionStatus             │
├─────────────────────────────────────────────────────┤
│  Allocation (nullable)                                │
│  id, allocatedQuantity, allocationPrice,              │
│  allocationStatus                                     │
├─────────────────────────────────────────────────────┤
│  Sales (array, 0+)                                    │
│  ┌─ id, quantity, amount, fee, tax, otherCost,       │
│  │  status, soldAt                                   │
│  └──────────────────────────────────────────────────┘│
├─────────────────────────────────────────────────────┤
│  remainingQuantity (nullable, derived)                │
│  = allocatedQuantity - Σ(ACTIVE Sale.quantity)      │
│  = null (if Allocation is null)                       │
└─────────────────────────────────────────────────────┘
```
