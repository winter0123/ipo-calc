# 청약 기록 관리 기능 명세

## 개요
이미 증권사를 통해 완료된 청약 결과를 사용자가 사후에 수동으로 입력·기록·조회할 수 있는 기능을 제공한다.
실시간 청약 접수·배정 처리 시스템이 아니며, 시스템 내에서 주문 접수/체결 프로세스를 처리하지 않는다.
배정(Allocation) 및 환불 금액 계산은 Epic 7(별도 도메인)에서 다룬다.

## 용어 정의
- **Subscription**: 청약 기록 엔티티 (사용자가 직접 입력한 "청약했다"는 사실 기록)
- **Investor**: 청약한 투자자 기준정보 엔티티
- **SecuritiesAccount**: 청약에 사용한 증권 계좌 엔티티
- **IPO**: 청약 대상 공모주 기준정보 엔티티
- **배정(Allocation)**: Epic 7의 별도 도메인. 청약 결과로 몇 주가 배정되었는지를 기록하는 엔티티 (아직 미구현)
- **내부 ID**: Long 타입의 자동증가 기본키 (UUID를 사용하지 않으며 기존 프로젝트 일관성 유지)

## 도메인 모델
### Subscription 엔티티
| 필드명 | 타입 | 필수 | 설명 |
|--------|------|------|------|
| id | Long | Y | 내부 식별자 (자동증가) |
| investorId | Long | Y | 투자자 기준정보 참조 (외래 키) |
| securitiesAccountId | Long | Y | 증권 계좌 참조 (외래 키) |
| ipoId | Long | Y | 청약한 IPO 기준정보 참조 (외래 키) |
| quantity | Long | Y | 청약수량 (주 단위, 0 초과) |
| depositAmount | Long | Y | 증거금 납입액 (원 단위, 0 이상 — 사용자가 입력한 사실 기록) |
| subscribedAt | LocalDateTime | Y | 청약일시 |
| memo | String | N | 메모 (선택, 예: "비대면 청약", "미래에셋 앱") |
| status | String | Y | 상태 (ACTIVE/CANCELLED) |
| deletedAt | Timestamp | N | 논리 삭제 시각 |
| createdAt | Timestamp | Y | 생성 시각 |
| updatedAt | Timestamp | Y | 수정 시각 |

> **참고**: 배정 결과 필드/상태(배정대기/배정완료)는 Subscription에 포함하지 않는다. 배정 관련 정보는 Subscription을 참조하는 Epic 7의 Allocation 엔티티에서 다룬다(D-S-001). 환불 금액 계산 로직도 Subscription에 두지 않는다(D-S-002). 상태값은 ACTIVE/CANCELLED만 사용하며 실시간 워크플로 상태 머신을 도입하지 않는다(D-S-003).

## API 엔드포인트 목록
### 1. 청약 등록
- **POST** `/subscriptions`
- **요청 본문**:
  ```json
  {
    "investorId": 1,
    "securitiesAccountId": 2,
    "ipoId": 3,
    "quantity": 100,
    "depositAmount": 1000000,
    "subscribedAt": "2026-10-01T09:00:00",
    "memo": "비대면 청약 (선택)"
  }
  ```
- **응답** (성공):
  ```json
  {
    "id": 1,
    "investorId": 1,
    "securitiesAccountId": 2,
    "ipoId": 3,
    "quantity": 100,
    "depositAmount": 1000000,
    "subscribedAt": "2026-10-01T09:00:00",
    "memo": "비대면 청약",
    "status": "ACTIVE",
    "createdAt": "2026-09-20T10:00:00Z",
    "updatedAt": "2026-09-20T10:00:00Z"
  }
  ```
- **응답** (실패 - 필수값 누락/수량 0 이하/증거금 음수): 400 Bad Request
- **응답** (실패 - 동일 투자자·계좌·IPO 조합의 ACTIVE 기록 중복): 409 Conflict
- **응답** (실패 - 존재하지 않는 Investor/SecuritiesAccount/IPO 참조): 404 Not Found

### 2. 청약 목록 조회
- **GET** `/subscriptions`
- **쿼리 파라미터** (둘 다 빈 값이면 전체 조회, 둘 다 사용 가능):
  - `investorId` (Long, 선택) — 투자자별 필터
  - `securitiesAccountId` (Long, 선택) — 계좌별 필터
