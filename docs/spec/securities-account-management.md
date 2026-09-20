# 증권계좌 관리 기능 명세

## 개요
투자자가 보유한 증권계좌를 등록·조회·수정·비활성화할 수 있는 기능을 제공한다. 계좌번호는 마스킹 처리가 기본이며, 전노출 조회는 별도 인가 엔드포인트를 통해 제한적으로 허용한다.

## 용어 정의
- **SecuritiesAccount**: 증권계좌 엔티티
- **내부 ID**: Long 타입의 자동증가 기본키 (IDENTITY)
- **계좌번호**: 실제 증권사 계좌 번호. 암호화 저장 및 마스킹 응답 적용
- **기본 계좌**: 투자자당 하나만 지정 가능한 대표 계좌 (is_default)

## 도메인 모델
### SecuritiesAccount 엔티티
| 필드명 | 타입 | 필수 | 설명 |
|--------|------|------|------|
| id | Long | Y | 내부 식별자 (자동증가, IDENTITY) |
| investorId | Long | Y | 투자자 외부키 (FK → Investor.id) |
| securitiesFirmId | Long | Y | 증권사 외부키 (FK → SecuritiesFirm.id) |
| accountNumber | String | Y | 계좌번호 (암호화 저장, 응답 시 마스킹 기본) |
| accountName | String | N | 계좌 별칭 (예: "개인 MTS 계좌") |
| isDefault | Boolean | Y | 기본 계좌 여부 (투자자당 하나만 TRUE, 부분 유니크 제약) |
| status | String | Y | 상태 (ACTIVE/INACTIVE) |
| deletedAt | Timestamp | N | 논리 삭제 시각 |
| createdAt | Timestamp | Y | 생성 시각 |
| updatedAt | Timestamp | Y | 수정 시각 |

## API 엔드포인트 목록

### 1. 증권계좌 등록
- **POST** `/securities-accounts`
- **요청 본문**:
  ```json
  {
    "investorId": 1,
    "securitiesFirmId": 3,
    "accountNumber": "123-456-789012",
    "accountName": "개인 MTS 계좌",
    "isDefault": false
  }
  ```
- **응답** (성공):
  ```json
  {
    "id": 1,
    "investorId": 1,
    "securitiesFirmId": 3,
    "accountNumber": "********9012",
    "accountName": "개인 MTS 계좌",
    "isDefault": false,
    "status": "ACTIVE",
    "deletedAt": null,
    "createdAt": "2026-09-21T10:00:00Z",
    "updatedAt": "2026-09-21T10:00:00Z"
  }
  ```
- **응답** (실패 - 필수값 누락): 400 Bad Request
- **응답** (실패 - 존재하지 않는 투자자/증권사): 400 Bad Request
- **응답** (실패 - 소유권 없음): 403 Forbidden

### 2. 증권계좌 목록 조회 (마스킹된 계좌번호)
- **GET** `/securities-accounts`
- **쿼리 파라미터**:
  - `investorId`: Long (필수) — 조회할 투자자 ID
  - `status`: String (선택, ACTIVE/INACTIVE/BOTH, 기본값: ACTIVE)
- **응답** (성공):
  ```json
  [
    {
      "id": 1,
      "investorId": 1,
      "securitiesFirmId": 3,
      "securitiesFirmName": "미래에셋증권",
      "accountNumber": "********9012",
      "accountName": "개인 MTS 계좌",
      "isDefault": true,
      "status": "ACTIVE",
      "deletedAt": null,
      "createdAt": "2026-09-21T10:00:00Z",
      "updatedAt": "2026-09-21T10:00:00Z"
    }
  ]
  ```
- **응답** (실패 - 소유권 없음): 403 Forbidden

### 3. 증권계좌 상세 조회 (마스킹된 계좌번호)
- **GET** `/securities-accounts/{id}`
- **경로 파라미터**: id (Long 타입)
- **응답** (성공):
  ```json
  {
    "id": 1,
    "investorId": 1,
    "securitiesFirmId": 3,
    "securitiesFirmName": "미래에셋증권",
    "accountNumber": "********9012",
    "accountName": "개인 MTS 계좌",
    "isDefault": true,
    "status": "ACTIVE",
    "deletedAt": null,
    "createdAt": "2026-09-21T10:00:00Z",
    "updatedAt": "2026-09-21T10:00:00Z"
  }
  ```
- **응답** (실패 - 존재하지 않는 ID): 404 Not Found
- **응답** (실패 - 소유권 없음): 403 Forbidden

