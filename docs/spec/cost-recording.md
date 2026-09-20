# 청약 비용 기록 기능 명세

## 개요
청약/취득 단계에서 발생하는 비용(예: 청약수수료, 계좌개설비, 이체수수료)을 사용자가 사후에 수동으로 입력·기록·조회하는 기능을 제공한다.
실시간 비용 처리(증권사 API 연동, 자동 부과 수집)가 아니며, 시스템 내에서 비용 정산 워크플로를 처리하지 않는다.
비용 기록은 "이미 발생한 비용 결과를 기록했다"는 사실 자체이므로 사실 기록의 성격을 가지며, 세율/수수료 추정·자동 계산 등 파생 로직은 포함하지 않는다(D-C-005).
상태 전이 머신을 갖지 않으며, 상태값은 ACTIVE/CANCELLED만 사용한다(D-C-004).

> **Epic 8(Sale)과의 책임 경계 (D-C-001)**: 매도 시점의 비용(수수료/세금/기타비용)은 이미 Epic 8의 `Sale.fee`/`Sale.tax`/`Sale.otherCost` 필드로 기록하도록 설계되어 있다([[sale-management]] D-SL-007). Epic 9의 Cost 엔티티는 **청약/취득 단계의 비용만** 대상으로 하며, 매도 비용을 다시 설계하지 않는다. 수익 공식(`매도금액 − 취득 관련 비용 − 매도 수수료 − 세금 − 기타 비용 = 순수익`)에서 "취득 관련 비용" 항목이 이 Epic의 Cost, "매도 수수료/세금/기타 비용" 항목이 Epic 8의 Sale 필드에 각각 대응한다.

## 용어 정의
- **Cost**: 청약/취득 단계에서 발생한 비용을 기록하는 엔티티 (사실 기록, 자동 계산 로직 포함하지 않음 — D-C-005)
- **Subscription**: 비용 기록이 연결될 청약 결과 엔티티 (Epic 6) — Cost는 Subscription을 N:1로 참조함 (D-C-001)
- **비용종류(costType)**: 비용의 이름/종류를 나타내는 **자유 텍스트** (예: "청약수수료", "계좌개설비", "이체수수료"). Enum으로 고정하지 않는다 — 수수료/세금 정책이 자주 바뀌고 증권사마다 다르므로 사용자가 자유롭게 입력한다 (D-C-002)
- **발생일시(incurredAt)**: 해당 비용이 발생한 일시
- **금액(amount)**: 사용자가 입력한 실제 비용 금액 (원 단위, 시스템이 계산하지 않음)
- **메모(memo)**: 비용에 대한 자유 형식 메모 (선택, 선택 필드 — null 허용)
- **내부 ID**: Long 타입의 자동증가 기본키 (`GENERATED ALWAYS AS IDENTITY`, UUID를 사용하지 않음)

## 도메인 모델
### Cost 엔티티
| 필드명 | 타입 | 필수 | 설명 |
|--------|------|------|------|
| id | Long | Y | 내부 식별자 (자동증가) |
| subscriptionId | Long | Y | 연결된 청약 기록 참조 (외래 키, N:1 — D-C-001) |
| costType | String | Y | 비용종류 (자유 텍스트, Enum 아님 — 예: "청약수수료", "계좌개설비", D-C-002) |
| amount | Long | Y | 금액 (원 단위, 0 이상 — D-C-005) |
| incurredAt | LocalDateTime | Y | 발생일시 |
| memo | String | N | 메모 (선택 — null 허용) |
| status | String | Y | 상태 (ACTIVE/CANCELLED만 사용 — D-C-004) |
| deletedAt | Timestamp | N | 논리 삭제 시각 |
| createdAt | Timestamp | Y | 생성 시각 |
| updatedAt | Timestamp | Y | 수정 시각 |

> **참고**: Cost는 Subscription을 N:1로 참조한다(D-C-001). 하나의 청약에 여러 항목의 취득 비용이 있을 수 있으므로(예: 청약수수료 + 계좌관리비) 유니크 제약은 두지 않는다(D-C-002). 통화는 원화 단일 지원이며 별도 currency 필드를 두지 않는다(D-C-006). 비용 금액은 사용자 입력값 그대로 저장하며 세율/수수료 자동 계산 로직은 설계·구현하지 않는다(D-C-005).
>
> 참조 무결성: Subscription이 CANCELLED(논리 삭제)된 경우에도 연결된 Cost 기록은 그대로 유지된다. 비용 기록은 이미 발생한 사실이므로 보존된다.