- **응답**:
  ```json
  [
    {
      "id": 1,
      "investorId": 1,
      "securitiesAccountId": 2,
      "ipoId": 3,
      "quantity": 100,
      "depositAmount": 1000000,
      "subscribedAt": "2026-10-01T09:00:00",
      "memo": "비대면 청약",
      "status": "ACTIVE",
      "createdAt": "2026-09-20T10:00:00Z",
      "updatedAt": "2026-09-20T10:00:00Z"
    }
  ]
  ```
- 삭제된 기록(deletedAt이 설정된 CANCELLED)은 목록에서 제외한다.

### 3. 청약 상세 조회
- **GET** `/subscriptions/{id}`
- **경로 파라미터**: id (Long 타입)
- **응답** (성공):
  ```json
  {
    "id": 1,
    "investorId": 1,
    "securitiesAccountId": 2,
    "ipoId": 3,
    "quantity": 100,
    "depositAmount": 1000000,
    "subscribedAt": "2026-10-01T09:00:00",
    "memo": "비대면 청약",
    "status": "ACTIVE",
    "createdAt": "2026-09-20T10:00:00Z",
    "updatedAt": "2026-09-20T10:00:00Z"
  }
  ```
- **응답** (실패 - 존재하지 않는 ID): 404 Not Found

### 4. 청약 수정
- **PATCH** `/subscriptions/{id}`
- **경로 파라미터**: id (Long 타입)
- **요청 본문** (시점 제약 없이 전체 필드 수정 허용):
  ```json
  {
    "investorId": 1,
    "securitiesAccountId": 2,
    "ipoId": 3,
    "quantity": 50,
    "depositAmount": 500000,
    "subscribedAt": "2026-10-01T09:30:00",
    "memo": "수정된 메모"
  }
  ```
- **응답** (성공):
  ```json
  {
    "id": 1,
    "investorId": 1,
    "securitiesAccountId": 2,
    "ipoId": 3,
    "quantity": 50,
    "depositAmount": 500000,
    "subscribedAt": "2026-10-01T09:30:00",
    "memo": "수정된 메모",
    "status": "ACTIVE",
    "createdAt": "2026-09-20T10:00:00Z",
    "updatedAt": "2026-09-20T11:00:00Z"
  }
  ```
- **응답** (실패 - 유효성 오류): 400 Bad Request
- **응답** (실패 - 동일 투자자·계좌·IPO 조합의 다른 ACTIVE 기록과 중복): 409 Conflict
- **응답** (실패 - 존재하지 않는 ID): 404 Not Found
- **응답** (실패 - 배정 기록(Allocation)이 연결되어 있고 청약수량을 배정 수량 미만으로 축소): 400 Bad Request (세부 검증 로직은 Epic 7 구현 시 함께 확정)

### 5. 청약 취소 (논리 삭제)
- **DELETE** `/subscriptions/{id}`
- **경로 파라미터**: id (Long 타입)
- **응답** (성공): 204 No Content — status가 CANCELLED로 변경되고 deletedAt이 설정됨. 물리 삭제(레코드 완전 제거)는 수행하지 않는다.
- **응답** (실패 - 존재하지 않는 ID): 404 Not Found

## Acceptance Criteria
**AC-001**: 청약 등록 성공
- Given: 유효한 투자자, 계좌, IPO 참조와 수량, 증거금, 청약일시가 제공됨
- When: POST /subscriptions 요청이 전송됨
- Then: 201 Created 응답이 반환되고, 새로운 Subscription이 ACTIVE 상태로 생성됨

**AC-002**: 청약 등록 실패 (필수값 누락)
- Given: 투자자, 계좌, IPO, 수량, 증거금, 청약일시 중 하나 이상이 누락됨
- When: POST /subscriptions 요청이 전송됨
- Then: 400 Bad Request 응답이 반환됨

**AC-003**: 청약 등록 실패 (수량/증거금 값 오류)
- Given: quantity가 0 이하이거나 depositAmount가 음수로 제공됨
- When: POST /subscriptions 요청이 전송됨
- Then: 400 Bad Request 응답이 반환됨