### 4. 증권계좌 계좌번호 전체 노출 조회
- **GET** `/securities-accounts/{id}/account-number`
- **설명**: 계좌번호 전체를 평문으로 반환한다. 소유 투자자의 로그인 계정 또는 시스템 관리자만 접근 가능하며, 접근 기록이 감사 로그에 남는다.
- **경로 파라미터**: id (Long 타입)
- **응답** (성공):
  ```json
  {
    "id": 1,
    "accountNumber": "123-456-789012",
    "exposedAt": "2026-09-21T10:05:00Z"
  }
  ```
- **응답** (실패 - 소유권 없음): 403 Forbidden
- **응답** (실패 - 존재하지 않는 ID): 404 Not Found

### 5. 증권계좌 수정
- **PATCH** `/securities-accounts/{id}`
- **경로 파라미터**: id (Long 타입)
- **요청 본문**:
  ```json
  {
    "accountName": "변경된 계좌 별칭",
    "securitiesFirmId": 5
  }
  ```
- **응답** (성공):
  ```json
  {
    "id": 1,
    "investorId": 1,
    "securitiesFirmId": 5,
    "securitiesFirmName": "한국투자증권",
    "accountNumber": "********9012",
    "accountName": "변경된 계좌 별칭",
    "isDefault": true,
    "status": "ACTIVE",
    "deletedAt": null,
    "createdAt": "2026-09-21T10:00:00Z",
    "updatedAt": "2026-09-21T11:00:00Z"
  }
  ```
- **응답** (실패 - 존재하지 않는 ID): 404 Not Found
- **응답** (실패 - 소유권 없음): 403 Forbidden
- **응답** (실패 - 존재하지 않는 증권사): 400 Bad Request
- **참고**: accountNumber는 수정 불가. 계좌번호 변경은 삭제 후 재등록으로 처리.

### 6. 증권계좌 비활성화 (논리 삭제)
- **DELETE** `/securities-accounts/{id}`
- **경로 파라미터**: id (Long 타입)
- **응답** (성공): 204 No Content — status가 INACTIVE로 변경되고 deletedAt이 설정됨
- **응답** (실패 - 존재하지 않는 ID): 404 Not Found
- **응답** (실패 - 소유권 없음): 403 Forbidden
- **참고**: 기존 Subscription 등 금융 기록과의 참조 무결성을 위해 물리 삭제는 절대 불가. 논리 삭제만 허용.

### 7. 기본 계좌 지정
- **PATCH** `/securities-accounts/{id}/set-default`
- **설명**: 해당 계좌를 투자자의 기본 계좌로 지정한다. 기존 기본 계좌의 isDefault는 자동으로 false로 변경된다. 트랜잭션 내에서 원자적으로 처리되며, 동시성 제어(낙관적 락)가 적용된다.
- **경로 파라미터**: id (Long 타입)
- **응답** (성공):
  ```json
  {
    "id": 1,
    "investorId": 1,
    "securitiesFirmId": 3,
    "securitiesFirmName": "미래에셋증권",
    "accountNumber": "********9012",
    "accountName": "개인 MTS 계좌",
    "isDefault": true,
    "status": "ACTIVE",
    "deletedAt": null,
    "createdAt": "2026-09-21T10:00:00Z",
    "updatedAt": "2026-09-21T10:05:00Z"
  }
  ```
- **응답** (실패 - 존재하지 않는 ID): 404 Not Found
- **응답** (실패 - 소유권 없음): 403 Forbidden
- **응답** (실패 - 이미 기본 계좌): 409 Conflict (이미 해당 계좌가 기본 계좌로 지정되어 있는 경우)
- **응답** (실패 - 비활성화된 계좌): 400 Bad Request

## Acceptance Criteria

### AC-001: 증권계좌 등록 성공
**Given:** 인증된 계정이 특정 투자자를 소유하고 있고, 유효한 증권사 ID와 계좌번호가 제공됨  
**When:** POST /securities-accounts 요청을 보냄  
**Then:** 201 Created 응답을 반환하고, 신규 SecuritiesAccount 레코드가 생성됨  
**And:** 응답에 계좌 ID, 투자자 ID, 증권사 ID, 마스킹된 계좌번호, status=ACTIVE, deletedAt=null이 포함됨

### AC-002: 증권계좌 등록 실패 (필수값 누락)
**Given:** 인증된 계정이 특정 투자자를 소유하고 있음  
**When:** POST /securities-accounts 요청에 investorId, securitiesFirmId, accountNumber 중 하나 이상 누락됨  
**Then:** 400 Bad Request 응답을 반환하고, VALIDATION_ERROR 오류가 발생함  
**And:** 레코드가 생성되지 않음

