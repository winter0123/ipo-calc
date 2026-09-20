# 배정 결과 관리 기능 명세

## 개요
이미 증권사를 통해 확정된 배정 결과를 사용자가 사후에 수동으로 입력·기록·조회하는 기능을 제공한다.
실시간 배정 처리(순번·경쟁률 기반 실시간 배정)가 아니며, 시스템 내에서 배정 확정·취소 워크플로를 처리하지 않는다.
배정 기록은 "이미 확정된 배정 결과를 기록했다"는 사실 자체이므로 생성 시점에 이미 최종 상태이며, 상태 전이 머신을 갖지 않는다(D-A-001).
잔여수량(배정수량 - 총매도수량) 계산은 매도(Sale, Epic 8) 데이터에 의존하는 파생값으로 Epic 8/11에서 처리하며, 이 도메인에서 계산하지 않는다(D-A-003).

## 용어 정의
- **Allocation**: 배정 결과 기록 엔티티 (배정된 주식 수량과 그 부가 정보를 기록)
- **Subscription**: 배정 결과가 연결될 청약 기록 엔티티 (Epic 6, 구현 완료)
- **Sale**: 배정된 주식을 매도한 기록 엔티티 (Epic 8, 아직 미구현)
- **배정수량(allocatedQuantity)**: 해당 청약에 대해 배정받은 주식 수 (낙찰된 사실 기록)
- **배정방식메모(allocationMethod)**: 균등/비례/추첨 등 배정 방식을 증권사 표현 그대로 기록하는 자유 텍스트 (Enum 아님, D-A-004)
- **잔여수량**: 배정수량 - 총매도수량으로 계산되는 파생값 (Epic 8/11 범위, 이 도메인에서 계산하지 않음 — D-A-003)
- **내부 ID**: Long 타입의 자동증가 기본키 (`GENERATED ALWAYS AS IDENTITY`, UUID를 사용하지 않으며 기존 프로젝트 일관성 유지)

## 도메인 모델
### Allocation 엔티티
| 필드명 | 타입 | 필수 | 설명 |
|--------|------|------|------|
| id | Long | Y | 내부 식별자 (자동증가) |
| subscriptionId | Long | Y | 연결된 청약 기록 참조 (외래 키, **유니크 — 1:1 보장**) |
| allocatedQuantity | Long | Y | 배정수량 (주 단위, 0 이상) |
| allocatedAt | LocalDateTime | Y | 배정일시 |
| allocationMethod | String | N | 배정방식메모 (선택, 자유 텍스트 — Enum 아님, 예: "균등배정", "비례배정", "추첨") |
| allocationPrice | Long | N | 배정단가 (원 단위, 선택) |
| note | String | N | 비고 (선택) |
| status | String | Y | 상태 (ACTIVE/CANCELLED만 사용 — D-A-001) |
| deletedAt | Timestamp | N | 논리 삭제 시각 |
| createdAt | Timestamp | Y | 생성 시각 |
| updatedAt | Timestamp | Y | 수정 시각 |

> **참고**: Allocation은 Subscription과 **정확히 1:1** 관계다(D-A-002). 한 청약에 대한 ACTIVE 배정 결과는 하나만 존재하며, `subscription_id`의 ACTIVE 상태 기준 부분 유니크 제약으로 중복 배정 기록을 방지한다(부분 배정도 하나의 배정수량 값으로 표현하며, 여러 차례에 걸친 배정 이력 관리는 MVP 범위 밖). CANCELLED 기록이 있어도 재등록은 허용된다(Subscription과 동일 원칙). 상태값은 ACTIVE/CANCELLED만 사용하며 배정대기/배정완료 같은 워크플로 상태 머신을 도입하지 않는다(D-A-001). 잔여수량 계산 로직은 두지 않는다(D-A-003).
>
> 참조 무결성: Subscription이 CANCELLED(논리 삭제)된 경우에도 해당 Allocation 기록은 그대로 유지된다. 배정 기록은 청약이 취소되더라도 이미 발생한 사실이므로 보존된다.

## API 엔드포인트 목록
### 1. 배정 등록
- **POST** `/allocations`
- **요청 본문**:
  ```json
  {
    "subscriptionId": 1,
    "allocatedQuantity": 50,
    "allocatedAt": "2026-10-05T18:00:00",
    "allocationMethod": "균등배정 (선택)",
    "allocationPrice": 10000,
    "note": "비고 (선택)"
  }
  ```
- **응답** (성공 — 201 Created):
  ```json
  {
    "id": 1,
    "subscriptionId": 1,
    "allocatedQuantity": 50,
    "allocatedAt": "2026-10-05T18:00:00",
    "allocationMethod": "균등배정",
    "allocationPrice": 10000,
    "note": "비고",
    "status": "ACTIVE",
    "deletedAt": null,
    "createdAt": "2026-09-21T10:00:00Z",
    "updatedAt": "2026-09-21T10:00:00Z"
  }
  ```