**AC-004**: 청약 등록 실패 (기준정보 존재하지 않음)
- Given: 존재하지 않는 investorId, securitiesAccountId, ipoId 중 하나가 제공됨
- When: POST /subscriptions 요청이 전송됨
- Then: 404 Not Found 응답이 반환됨

**AC-005**: 청약 등록 실패 (중복)
- Given: 동일한 (investorId, securitiesAccountId, ipoId) 조합의 ACTIVE 상태 청약 기록이 이미 존재함
- When: POST /subscriptions 요청이 전송됨
- Then: 409 Conflict 응답이 반환됨 (DB 부분 유니크 인덱스가 중복을 방지)

**AC-006**: 동일 조합의 취소(CANCELLED) 기록이 있어도 재등록 성공
- Given: 동일한 (investorId, securitiesAccountId, ipoId) 조합의 기록이 CANCELLED 상태로 존재함
- When: POST /subscriptions 요청이 전송됨
- Then: 201 Created 응답이 반환되고, 새 Subscription이 ACTIVE 상태로 생성됨 (중복 검사는 ACTIVE 기록 기준)

**AC-007**: 청약 목록 조회 성공
- Given: 하나 이상의 청약 기록이 등록됨
- When: GET /subscriptions 요청이 전송됨
- Then: 200 OK 응답이 반환되고, ACTIVE 청약 목록이 포함됨

**AC-008**: 투자자별 목록 조회 성공
- Given: 여러 투자자의 청약 기록이 존재함
- When: GET /subscriptions?investorId={id} 요청이 전송됨
- Then: 200 OK 응답이 반환되고, 해당 투자자의 청약 기록만 포함됨

**AC-009**: 계좌별 목록 조회 성공
- Given: 여러 계좌의 청약 기록이 존재함
- When: GET /subscriptions?securitiesAccountId={id} 요청이 전송됨
- Then: 200 OK 응답이 반환되고, 해당 계좌의 청약 기록만 포함됨

**AC-010**: 청약 상세 조회 성공
- Given: 존재하는 청약 ID가 제공됨
- When: GET /subscriptions/{id} 요청이 전송됨
- Then: 200 OK 응답이 반환되고, 해당 청약의 상세 정보가 반환됨

**AC-011**: 청약 상세 조회 실패 (존재하지 않는 ID)
- Given: 존재하지 않는 청약 ID가 제공됨
- When: GET /subscriptions/{id} 요청이 전송됨
- Then: 404 Not Found 응답이 반환됨

**AC-012**: 청약 수정 성공
- Given: 존재하는 청약 ID와 유효한 수정 데이터가 제공됨
- When: PATCH /subscriptions/{id} 요청이 전송됨
- Then: 200 OK 응답이 반환되고, 청약 정보가 업데이트되며 updatedAt이 갱신됨

**AC-013**: 청약 수정 실패 (유효성 오류)
- Given: 존재하는 청약 ID와 유효하지 않은 데이터(수량 0 이하, 증거금 음수 등)가 제공됨
- When: PATCH /subscriptions/{id} 요청이 전송됨
- Then: 400 Bad Request 응답이 반환됨

**AC-014**: 청약 수정 실패 (중복)
- Given: 다른 ACTIVE 청약의 (investorId, securitiesAccountId, ipoId) 조합과 중복되도록 수정 시도함
- When: PATCH /subscriptions/{id} 요청이 전송됨
- Then: 409 Conflict 응답이 반환됨

**AC-015**: 청약 수정 실패 (존재하지 않는 ID)
- Given: 존재하지 않는 청약 ID와 수정 데이터가 제공됨
- When: PATCH /subscriptions/{id} 요청이 전송됨
- Then: 404 Not Found 응답이 반환됨

**AC-016**: 청약 취소 성공
- Given: 존재하는 ACTIVE 청약 기록이 있음
- When: DELETE /subscriptions/{id} 요청이 전송됨
- Then: 204 No Content 응답이 반환되고, status가 CANCELLED로 변경되며 deletedAt이 설정됨 (물리 삭제하지 않음)

**AC-017**: 청약 취소 실패 (존재하지 않는 ID)
- Given: 존재하지 않는 청약 ID가 제공됨
- When: DELETE /subscriptions/{id} 요청이 전송됨
- Then: 404 Not Found 응답이 반환됨

