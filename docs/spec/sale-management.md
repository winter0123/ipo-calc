# 분할 매도 기록 기능 명세

## 개요
이미 증권사를 통해 완료된 매도 결과를 사용자가 사후에 수동으로 입력·기록·조회하는 기능을 제공한다.
실시간 매도 처리(증권사 API 연동, 실시간 시세 반영)가 아니며, 시스템 내에서 매도 주문·체결 워크플로를 처리하지 않는다.
매도 기록은 "이미 완료된 매도 결과를 기록했다"는 사실 자체이므로 사실 기록의 성격을 가지며, 수익 계산 등 파생 로직은 포함하지 않는다(D-SL-007).
상태 전이 머신을 갖지 않으며, 상태값은 ACTIVE/CANCELLED만 사용한다(D-SL-001).

## 용어 정의
- **Sale**: 배정된 주식을 매도한 기록 엔티티 (매도 사실 기록, 수익 계산 로직 포함하지 않음 — D-SL-007)
- **Allocation**: 매도 기록이 연결될 배정 결과 엔티티 (Epic 7, 구현 완료)
- **매도수량(quantity)**: 매도한 주식 수 (단위: 주)
- **매도단가(price)**: 주당 매도 가격 (단위: 원)
- **매도금액(amount)**: 매도 수량 × 매도단가 (사용자 입력값으로 저장, 시스템 계산값이 아님)
- **매도일시(saleDate)**: 매도가 완료된 일시
- **수수료(fee)**: 증권사 수수료 (선택, 선택 필드 — null 허용)
- **세금(tax)**: 매도 관련 세금 (선택, 선택 필드 — null 허용)
- **기타비용(otherCost)**: 기타 매도 관련 비용 (선택, 선택 필드 — null 허용)
- **내부 ID**: Long 타입의 자동증가 기본키 (`GENERATED ALWAYS AS IDENTITY`, UUID를 사용하지 않음)

## 도메인 모델
### Sale 엔티티
| 필드명 | 타입 | 필수 | 설명 |
|--------|------|------|------|
| id | Long | Y | 내부 식별자 (자동증가) |
| allocationId | Long | Y | 연결된 배정 기록 참조 (외래 키) |
| quantity | Long | Y | 매도수량 (주 단위, 0 초과) |
| price | Long | N | 매도단가 (원 단위, 선택 — 0 이상) |
| amount | Long | N | 매도금액 (원 단위, 선택 — 0 이상) |
| saleDate | LocalDateTime | Y | 매도일시 |
| fee | Long | N | 수수료 (원 단위, 선택 — null 허용, D-SL-007) |
| tax | Long | N | 세금 (원 단위, 선택 — null 허용, D-SL-007) |
| otherCost | Long | N | 기타비용 (원 단위, 선택 — null 허용, D-SL-007) |
| status | String | Y | 상태 (ACTIVE/CANCELLED만 사용 — D-SL-001) |
| deletedAt | Timestamp | N | 논리 삭제 시각 |
| createdAt | Timestamp | Y | 생성 시각 |
| updatedAt | Timestamp | Y | 수정 시각 |

> **참고**: Sale은 Allocation을 N:1로 참조한다(D-SL-006). 하나의 배정에 여러 번의 매도가 있을 수 있으므로 유니크 제약은 두지 않는다. 등록/수정 시 누적 매도수량 검증으로 데이터 무결성을 보장한다(D-SL-003). 상태값은 ACTIVE/CANCELLED만 사용하며, "일부매도/전량매도/보유중" 같은 파생 상태는 이 도메인에 두지 않는다 — 해당 값들은 Allocation의 배정수량과 Sale 누적수량을 비교해 계산하는 파생 뷰이며, Epic 11/12 조회·대시보드 API의 책임이다(D-SL-001).
>
> 참조 무결성: Allocation이 CANCELLED(논리 삭제)된 경우에도 연결된 Sale 기록은 그대로 유지된다. 매도 기록은 이미 발생한 사실이므로 보존된다.

## API 엔드포인트 목록
### 1. 매도 등록
- **POST** `/sales`
- **요청 본문**:
  ```json
  {
    "allocationId": 1,
    "quantity": 30,
    "price": 25000,
    "amount": 750000,
    "saleDate": "2026-10-15T14:30:00",
    "fee": 1500,
    "tax": 0,
    "otherCost": null
  }
  ```
- **응답** (성공 — 201 Created):
  ```json
  {
    "id": 1,
    "allocationId": 1,
    "quantity": 30,
    "price": 25000,
    "amount": 750000,
    "saleDate": "2026-10-15T14:30:00",
    "fee": 1500,
    "tax": 0,
    "otherCost": null,
    "status": "ACTIVE",
    "deletedAt": null,
    "createdAt": "2026-09-21T10:00:00Z",
    "updatedAt": "2026-09-21T10:00:00Z"
  }
  ```