### AC-003: 증권계좌 등록 실패 (존재하지 않는 투자자)
**Given:** 존재하지 않는 투자자 ID가 제공됨  
**When:** POST /securities-accounts 요청을 보냄  
**Then:** 400 Bad Request 응답을 반환함  
**And:** 레코드가 생성되지 않음

### AC-004: 증권계좌 등록 실패 (존재하지 않는 증권사)
**Given:** 존재하지 않는 증권사 ID가 제공됨  
**When:** POST /securities-accounts 요청을 보냄  
**Then:** 400 Bad Request 응답을 반환함  
**And:** 레코드가 생성되지 않음

### AC-005: 증권계좌 등록 실패 (계정 소유권 없음)
**Given:** 인증된 계정 A가 존재하고, 다른 계정 B의 투자자 ID가 제공됨  
**When:** 계정 A가 POST /securities-accounts 요청을 보냄  
**Then:** 403 Forbidden 응답을 반환함

### AC-006: 증권계좌 목록 조회 성공 (마스킹)
**Given:** 특정 투자자에게 2개의 증권계좌가 연결되어 있음  
**When:** GET /securities-accounts?investorId={id} 요청을 보냄  
**Then:** 200 OK 응답을 반환하고, 2개 계좌의 목록이 마스킹된 계좌번호와 함께 반환됨  
**And:** 다른 투자자의 계좌는 포함되지 않음

### AC-007: 증권계좌 상세 조회 성공 (마스킹)
**Given:** 인증된 계정이 특정 증권계좌를 소유하고 있음  
**When:** GET /securities-accounts/{valid_id} 요청을 보냄  
**Then:** 200 OK 응답을 반환하고, 해당 계좌의 상세 정보가 마스킹된 계좌번호와 함께 반환됨

### AC-008: 증권계좌 계좌번호 전체 노출 조회 성공
**Given:** 인증된 계정이 해당 증권계좌의 소유 투자자를 보유하고 있음  
**When:** GET /securities-accounts/{id}/account-number 요청을 보냄  
**Then:** 200 OK 응답을 반환하고, 계좌번호 전체가 평문으로 반환됨  
**And:** 감사 로그에 접근 기록이 남음

### AC-009: 증권계좌 계좌번호 전체 노출 조회 실패 (소유권 없음)
**Given:** 인증된 계정 A가 존재하고, 다른 계정 B의 증권계좌 ID가 알려져 있음  
**When:** 계정 A가 GET /securities-accounts/{accountB_id}/account-number 요청을 보냄  
**Then:** 403 Forbidden 응답을 반환함  
**And:** 계좌번호 전체를 반환하지 않음

### AC-010: 증권계좌 수정 성공
**Given:** 인증된 계정이 특정 증권계좌를 소유하고 있고, 새로운 계좌 별칭이 제공됨  
**When:** PATCH /securities-accounts/{id} 요청을 보냄  
**Then:** 200 OK 응답을 반환하고, 계좌 정보가 업데이트됨  
**And:** updated_at이 반영됨

### AC-011: 증권계좌 수정 실패 (존재하지 않는 증권사)
**Given:** 인증된 계정이 특정 증권계좌를 소유하고 있음  
**When:** PATCH /securities-accounts/{id} 요청에 존재하지 않는 securitiesFirmId가 포함됨  
**Then:** 400 Bad Request 응답을 반환함

### AC-012: 증권계좌 비활성화 성공 (soft delete)
**Given:** 인증된 계정이 특정 증권계좌를 소유하고 있음  
**When:** DELETE /securities-accounts/{id} 요청을 보냄  
**Then:** 204 No Content 응답을 반환하고, status가 INACTIVE로 변경됨  
**And:** deletedAt에 현재 타임스탬프가 설정됨

### AC-013: 비활성화된 증권계좌는 신규 청약 불가하지만 기존 기록은 유지
**Given:** 비활성화된 증권계좌에 기존 Subscription 기록이 있음  
**When:** 해당 계좌로 신규 청약을 시도함  
**Then:** 400 Bad Request 또는 403 Forbidden 응답을 반환하고, 청약이 차단됨  
**And:** 기존 Subscription 기록은 정상적으로 조회 및 수익 계산에 포함됨

### AC-014: 기본 계좌 지정 성공
**Given:** 특정 투자자에게 2개의 활성화된 증권계좌가 있고, A 계좌가 현재 기본 계좌임  
**When:** B 계좌에 대해 PATCH /securities-accounts/{B_id}/set-default 요청을 보냄  
**Then:** 200 OK 응답을 반환하고, B 계좌의 isDefault가 true로 변경됨  
**And:** A 계좌의 isDefault가 false로 변경됨 (원자적 처리)