**AC-018**: 취소된 청약은 목록 조회에서 제외
- Given: CANCELLED 상태의 청약 기록이 존재함
- When: GET /subscriptions 요청이 전송됨
- Then: CANCELLED 기록은 응답에 포함되지 않음

**AC-019**: 배정 연결 시 수량 축소 제한 (Epic 7 구현 시 확정)
- Given: 해당 청약에 배정 기록(Allocation)이 연결되어 있고, 신규 수량이 배정 수량 미만으로 요청됨
- When: PATCH /subscriptions/{id} 요청이 전송됨
- Then: 400 Bad Request 응답이 반환되고 참조 무결성 유지됨 (배정 기준 수량 이상이거나 미연결 시에는 수정 허용, 세부 검증 로직은 Epic 7에서 확정)

**AC-020**: 배정 미연결 청약의 수량 축소는 자유 허용
- Given: 배정 기록(Allocation)이 연결되지 않은 청약이 존재함
- When: 수량을 줄여 PATCH /subscriptions/{id} 요청이 전송됨
- Then: 200 OK 응답이 반환되고 수량이 축소 반영됨

## 엣지케이스
- 동일 (investorId, securitiesAccountId, ipoId) 조합의 중복 방지는 **ACTIVE 상태 기준의 부분 유니크 인덱스**로 처리하며, CANCELLED 기록은 재등록을 막지 않음
- 잠재적 동시성: 단일 사용자가 자신의 기록을 순차 입력하는 개인용 서비스이므로 DB 유니크 제약 + 표준 트랜잭션 경계로 충분하며, 낙관적/비관적 락이나 분산 락을 도입하지 않음 (D-S-006)
- 메모(memo)는 선택 필드이며 null 허용 (기본 빈 문자열 사용 금지 — null 그대로 저장)
- 청약일시(subscribedAt)는 미래 시각도 입력 가능(사후 기록 서비스이므로 과거/현재만 강제하지 않음). 단 기록 성격상 유효성 오류 목록에서 음수/0 문제만 기본 검증
- quantity는 0 초과 Long, depositAmount는 0 이상 Long으로만 검증 (환불액 계산은 Epic 7 범위 — 이 도메인에서 계산하지 않음)
- 이미 CANCELLED된 기록을 다시 수정하는 요청은 404로 처리 (조회 API에서 노출되지 않으므로)
- INACTIVE 처리된 IPO / 삭제된 투자자·계좌가 참조된 기존 Subscription은 그대로 유지 (논리 삭제 원칙)
- 비활성화(CANCELLED)된 기록은 상세 조회에서도 노출하지 않음
- status 필드는 ACTIVE 또는 CANCELLED만 허용 (다른 상태값은 400)
- 메모가 매우 긴 경우(길이 상한 초과)는 상한을 아키텍처 단계에서 확정하여 400 처리

## 데이터 흐름
### Investor / SecuritiesAccount / IPO ← Subscription 참조 관계도
```
Investor (One) <---- (Many) Subscription (Many) ----> (One) IPO
SecuritiesAccount (One) <---- (Many)          |
                                              v
                                        Allocation (Epic 7, 아직 미구현)
```
- Subscription은 정확히 하나의 Investor, 하나의 SecuritiesAccount, 하나의 IPO를 참조함 (외래 키 제약으로 참조 무결성 보장)
- 하나의 Investor / SecuritiesAccount / IPO는 여러 Subscription(다른 조합 또는 취소된 기록)으로부터 참조될 수 있음
- IPO·투자자·계좌의 삭제는 논리 삭제로만 처리되므로 기존 Subscription 기록은 유지됨

### Subscription → Allocation 관계 (Epic 7 — 아직 미구현)
- 배정 결과는 Epic 7의 별도 Allocation 엔티티가 Subscription을 참조(N:1 또는 1:1)하는 형태로 설계 예정이며, **이번 Epic에서 구현하지 않는다** (D-S-001)
- Subscription에는 배정 상태/배정 수량 필드를 두지 않으므로, 이후 Allocation 도입 시 기존 데이터를 변경할 필요가 없다 (도메인 분리 원칙)
- 환불 금액 계산은 배정 결과에 의존하는 값으로 Allocation(Epic 7) 도메인에서 처리하며, Subscription은 사용자가 입력한 증거금 납입액(사실 기록)만 보유한다 (D-S-002)