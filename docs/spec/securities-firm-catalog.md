# 증권사 기준정보 관리 기능 명세

## 개요
증권계좌가 참조할 증권사 기준정보를 일관되게 등록·조회할 수 있는 기능을 제공한다.

## 용어 정의
- **SecuritiesFirm**: 증권사 기준정보 엔티티
- **내부 ID**: Long 타입의 자동증가 기본키
- **외부 표준코드**: 금융투자협회 표준코드 등 외부 식별자 (선택사항)

## 도메인 모델
### SecuritiesFirm 엔티티
| 필드명 | 타입 | 필수 | 설명 |
|--------|------|------|------|
| id | Long | Y | 내부 식별자 (자동증가) |
| name | String | Y | 증권사명 |
| externalCode | String | N | 외부 표준코드 (금융투자협회 표준코드 등) |
| businessRegistrationNumber | String | N | 사업자등록번호 |
| status | String | Y | 상태 (ACTIVE/INACTIVE) |
| deletedAt | Timestamp | N | 논리 삭제 시각 |
| createdAt | Timestamp | Y | 생성 시각 |
| updatedAt | Timestamp | Y | 수정 시각 |

> **참고**: 조사 문서의 "대안적 접근"을 따라 MVP에서는 증권사명 변경 이력 테이블을 구현하지 않으며, 현재 이름만 저장한다.

## API 엔드포인트 목록
### 1. 증권사 등록
- **POST** `/securities-firms`
- **요청 본문**:
  ```json
  {
    "name": "증권사명",
    "externalCode": "외부표준코드 (선택)",
    "businessRegistrationNumber": "사업자등록번호 (선택)"
  }
  ```
- **응답** (성공):
  ```json
  {
    "id": 1,
    "name": "증권사명",
    "externalCode": "외부표준코드",
    "businessRegistrationNumber": "사업자등록번호",
    "status": "ACTIVE",
    "createdAt": "2026-09-20T10:00:00Z",
    "updatedAt": "2026-09-20T10:00:00Z"
  }
  ```
- **응답** (실패 - 빈 이름): 400 Bad Request
- **응답** (실패 - 중복 이름): 409 Conflict

### 2. 증권사 목록 조회
- **GET** `/securities-firms`
- **쿼리 파라미터**: 없음 (모든 인증된 사용자가 조회 가능)
- **응답**:
  ```json
  [
    {
      "id": 1,
      "name": "증권사명",
      "externalCode": "외부표준코드",
      "businessRegistrationNumber": "사업자등록번호",
      "status": "ACTIVE",
      "createdAt": "2026-09-20T10:00:00Z",
      "updatedAt": "2026-09-20T10:00:00Z"
    }
  ]
  ```

### 3. 증권사 상세 조회
- **GET** `/securities-firms/{id}`
- **경로 파라미터**: id (Long 타입)
- **응답** (성공):
  ```json
  {
    "id": 1,
    "name": "증권사명",
    "externalCode": "외부표준코드",
    "businessRegistrationNumber": "사업자등록번호",
    "status": "ACTIVE",
    "createdAt": "2026-09-20T10:00:00Z",
    "updatedAt": "2026-09-20T10:00:00Z"
  }
  ```
- **응답** (실패 - 존재하지 않는 ID): 404 Not Found

### 4. 증권사 수정
- **PATCH** `/securities-firms/{id}`
- **경로 파라미터**: id (Long 타입)
- **요청 본문**:
  ```json
  {
    "name": "변경된증권사명",
    "externalCode": "변경된외부표준코드",
    "businessRegistrationNumber": "변경된사업자등록번호"
  }
  ```
- **응답** (성공):
  ```json
  {
    "id": 1,
    "name": "변경된증권사명",
    "externalCode": "변경된외부표준코드",
    "businessRegistrationNumber": "변경된사업자등록번호",
    "status": "ACTIVE",
    "createdAt": "2026-09-20T10:00:00Z",
    "updatedAt": "2026-09-20T11:00:00Z"
  }
  ```
- **응답** (실패 - 빈 이름): 400 Bad Request
- **응답** (실패 - 중복 이름): 409 Conflict
- **응답** (실패 - 존재하지 않는 ID): 404 Not Found

### 5. 증권사 비활성화 (논리 삭제)
- **DELETE** `/securities-firms/{id}`
- **경로 파라미터**: id (Long 타입)
- **응답** (성공): 204 No Content — **연결된 증권계좌가 있어도 비활성화(논리 삭제)는 항상 허용된다.** 물리 삭제(레코드 완전 제거)만 금지되며, 이 엔드포인트는 물리 삭제를 수행하지 않으므로 연결된 계좌 여부와 무관하게 성공한다.
- **응답** (실패 - 존재하지 않는 ID): 404 Not Found

## Acceptance Criteria
**AC-001**: 증권사 등록 성공
- Given: 유효한 증권사명이 제공됨
- When: POST /securities-firms 요청이 전송됨
- Then: 201 Created 응답이 반환되고, 신규 SecuritiesFirm이 생성됨