## API 엔드포인트 목록
### 1. 비용 등록
- **POST** `/costs`
- **요청 본문**:
  ```json
  {
    "subscriptionId": 1,
    "costType": "청약수수료",
    "amount": 25000,
    "incurredAt": "2026-09-10T10:00:00",
    "memo": "OO증권 청약수수료"
  }
  ```
- **응답** (성공 — 201 Created):
  ```json
  {
    "id": 1,
    "subscriptionId": 1,
    "costType": "청약수수료",
    "amount": 25000,
    "incurredAt": "2026-09-10T10:00:00",
    "memo": "OO증권 청약수수료",
    "status": "ACTIVE",
    "deletedAt": null,
    "createdAt": "2026-09-21T10:00:00Z",
    "updatedAt": "2026-09-21T10:00:00Z"
  }
  ```
- **응답** (실패 - 필수값 누락/금액 0 미만): 400 Bad Request
- **응답** (실패 - 존재하지 않는 Subscription 참조): 404 Not Found

### 2. 비용 목록 조회
- **GET** `/costs`
- **쿼리 파라미터** (모두 빈 값이면 전체 조회, 둘 이상 사용 가능):
  - `subscriptionId` (Long, 선택) — 청약별 필터
  - `investorId` (Long, 선택) — 투자자별 필터 (Cost → Subscription → Investor 참조 경유 조회)
  - `investorName` (String, 선택) — 투자자명 필터 (Cost → Subscription → Investor 참조 경유 조회)
- **응답** (성공):
  ```json
  [
    {
      "id": 1,
      "subscriptionId": 1,
      "costType": "청약수수료",
      "amount": 25000,
      "incurredAt": "2026-09-10T10:00:00",
      "memo": "OO증권 청약수수료",
      "status": "ACTIVE",
      "deletedAt": null,
      "createdAt": "2026-09-21T10:00:00Z",
      "updatedAt": "2026-09-21T10:00:00Z"
    }
  ]
  ```
- 삭제된 기록(deletedAt이 설정된 CANCELLED)은 목록에서 제외한다.
- 정렬 순서: `incurredAt` 내림차순, 동일 incurredAt인 경우 `createdAt` 내림차순

### 3. 비용 상세 조회
- **GET** `/costs/{id}`
- **경로 파라미터**: id (Long 타입)
- **응답** (성공):
  ```json
  {
    "id": 1,
    "subscriptionId": 1,
    "costType": "청약수수료",
    "amount": 25000,
    "incurredAt": "2026-09-10T10:00:00",
    "memo": "OO증권 청약수수료",
    "status": "ACTIVE",
    "deletedAt": null,
    "createdAt": "2026-09-21T10:00:00Z",
    "updatedAt": "2026-09-21T10:00:00Z"
  }
  ```
- **응답** (실패 - 존재하지 않는 ID): 404 Not Found

### 4. 비용 수정
- **PATCH** `/costs/{id}`
- **경로 파라미터**: id (Long 타입)
- **요청 본문** (시점/필드 제약 없이 전체 허용 — D-C-003):
  ```json
  {
    "costType": "청약수수료",
    "amount": 30000,
    "incurredAt": "2026-09-10T11:00:00",
    "memo": "정정된 청약수수료"
  }
  ```
- **응답** (성공):
  ```json
  {
    "id": 1,
    "subscriptionId": 1,
    "costType": "청약수수료",
    "amount": 30000,
    "incurredAt": "2026-09-10T11:00:00",
    "memo": "정정된 청약수수료",
    "status": "ACTIVE",
    "deletedAt": null,
    "createdAt": "2026-09-21T10:00:00Z",
    "updatedAt": "2026-09-21T11:00:00Z"
  }
  ```
- **응답** (실패 - 유효성 오류/금액 0 미만): 400 Bad Request
- **응답** (실패 - 존재하지 않는 ID): 404 Not Found
- **응답** (실패 - 수정 대상이 CANCELLED 상태): 404 Not Found

### 5. 비용 취소 (논리 삭제)
- **DELETE** `/costs/{id}`
- **경로 파라미터**: id (Long 타입)
- **응답** (성공): 204 No Content — status가 CANCELLED로 변경되고 deletedAt이 설정됨. 물리 삭제(레코드 완전 제거)는 수행하지 않는다(D-C-004).
- **응답** (실패 - 존재하지 않는 ID): 404 Not Found

## Acceptance Criteria
**AC-001**: 비용 등록 성공
- Given: 존재하는 Subscription과 유효한 비용종류, 금액, 발생일시가 제공됨
- When: POST /costs 요청이 전송됨
- Then: 201 Created 응답이 반환되고, 새로운 Cost가 ACTIVE 상태로 생성됨