- **응답** (실패 - 필수값 누락/배정수량 음수/배정수량이 청약수량 초과): 400 Bad Request
- **응답** (실패 - 동일 subscriptionId에 이미 배정 기록 존재): 409 Conflict (DB 유니크 제약이 1:1 중복을 방지)
- **응답** (실패 - 존재하지 않는 Subscription 참조): 404 Not Found

### 2. 배정 목록 조회
- **GET** `/allocations`
- **쿼리 파라미터** (둘 다 빈 값이면 전체 조회, 둘 다 사용 가능):
  - `subscriptionId` (Long, 선택) — 청약별 필터
  - `investorId` (Long, 선택) — 투자자별 필터 (Allocation → Subscription → Investor 참조 경유 조회)
- **응답**:
  ```json
  [
    {
      "id": 1,
      "subscriptionId": 1,
      "allocatedQuantity": 50,
      "allocatedAt": "2026-10-05T18:00:00",
      "allocationMethod": "균등배정",
      "allocationPrice": 10000,
      "note": "비고",
      "status": "ACTIVE",
      "deletedAt": null,
      "createdAt": "2026-09-21T10:00:00Z",
      "updatedAt": "2026-09-21T10:00:00Z"
    }
  ]
  ```
- 삭제된 기록(deletedAt이 설정된 CANCELLED)은 목록에서 제외한다.

### 3. 배정 상세 조회
- **GET** `/allocations/{id}`
- **경로 파라미터**: id (Long 타입)
- **응답** (성공):
  ```json
  {
    "id": 1,
    "subscriptionId": 1,
    "allocatedQuantity": 50,
    "allocatedAt": "2026-10-05T18:00:00",
    "allocationMethod": "균등배정",
    "allocationPrice": 10000,
    "note": "비고",
    "status": "ACTIVE",
    "deletedAt": null,
    "createdAt": "2026-09-21T10:00:00Z",
    "updatedAt": "2026-09-21T10:00:00Z"
  }
  ```
- **응답** (실패 - 존재하지 않는 ID): 404 Not Found

### 4. 배정 수정
- **PATCH** `/allocations/{id}`
- **경로 파라미터**: id (Long 타입)
- **요청 본문** (시점 제약 없이 수정 허용 — D-A-007):
  ```json
  {
    "allocatedQuantity": 40,
    "allocatedAt": "2026-10-05T19:00:00",
    "allocationMethod": "비례배정",
    "allocationPrice": 15000,
    "note": "수정된 비고"
  }
  ```
- **응답** (성공):
  ```json
  {
    "id": 1,
    "subscriptionId": 1,
    "allocatedQuantity": 40,
    "allocatedAt": "2026-10-05T19:00:00",
    "allocationMethod": "비례배정",
    "allocationPrice": 15000,
    "note": "수정된 비고",
    "status": "ACTIVE",
    "deletedAt": null,
    "createdAt": "2026-09-21T10:00:00Z",
    "updatedAt": "2026-09-21T11:00:00Z"
  }
  ```
- **응답** (실패 - 유효성 오류/배정수량이 청약수량 초과): 400 Bad Request
- **응답** (실패 - 존재하지 않는 ID): 404 Not Found
- **응답** (실패 - 매도(Sale) 기록이 연결된 배정을 매도수량 미만으로 축소): 400 Bad Request (세부 검증 로직은 Epic 8 구현 시 확정 — D-A-007)

### 5. 배정 취소 (논리 삭제)
- **DELETE** `/allocations/{id}`
- **경로 파라미터**: id (Long 타입)
- **응답** (성공): 204 No Content — status가 CANCELLED로 변경되고 deletedAt이 설정됨. 물리 삭제(레코드 완전 제거)는 수행하지 않는다.
- **응답** (실패 - 존재하지 않는 ID): 404 Not Found

## Acceptance Criteria
**AC-001**: 배정 등록 성공
- Given: 존재하는 Subscription과 유효한 배정수량, 배정일시가 제공됨
- When: POST /allocations 요청이 전송됨
- Then: 201 Created 응답이 반환되고, 새로운 Allocation이 ACTIVE 상태로 생성됨

**AC-002**: 배정 등록 실패 (필수값 누락)
- Given: subscriptionId, allocatedQuantity, allocatedAt 중 하나 이상이 누락됨
- When: POST /allocations 요청이 전송됨
- Then: 400 Bad Request 응답이 반환됨

**AC-003**: 배정 등록 실패 (배정수량 값 오류)
- Given: allocatedQuantity가 0 미만이거나 청약수량(subscription.quantity)을 초과함
- When: POST /allocations 요청이 전송됨
- Then: 400 Bad Request 응답이 반환됨 (단순 값 검증 — D-A-005)