**AC-002**: 증권사 등록 실패 (빈 이름)
- Given: 빈 증권사명이 제공됨
- When: POST /securities-firms 요청이 전송됨
- Then: 400 Bad Request 응답이 반환됨

**AC-003**: 증권사 등록 실패 (중복 이름)
- Given: 기존에 동일한 이름의 증권사가 존재함
- When: POST /securities-firms 요청이 전송됨
- Then: 409 Conflict 응답이 반환됨

**AC-004**: 증권사 목록 조회 성공
- Given: 하나 이상의 증권사가 등록됨
- When: GET /securities-firms 요청이 전송됨
- Then: 200 OK 응답이 반환되고, 모든 증권사 목록이 포함됨

**AC-005**: 증권사 상세 조회 성공
- Given: 존재하는 증권사 ID가 제공됨
- When: GET /securities-firms/{id} 요청이 전송됨
- Then: 200 OK 응답이 반환되고, 해당 증권사의 상세 정보가 반환됨

**AC-006**: 증권사 상세 조회 실패 (존재하지 않는 ID)
- Given: 존재하지 않는 증권사 ID가 제공됨
- When: GET /securities-firms/{id} 요청이 전송됨
- Then: 404 Not Found 응답이 반환됨

**AC-007**: 증권사 수정 성공
- Given: 존재하는 증권사 ID와 유효한 수정 데이터가 제공됨
- When: PATCH /securities-firms/{id} 요청이 전송됨
- Then: 200 OK 응답이 반환되고, 증권사 정보가 업데이트됨

**AC-008**: 증권사 수정 실패 (빈 이름)
- Given: 존재하는 증권사 ID와 빈 증권사명이 제공됨
- When: PATCH /securities-firms/{id} 요청이 전송됨
- Then: 400 Bad Request 응답이 반환됨

**AC-009**: 증권사 수정 실패 (중복 이름)
- Given: 기존에 동일한 이름의 증권사가 존재하고, 해당 증권사 ID가 제공됨
- When: PATCH /securities-firms/{id} 요청이 전송됨 (중복 이름으로 수정 시도)
- Then: 409 Conflict 응답이 반환됨

**AC-010**: 증권사 수정 실패 (존재하지 않는 ID)
- Given: 존재하지 않는 증권사 ID와 수정 데이터가 제공됨
- When: PATCH /securities-firms/{id} 요청이 전송됨
- Then: 404 Not Found 응답이 반환됨

**AC-011**: 증권사 비활성화 성공 (연결된 계좌 없음)
- Given: 존재하는 증권사 ID와 연결된 증권계좌가 없음
- When: DELETE /securities-firms/{id} 요청이 전송됨
- Then: 204 No Content 응답이 반환되고, 증권사의 status가 INACTIVE로 변경되며 deletedAt이 설정됨

**AC-012**: 증권사 비활성화 성공 (연결된 계좌 있음)
- Given: 존재하는 증권사 ID와 연결된 증권계좌가 하나 이상 있음
- When: DELETE /securities-firms/{id} 요청이 전송됨
- Then: 204 No Content 응답이 반환되고, 증권사의 status가 INACTIVE로 변경됨 (논리 삭제는 연결된 계좌 여부와 무관하게 항상 허용됨 — 물리 삭제만 금지되며 이 엔드포인트는 물리 삭제를 수행하지 않음). 기존 연결된 증권계좌는 그대로 유지됨

**AC-013**: 증권사 비활성화 실패 (존재하지 않는 ID)
- Given: 존재하지 않는 증권사 ID가 제공됨
- When: DELETE /securities-firms/{id} 요청이 전송됨
- Then: 404 Not Found 응답이 반환됨

**AC-014**: 비활성화된 증권사는 신규 계좌 등록 시 선택 불가
- Given: INACTIVE 상태의 증권사가 존재함
- When: 증권계좌 등록 시 증권사 선택 목록이 제공됨
- Then: INACTIVE 상태의 증권사는 선택 목록에 포함되지 않음

## 엣지케이스
- 외부 표준코드가 null인 경우 중복 검사에서 제외
- 사업자등록번호가 null인 경우 중복 검사에서 제외
- 상태 필드는 ACTIVE 또는 INACTIVE만 허용
- 삭제된 증권사(deletedAt이 설정된 경우)는 조회 API에서 제외되어야 함
- 매우 긴 증권사명 (255자 초과)에 대한 처리
- 특수 문자가 포함된 증권사명 처리

## 데이터 흐름
### SecuritiesFirm ← SecuritiesAccount 참조 관계도
```
SecuritiesAccount (Many) ---> SecuritiesFirm (One)
```
- 증권계좌는 정확히 하나의 증권사를 참조함
- 증권사의 삭제(비활성화)는 참조 무결성을 유지하기 위해 논리 삭제로만 처리
- 증권계좌의 증권사 필드는 외부 키 제약 조건으로 참조 무결성 보장
- 증권사 비활성화 시, 기존 증권계좌는 유지되지만 신규 계좌 생성 시 해당 증권사는 선택 불가