- **응답** (실패 - 필수값 누락/매도수량 0 이하): 400 Bad Request
- **응답** (실패 - 존재하지 않는 Allocation 참조): 404 Not Found
- **응답** (실패 - 누적 매도수량이 배정수량 초과): 400 Bad Request (Σ(ACTIVE 매도수량) ≤ 배정수량 검증 — D-SL-003)

### 2. 매도 목록 조회
- **GET** `/sales`
- **쿼리 파라미터** (셋 다 빈 값이면 전체 조회, 둘 이상 사용 가능):
  - `allocationId` (Long, 선택) — 배정별 필터
  - `investorId` (Long, 선택) — 투자자별 필터 (Sale → Allocation → Subscription → Investor 참조 경유 조회)
  - `investorName` (String, 선택) — 투자자명 필터 (Sale → Allocation → Subscription → Investor 참조 경유 조회)
- **응답**:
  ```json
  [
    {
      "id": 1,
      "allocationId": 1,
      "quantity": 30,
      "price": 25000,
      "amount": 750000,
      "saleDate": "2026-10-15T14:30:00",
      "fee": 1500,
      "tax": 0,
      "otherCost": null,
      "status": "ACTIVE",
      "deletedAt": null,
      "createdAt": "2026-09-21T10:00:00Z",
      "updatedAt": "2026-09-21T10:00:00Z"
    }
  ]
  ```
- 삭제된 기록(deletedAt이 설정된 CANCELLED)은 목록에서 제외한다.
- 정렬 순서: `saleDate` 내림차순, 동일 saleDate인 경우 `createdAt` 내림차순 (D-SL-002).

### 3. 매도 상세 조회
- **GET** `/sales/{id}`
- **경로 파라미터**: id (Long 타입)
- **응답** (성공):
  ```json
  {
    "id": 1,
    "allocationId": 1,
    "quantity": 30,
    "price": 25000,
    "amount": 750000,
    "saleDate": "2026-10-15T14:30:00",
    "fee": 1500,
    "tax": 0,
    "otherCost": null,
    "status": "ACTIVE",
    "deletedAt": null,
    "createdAt": "2026-09-21T10:00:00Z",
    "updatedAt": "2026-09-21T10:00:00Z"
  }
  ```
- **응답** (실패 - 존재하지 않는 ID): 404 Not Found

### 4. 매도 수정
- **PATCH** `/sales/{id}`
- **경로 파라미터**: id (Long 타입)
- **요청 본문** (시점/필드 제약 없이 전체 허용 — D-SL-003):
  ```json
  {
    "quantity": 25,
    "price": 26000,
    "amount": 650000,
    "saleDate": "2026-10-15T15:00:00",
    "fee": 1300,
    "tax": 0,
    "otherCost": null
  }
  ```
- **응답** (성공):
  ```json
  {
    "id": 1,
    "allocationId": 1,
    "quantity": 25,
    "price": 26000,
    "amount": 650000,
    "saleDate": "2026-10-15T15:00:00",
    "fee": 1300,
    "tax": 0,
    "otherCost": null,
    "status": "ACTIVE",
    "deletedAt": null,
    "createdAt": "2026-09-21T10:00:00Z",
    "updatedAt": "2026-09-21T11:00:00Z"
  }
  ```
- **응답** (실패 - 유효성 오류/매도수량 0 이하): 400 Bad Request
- **응답** (실패 - 누적 매도수량이 배정수량 초과): 400 Bad Request (Σ(ACTIVE 매도수량) ≤ 배정수량 재검증 — D-SL-003)
- **응답** (실패 - 존재하지 않는 ID): 404 Not Found
- **응답** (실패 - 수정 대상이 CANCELLED 상태): 404 Not Found

### 5. 매도 취소 (논리 삭제)
- **DELETE** `/sales/{id}`
- **경로 파라미터**: id (Long 타입)
- **응답** (성공): 204 No Content — status가 CANCELLED로 변경되고 deletedAt이 설정됨. 물리 삭제(레코드 완전 제거)는 수행하지 않는다(D-SL-005).
- **응답** (실패 - 존재하지 않는 ID): 404 Not Found

## Acceptance Criteria
**AC-001**: 매도 등록 성공
- Given: 존재하는 Allocation과 유효한 매도수량, 매도일시가 제공됨
- When: POST /sales 요청이 전송됨
- Then: 201 Created 응답이 반환되고, 새로운 Sale이 ACTIVE 상태로 생성됨

**AC-002**: 매도 등록 실패 (필수값 누락)
- Given: allocationId, quantity, saleDate 중 하나 이상이 누락됨
- When: POST /sales 요청이 전송됨
- Then: 400 Bad Request 응답이 반환됨