**AC-004**: 배정 등록 실패 (Subscription 존재하지 않음)
- Given: 존재하지 않는 subscriptionId가 제공됨
- When: POST /allocations 요청이 전송됨
- Then: 404 Not Found 응답이 반환됨

**AC-005**: 배정 등록 실패 (중복 — 1:1 제약)
- Given: 해당 subscriptionId에 이미 ACTIVE 배정 기록이 존재함
- When: POST /allocations 요청이 전송됨
- Then: 409 Conflict 응답이 반환됨 (DB subscription_id 유니크 제약이 중복을 방지 — D-A-002)

**AC-006**: 취소된 배정이 있으면 재등록 성공 (Subscription과 동일 원칙)
- Given: 해당 subscriptionId에 CANCELLED 상태의 배정 기록이 존재함
- When: POST /allocations 요청이 전송됨
- Then: 201 Created 응답이 반환되고, 새 Allocation이 ACTIVE 상태로 생성됨 (1:1 제약은 ACTIVE 기록 기준이며, 사용자가 실수로 취소한 뒤 정정 입력하는 경우를 허용 — Subscription의 D-S-006/AC-006과 동일 원칙)

**AC-007**: 배정 목록 조회 성공
- Given: 하나 이상의 배정 기록이 등록됨
- When: GET /allocations 요청이 전송됨
- Then: 200 OK 응답이 반환되고, ACTIVE 배정 목록이 포함됨

**AC-008**: 청약별 목록 조회 성공
- Given: 여러 청약의 배정 기록이 존재함
- When: GET /allocations?subscriptionId={id} 요청이 전송됨
- Then: 200 OK 응답이 반환되고, 해당 청약의 배정 기록만 포함됨 (1:1이므로 최대 1건)

**AC-009**: 투자자별 목록 조회 성공
- Given: 여러 투자자의 배정 기록이 존재함
- When: GET /allocations?investorId={id} 요청이 전송됨
- Then: 200 OK 응답이 반환되고, 해당 투자자의 Subscription에 연결된 배정 기록만 포함됨

**AC-010**: 배정 상세 조회 성공
- Given: 존재하는 배정 ID가 제공됨
- When: GET /allocations/{id} 요청이 전송됨
- Then: 200 OK 응답이 반환되고, 해당 배정의 상세 정보가 반환됨

**AC-011**: 배정 상세 조회 실패 (존재하지 않는 ID)
- Given: 존재하지 않는 배정 ID가 제공됨
- When: GET /allocations/{id} 요청이 전송됨
- Then: 404 Not Found 응답이 반환됨

**AC-012**: 배정 수정 성공
- Given: 존재하는 배정 ID와 유효한 수정 데이터가 제공됨
- When: PATCH /allocations/{id} 요청이 전송됨
- Then: 200 OK 응답이 반환되고, 배정 정보가 업데이트되며 updatedAt이 갱신됨 (시점 제약 없이 수정 허용 — D-A-007)

**AC-013**: 배정 수정 실패 (유효성 오류)
- Given: 존재하는 배정 ID와 유효하지 않은 데이터(배정수량 0 미만, 청약수량 초과 등)가 제공됨
- When: PATCH /allocations/{id} 요청이 전송됨
- Then: 400 Bad Request 응답이 반환됨

**AC-014**: 배정 수정 실패 (존재하지 않는 ID)
- Given: 존재하지 않는 배정 ID와 수정 데이터가 제공됨
- When: PATCH /allocations/{id} 요청이 전송됨
- Then: 404 Not Found 응답이 반환됨

**AC-015**: 배정 수정 실패 (매도 연결 시 수량 축소 제한 — Epic 8에서 확정)
- Given: 해당 배정에 매도(Sale) 기록이 연결되어 있고, 신규 배정수량이 연결된 매도수량 합계 미만으로 요청됨
- When: PATCH /allocations/{id} 요청이 전송됨
- Then: 400 Bad Request 응답이 반환되고 참조 무결성이 유지됨 (연결된 매도수량 이상이거나 매도 미연결 시에는 수정 허용, 세부 검증 로직은 Epic 8 구현 시 확정 — D-A-007)

**AC-016**: 배정 취소 성공 (논리 삭제)
- Given: 존재하는 ACTIVE 배정 기록이 있음
- When: DELETE /allocations/{id} 요청이 전송됨
- Then: 204 No Content 응답이 반환되고, status가 CANCELLED로 변경되며 deletedAt이 설정됨 (물리 삭제하지 않음)

**AC-017**: 배정 취소 실패 (존재하지 않는 ID)
- Given: 존재하지 않는 배정 ID가 제공됨
- When: DELETE /allocations/{id} 요청이 전송됨
- Then: 404 Not Found 응답이 반환됨