**AC-002**: 비용 등록 실패 (필수값 누락)
- Given: subscriptionId, costType, amount, incurredAt 중 하나 이상이 누락됨
- When: POST /costs 요청이 전송됨
- Then: 400 Bad Request 응답이 반환됨

**AC-003**: 비용 등록 실패 (금액 값 오류)
- Given: amount가 0 미만임
- When: POST /costs 요청이 전송됨
- Then: 400 Bad Request 응답이 반환됨 (단순 값 검증 — D-C-005)

**AC-004**: 비용 등록 실패 (Subscription 존재하지 않음)
- Given: 존재하지 않는 subscriptionId가 제공됨
- When: POST /costs 요청이 전송됨
- Then: 404 Not Found 응답이 반환됨

**AC-005**: 동일 청약에 비용 여러 건 등록 성공 (1:N)
- Given: 하나의 Subscription에 첫 번째 비용(청약수수료)이 등록됨
- When: 동일 Subscription에 두 번째 비용(계좌개설비)을 등록함
- Then: 201 Created 응답이 반환되고, 두 건의 Cost가 모두 ACTIVE 상태로 존재함 (유니크 제약 없음 — D-C-002)

**AC-006**: 비용 목록 조회 성공
- Given: 하나 이상의 비용 기록이 등록됨
- When: GET /costs 요청이 전송됨
- Then: 200 OK 응답이 반환되고, ACTIVE 비용 목록이 포함됨

**AC-007**: 청약별 목록 조회 성공
- Given: 여러 청약의 비용 기록이 존재함
- When: GET /costs?subscriptionId={id} 요청이 전송됨
- Then: 200 OK 응답이 반환되고, 해당 청약의 비용 기록만 포함됨

**AC-008**: 투자자별 목록 조회 성공
- Given: 여러 투자자의 비용 기록이 존재함
- When: GET /costs?investorId={id} 요청이 전송됨
- Then: 200 OK 응답이 반환되고, 해당 투자자의 Subscription에 연결된 비용 기록만 포함됨

**AC-009**: 비용 상세 조회 성공
- Given: 존재하는 비용 ID가 제공됨
- When: GET /costs/{id} 요청이 전송됨
- Then: 200 OK 응답이 반환되고, 해당 비용의 상세 정보가 반환됨

**AC-010**: 비용 상세 조회 실패 (존재하지 않는 ID)
- Given: 존재하지 않는 비용 ID가 제공됨
- When: GET /costs/{id} 요청이 전송됨
- Then: 404 Not Found 응답이 반환됨

**AC-011**: 비용 수정 성공
- Given: 존재하는 비용 ID와 유효한 수정 데이터가 제공됨
- When: PATCH /costs/{id} 요청이 전송됨
- Then: 200 OK 응답이 반환되고, 비용 정보가 업데이트되며 updatedAt이 갱신됨 (시점/필드 제약 없이 수정 허용 — D-C-003)

**AC-012**: 비용 수정 실패 (유효성 오류)
- Given: 존재하는 비용 ID와 유효하지 않은 데이터(금액 0 미만 등)가 제공됨
- When: PATCH /costs/{id} 요청이 전송됨
- Then: 400 Bad Request 응답이 반환됨

**AC-013**: 비용 수정 실패 (존재하지 않는 ID)
- Given: 존재하지 않는 비용 ID와 수정 데이터가 제공됨
- When: PATCH /costs/{id} 요청이 전송됨
- Then: 404 Not Found 응답이 반환됨

**AC-014**: 비용 취소 성공 (논리 삭제)
- Given: 존재하는 ACTIVE 비용 기록이 있음
- When: DELETE /costs/{id} 요청이 전송됨
- Then: 204 No Content 응답이 반환되고, status가 CANCELLED로 변경되며 deletedAt이 설정됨 (물리 삭제하지 않음 — D-C-004)

**AC-015**: 비용 취소 실패 (존재하지 않는 ID)
- Given: 존재하지 않는 비용 ID가 제공됨
- When: DELETE /costs/{id} 요청이 전송됨
- Then: 404 Not Found 응답이 반환됨

**AC-016**: 취소된 비용은 목록 조회에서 제외
- Given: CANCELLED 상태의 비용 기록이 존재함
- When: GET /costs 요청이 전송됨
- Then: CANCELLED 기록은 응답에 포함되지 않음