**AC-003**: 매도 등록 실패 (매도수량 값 오류)
- Given: quantity가 0 이하임
- When: POST /sales 요청이 전송됨
- Then: 400 Bad Request 응답이 반환됨 (단순 값 검증)

**AC-004**: 매도 등록 실패 (Allocation 존재하지 않음)
- Given: 존재하지 않는 allocationId가 제공됨
- When: POST /sales 요청이 전송됨
- Then: 404 Not Found 응답이 반환됨

**AC-005**: 매도 등록 실패 (누적 매도수량 배정수량 초과)
- Given: 해당 Allocation의 기존 ACTIVE 매도수량 합계 + 요청 매도수량 > 배정수량
- When: POST /sales 요청이 전송됨
- Then: 400 Bad Request 응답이 반환되고, 데이터 무결성이 유지됨 (D-SL-003)

**AC-006**: 분할 매도 등록 성공 (여러 건 등록)
- Given: 배정수량 100인 Allocation에 대해 첫 번째 매도 30건이 등록됨
- When: 동일 Allocation에 두 번째 매도 40건을 등록함
- Then: 201 Created 응답이 반환되고, 두 건의 Sale이 모두 ACTIVE 상태로 존재하며, 누적 매도수량(70) ≤ 배정수량(100)이 만족됨 (D-SL-006)

**AC-007**: 매도 목록 조회 성공
- Given: 하나 이상의 매도 기록이 등록됨
- When: GET /sales 요청이 전송됨
- Then: 200 OK 응답이 반환되고, ACTIVE 매도 목록이 포함됨

**AC-008**: 배정별 목록 조회 성공
- Given: 여러 배정의 매도 기록이 존재함
- When: GET /sales?allocationId={id} 요청이 전송됨
- Then: 200 OK 응답이 반환되고, 해당 배정의 매도 기록만 포함됨

**AC-009**: 투자자별 목록 조회 성공
- Given: 여러 투자자의 매도 기록이 존재함
- When: GET /sales?investorId={id} 요청이 전송됨
- Then: 200 OK 응답이 반환되고, 해당 투자자의 Allocation에 연결된 매도 기록만 포함됨

**AC-010**: 매도 상세 조회 성공
- Given: 존재하는 매도 ID가 제공됨
- When: GET /sales/{id} 요청이 전송됨
- Then: 200 OK 응답이 반환되고, 해당 매도의 상세 정보가 반환됨

**AC-011**: 매도 상세 조회 실패 (존재하지 않는 ID)
- Given: 존재하지 않는 매도 ID가 제공됨
- When: GET /sales/{id} 요청이 전송됨
- Then: 404 Not Found 응답이 반환됨

**AC-012**: 매도 수정 성공
- Given: 존재하는 매도 ID와 유효한 수정 데이터가 제공됨
- When: PATCH /sales/{id} 요청이 전송됨
- Then: 200 OK 응답이 반환되고, 매도 정보가 업데이트되며 updatedAt이 갱신됨 (시점/필드 제약 없이 수정 허용 — D-SL-003)

**AC-013**: 매도 수정 실패 (유효성 오류)
- Given: 존재하는 매도 ID와 유효하지 않은 데이터(매도수량 0 이하 등)가 제공됨
- When: PATCH /sales/{id} 요청이 전송됨
- Then: 400 Bad Request 응답이 반환됨

**AC-014**: 매도 수정 실패 (존재하지 않는 ID)
- Given: 존재하지 않는 매도 ID와 수정 데이터가 제공됨
- When: PATCH /sales/{id} 요청이 전송됨
- Then: 404 Not Found 응답이 반환됨

**AC-015**: 매도 수정 실패 (누적 매도수량 재검증)
- Given: 매도수량 수정 후 해당 Allocation의 Σ(ACTIVE 매도수량) > 배정수량이 됨
- When: PATCH /sales/{id} 요청이 전송됨
- Then: 400 Bad Request 응답이 반환되고 데이터 무결성이 유지됨 (D-SL-003)

**AC-016**: 매도 취소 성공 (논리 삭제)
- Given: 존재하는 ACTIVE 매도 기록이 있음
- When: DELETE /sales/{id} 요청이 전송됨
- Then: 204 No Content 응답이 반환되고, status가 CANCELLED로 변경되며 deletedAt이 설정됨 (물리 삭제하지 않음 — D-SL-005)

**AC-017**: 매도 취소 실패 (존재하지 않는 ID)
- Given: 존재하지 않는 매도 ID가 제공됨
- When: DELETE /sales/{id} 요청이 전송됨
- Then: 404 Not Found 응답이 반환됨

**AC-018**: 취소된 매도는 목록 조회에서 제외
- Given: CANCELLED 상태의 매도 기록이 존재함
- When: GET /sales 요청이 전송됨
- Then: CANCELLED 기록은 응답에 포함되지 않음