### AC-015: 기본 계좌 지정 실패 (이미 기본 계좌)
**Given:** 특정 투자자에게 A 계좌가 이미 기본 계좌로 지정되어 있음  
**When:** A 계좌에 대해 PATCH /securities-accounts/{A_id}/set-default 요청을 보냄  
**Then:** 409 Conflict 응답을 반환함

### AC-016: 기본 계좌 지정 실패 (비활성화된 계좌)
**Given:** INACTIVE 상태의 증권계좌가 존재함  
**When:** 해당 계좌에 대해 PATCH /securities-accounts/{id}/set-default 요청을 보냄  
**Then:** 400 Bad Request 응답을 반환함

### AC-017: 투자자당 기본 계좌는 정확히 1개
**Given:** 특정 투자자에게 기본 계좌가 하나 이상 지정되어 있지 않음  
**When:** 해당 투자자의 계좌 목록을 조회함  
**Then:** isDefault가 true인 계좌가 정확히 0개 또는 1개임 (DB 부분 유니크 제약으로 2개 이상 불가)

### AC-018: 투자자 비활성화 시 연결된 계좌 처리
**Given:** 특정 투자자에게 증권계좌가 연결되어 있음  
**When:** 해당 투자가 soft delete(비활성화)됨  
**Then:** 연결된 증권계좌는 삭제되지 않고 그대로 유지됨 (투자자와 계좌는 논리적으로 독립)

## 엣지케이스
1. **계좌번호 수정 시도**: accountNumber 필드는 PATCH로 변경 불가. 변경이 필요하면 기존 계좌를 비활성화하고 새 계좌를 등록해야 함.
2. **기본 계좌가 없는 투자자**: 투자자의 첫 번째 계좌 등록 시 isDefault=true로 등록할지, 기본값 false로 둘지 정책 결정 필요 (기본값: 첫 계좌 등록 시 자동으로 isDefault=true 설정).
3. **비활성화된 계좌를 기본 계좌로 지정 시도**: 400 Bad Request 반환.
4. **동시 기본 계좌 변경 요청**: 트랜잭션 레벨에서 투자자 레코드를 잠금(Snapshot/Version 체크)하여 먼저 온 요청만 성공, 나머지는 실패 반환.
5. **비활성화된 증권사의 기존 계좌**: 증권사가 INACTIVE 상태라도 기존 등록된 계좌는 유지되며, 계좌 수정 시 증권사 변경은 INACTIVE 증권사로 변경 불가.
6. **존재하지 않는 계좌 ID로 기본 계좌 지정**: 404 Not Found 반환.
7. **소유권 없는 계좌에 대한 모든 작업**: 403 Forbidden 반환.
8. **중복 계좌번호 등록**: 같은 투자자에게 동일한 계좌번호를 다른 증권사로 등록 허용 (실제로는 같은 계좌번호의 증권사가 다를 수 없으나, 시스템상 검증하지 않음).
9. **계좌번호 마스킹 패턴**: 조사 결론(뒷 4자리만 평문 노출)에 따라 뒷 4자리를 평문으로 노출하고 나머지는 '*'로 마스킹 (예: `********9012`). 4자리 미만인 계좌번호는 전체 마스킹.
10. **비활성화 후 재활성화**: DELETE(비활성화) 후 재활성화를 위한 별도 PATCH 엔드포인트는 MVP에서 미제공. 필요 시 추후 추가.

## 데이터 흐름

```
Investor (1) ──────────────┐
  │                        │
  │ investorId (FK)        │
  │                        ▼
  │              SecuritiesAccount (N)
  │                        │
  │                        │ securitiesFirmId (FK)
  │                        ▼
  └────────────── SecuritiesFirm (1)
```

**관계 설명:**
- Investor 1:N SecuritiesAccount — 하나의 투자자는 여러 증권계좌를 보유할 수 있음
- SecuritiesFirm 1:N SecuritiesAccount — 하나의 증권사는 여러 계좌에서 참조될 수 있음
- SecuritiesAccount는 Investor와 SecuritiesFirm을 외래키로 참조 (모두 N:1 관계)

**주요 제약:**
- **기본 계좌 유니크**: `UNIQUE(investor_id) WHERE is_default = TRUE` — 투자자당 기본 계좌는 정확히 하나
- **외래키 무결성**: `ON DELETE RESTRICT` — 참조 중인 투자자/증권사가 삭제(비활성화)되어도 계좌는 유지됨
- **논리 삭제**: 물리 삭제 금지, deletedAt 타임스탬프와 status 컬럼으로 소프트 삭제 처리
- **계좌번호 보호**: DB에는 암호화 저장, API 응답에서는 마스킹 기본, 전노출은 별도 엔드포인트+감사 로그