## 엣지케이스
- **매도 비용과의 중복 설계 금지 (D-C-001)**: 매도 시점의 수수료/세금/기타비용은 Epic 8의 `Sale.fee`/`Sale.tax`/`Sale.otherCost` 필드로 기록하므로 Epic 9에서 다시 설계하지 않는다. Cost는 청약/취득 단계 비용만 기록한다
- **비용종류 자유 텍스트 (D-C-002)**: costType은 Enum이 아닌 자유 텍스트로 저장하며 사전 정의된 값 목록에 제한받지 않는다. 수수료/세금 정책이 자주 바뀌고 증권사마다 다르므로 사용자 입력 그대로 기록한다
- **동일 청약 다건 비용 (D-C-002)**: 하나의 Subscription에 비용을 여러 건 등록할 수 있으며 유니크 제약을 두지 않는다. 비용 합산이 필요하면 Epic 10(수익 계산)이 취득 비용 합계를 계산한다
- **수정 제약 없음 (D-C-003)**: 사후 기록 서비스이므로 발생 시점과 무관하게 모든 필드를 자유롭게 수정할 수 있다. 별도의 변경 이력 테이블이나 감사 로그 테이블은 MVP 범위 밖이다(`created_at`/`updated_at`으로 충분)
- **논리 삭제만 허용 (D-C-004)**: 삭제는 status를 CANCELLED로 바꾸고 deletedAt을 설정하는 논리 삭제만 허용한다. 물리 삭제 금지
- **CANCELLED 비용 재활성화 불가**: 취소된 비용은 다시 ACTIVE로 되돌릴 수 없다. 정정이 필요하면 별도로 새 비용을 등록한다. 수정/취소 요청 시 CANCELLED 기록은 404로 처리한다
- **자동 계산 로직 금지 (D-C-005)**: amount는 `0 이상`만 검증하며, 세율/수수료를 추정하거나 자동 계산하는 로직은 어떤 형태로든 설계·구현하지 않는다. 사용자가 입력한 값을 그대로 저장한다
- **원화 단일 통화 (D-C-006)**: MVP 범위에서 원화만 다루므로 별도 currency 필드를 두지 않는다. 모든 금액 필드는 원 단위 Long이다
- **가격/금액 필드의 값 범위**: amount는 0 이상 Long으로 검증한다. 0값은 무료(비용 없음)로 허용된다
- **일시 필드**: incurredAt은 사후 기록 성격이므로 과거/현재/미래 시각 구분 없이 입력 허용
- **메모 필드**: memo는 선택 필드로 null을 허용한다
- **status 필드**는 ACTIVE 또는 CANCELLED만 허용 (다른 상태값은 400 — D-C-004). 파생 상태/다단계 상태 머신을 두지 않는다
- **청약 취소와 비용의 관계**: Subscription이 CANCELLED되더라도 연결된 Cost 기록은 유지된다 — 비용은 이미 발생한 사실이므로 보존된다
- **감사 로그 불필요 (D-C-003/D-C-004)**: 별도의 변경 이력 테이블, 감사 로그 테이블, 아카이브 전략은 MVP 범위 밖이다
- **동시성 제어 불필요**: 개인 사용자가 자신의 기록을 순차 입력하는 사후 기록 서비스이므로 낙관적/비관적 락, 분산 락을 도입하지 않는다

## 데이터 흐름
### Subscription ← Cost(N) 참조 관계도
```
Subscription (One) <---- (Many) Cost
                                  |
                                  +----> Profit (Epic 10, 아직 미구현)
```
- Cost는 Subscription을 N:1로 참조한다 (외래 키: subscriptionId — D-C-001)
- 하나의 청약에 여러 항목의 취득 비용이 있을 수 있으며 유니크 제약은 두지 않는다 (D-C-002)
- Subscription이 CANCELLED되어도 연결된 Cost 기록은 유지된다
- 매도 시점 비용(수수료/세금/기타비용)은 이 도메인이 아니라 Epic 8의 Sale 엔티티가 담당한다 (D-C-001)

### Cost → Profit 관계 (Epic 10 — 아직 미구현)
- 취득 관련 비용의 합계는 수익 계산(Epic 10, Profit)의 차감 항목으로 사용될 예정이다: `매도금액 − 취득 관련 비용 − 매도 수수료 − 세금 − 기타 비용 = 순수익`
- Epic 10 구현 시 Epic 9의 Cost(ACTIVE)와 Epic 8의 Sale(fee/tax/otherCost)을 함께 조회해 계산하는 흐름이 추가될 예정이다
- Cost 도메인 자체에는 수익 계산 로직을 두지 않는다 (D-C-005)