## 엣지케이스
- **누적 매도수량 검증 (D-SL-003)**: 등록/수정 시 Service 계층에서 표준 트랜잭션 경계 안에 "Σ(ACTIVE 매도수량) ≤ 배정수량"을 검증한다. 개인 사용자가 자신의 기록을 순차 입력하는 사후 기록 서비스이므로 동시성 제어(락 등)는 불필요하다(D-SL-004)
- **분할 매도**: 하나의 배정에 여러 건의 매도 기록이 등록될 수 있다(D-SL-006). 매도 순서는 별도 순번 필드 없이 `saleDate` + `createdAt` 정렬로 처리한다(D-SL-002)
- **매도 수정 시 재검증 (D-SL-003)**: 매도수량을 포함한 모든 필드를 수정할 수 있다. 수정 후에도 Σ(ACTIVE 매도수량) ≤ 배정수량 불변식이 항상 만족되는지 재검증한다. 이미 취소된 매도(CANCELLED)는 조회에서 제외되므로 수정/취소 요청 시 404로 처리한다
- **CANCELLED 매도 재활성화 불가**: 취소된 매도는 다시 ACTIVE로 되돌릴 수 없다. 정정이 필요하면 별도로 새 매도를 등록한다. 다른 금융 기록 엔티티와 동일한 원칙이다
- **수수료/세금/기타비용 (D-SL-007)**: fee, tax, otherCost는 선택 필드로 null을 허용한다. 사용자가 입력한 값을 그대로 저장하며, 이 값들로부터 수익을 계산하는 로직은 Epic 9(비용 기록)/Epic 10(수익 계산)의 책임이다. Sale 도메인에서는 이 값을 사실 기록으로만 저장한다
- **매도금액(amount)**: quantity × price로 계산되는 값이지만 사용자가 직접 입력하는 필드로 간주한다. 시스템에서 자동 계산하지 않는다(증권사에서 받은 실제 체결금액을 기록하는 것이므로)
- **배정 취소와 매도의 관계**: Allocation이 CANCELLED되더라도 연결된 Sale 기록은 유지된다 — 매도는 이미 발생한 사실이므로 보존된다
- **가격/금액 필드의 값 범위**: price, amount는 0 이상 Long으로 검증한다. 0값은 무료 매도(0원)로 허용된다
- **일시 필드**: saleDate는 사후 기록 성격이므로 과거/현재/미래 시각 구분 없이 입력 허용
- **status 필드**는 ACTIVE 또는 CANCELLED만 허용 (다른 상태값은 400 — D-SL-001). "일부매도/전량매도/보유중" 같은 파생 상태는 이 도메인에 두지 않으며, Epic 11/12 조회 API에서 Allocation과 Sale을 함께 조회해 계산하는 파생 뷰로 처리한다
- **감사 로그 불필요 (D-SL-005)**: 별도의 변경 이력 테이블, 감사 로그 테이블, 아카이브 전략은 MVP 범위 밖이다. `created_at`/`updated_at` 감사 필드로 충분하다
- **동시성 제어 불필요 (D-SL-004)**: 개인 사용자가 자신의 기록을 순차 입력하는 서비스이므로 낙관적/비관적 락, 분산 락을 도입하지 않는다. 등록/수정 시 Service 계층에서 표준 트랜잭션 경계 안에 누적 매도수량 검증을 수행하는 것으로 충분하다

## 데이터 흐름
### Allocation ← Sale(N) 참조 관계도
```
Subscription (One) <---- (1:1) Allocation ----> (Many) Sale
                                                     |
                                                     +----> Cost/Profit (Epic 9/10, 아직 미구현)
```
- Sale은 Allocation을 N:1로 참조한다 (외래 키: allocationId — D-SL-006)
- 하나의 배정에 여러 건의 매도가 있을 수 있으며 유니크 제약은 두지 않는다
- 등록/수정 시 누적 매도수량(Σ ACTIVE quantity) ≤ 배정수량(allocatedQuantity) 검증으로 데이터 무결성을 보장한다
- Allocation이 CANCELLED되어도 연결된 Sale 기록은 유지된다

### Sale → Cost/Profit 관계 (Epic 9/10 — 아직 미구현)
- 매도 기록(Sale)에서 발생하는 비용(Cost)과 수익(Profit) 계산은 Epic 9/10에서 별도 엔티티로 설계 예정이다
- Sale의 fee, tax, otherCost 필드는 사실 기록으로 저장되며, 수익 계산 로직은 Sale 도메인에 두지 않는다(D-SL-007)
- Epic 9/10 구현 시 Sale 엔티티를 참조해 비용/수익을 계산하는 흐름이 추가될 예정이다