**AC-018**: 취소된 배정은 목록 조회에서 제외
- Given: CANCELLED 상태의 배정 기록이 존재함
- When: GET /allocations 요청이 전송됨
- Then: CANCELLED 기록은 응답에 포함되지 않음

## 엣지케이스
- **중복 배정 방지(1:1)**: `subscription_id`의 **ACTIVE 상태 기준 부분 유니크 제약**으로 처리한다(D-A-002). CANCELLED 기록이 있어도 재등록은 허용된다 — Subscription의 취소 후 재등록 허용 원칙과 동일하게, 사용자가 실수로 취소한 뒤 정정 입력할 수 있어야 하기 때문이다
- **동시성 제어 불필요 (D-A-006)**: 개인 사용자가 자신의 기록을 순차 입력하는 사후 기록 서비스이므로 낙관적/비관적 락, 분산 락을 도입하지 않는다. 표준 트랜잭션 경계 + DB subscription_id 유니크 제약으로 충분하다 (실시간 동시성이 아닌 단순 값 검증 — D-A-005)
- **잔여수량 계산 금지 (D-A-003)**: 배정수량 - 총매도수량 계산은 Epic 8(Sale) 또는 Epic 11(조회 API)에서 Allocation과 Sale을 함께 조회해 처리한다. Allocation은 배정수량(사실 기록)만 저장한다
- **배정방식(allocationMethod)**: 자유 텍스트 선택 필드다. Enum 제약을 두지 않으며 null 허용 (D-A-004, 기본 빈 문자열 사용 금지 — null 그대로 저장)
- **수정 시점 제약 없음 (D-A-007)**: "배정 확정 후 취소/수정 제한" 같은 실시간 제약은 적용하지 않는다. 단, 이미 매도(Sale) 기록이 연결된 배정을 매도수량 미만으로 축소하는 수정은 참조 무결성 위반으로 차단한다 (Epic 8 구현 시 확정)
- **참조 무결성**: Subscription이 취소(논리 삭제)되어도 연결된 Allocation 기록은 유지된다 — 배정은 이미 발생한 사실이므로
- **배정수량**은 0 이상, 청약수량 이하의 Long으로만 검증 (배정수량 ≥ 0, 배정수량 ≤ 청약수량 — D-A-005)
- **배정일시(allocatedAt)**는 사후 기록 성격이므로 과거/현재/미래 시각 구분 없이 입력 허용
- **status 필드**는 ACTIVE 또는 CANCELLED만 허용 (다른 상태값은 400 — D-A-001). 배정대기/배정완료 같은 워크플로 상태 전이 머신은 설계하지 않는다
- 이미 CANCELLED된 배정을 다시 수정/취소하는 요청은 404로 처리 (조회 API에서 노출되지 않으므로)
- 배정단가(allocationPrice)는 0 이상 Long으로 검증하며, 0값은 무료 배정(0원)으로 허용
- 메모/비고/allocationMethod가 매우 긴 경우(길이 상한 초과)는 상한을 아키텍처 단계에서 확정하여 400 처리

## 데이터 흐름
### Subscription ← Allocation 참조 관계도 (Epic 7)
```
Subscription (One) <---- (1:1) Allocation ----> (Many) Sale (Epic 8, 아직 미구현)
```
- Allocation은 정확히 하나의 Subscription을 참조한다 (외래 키 제약 + subscription_id 유니크 제약으로 1:1 보장 — D-A-002)
- 한 Subscription은 최대 하나의 Allocation을 가진다 (배정 이력의 다중화 없음, MVP 범위 기준)
- 배정 결과는 Subscription에 필드로 두지 않고 별도 Allocation 엔티티로 분리했으므로, Epic 6 구현 완료분(Subscription)을 변경할 필요가 없다 (도메인 분리 원칙)
- Subscription → Investor / SecuritiesAccount / IPO 참조는 Epic 6에서 이미 구성되어 있으며, Allocation의 투자자별(investorId) 목록 조회는 이 경유 참조를 이용한다

### Allocation → Sale 관계 (Epic 8 — 아직 미구현)
- 매도(Sale) 기록은 배정 결과를 기준으로 생성되며, Allocation을 참조(N:1)하는 형태로 설계 예정이다. **이번 Epic에서 구현하지 않는다**
- 잔여수량(배정수량 - 총매도수량)은 Allocation과 Sale을 함께 조회해 계산하는 파생값으로, Epic 8(Sale) 또는 Epic 11(조회 API)에서 처리한다 — Allocation 도메인에 계산 로직을 두지 않는다 (D-A-003)
- Epic 8에서 매도 기록이 연결된 배정의 수량 축소 수정이 제한되므로, 현재 Epic 7에서는 이 검증을 위한 연동 지점만 남겨 두고 세부 로직은 Epic 8에서 확정한다 (D-A-007)