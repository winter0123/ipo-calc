# 투자자 관리 사양서 (Investor Management Specification)

> **수정 사항(2026-09-20, Master 결정 D-I-001)**: 아래 문서 전반의 "UUID" 표기는 초안 작성 시점의 제안이며, 기존 Account 엔티티와의 일관성을 위해 최종적으로 **Long(IDENTITY, 정수 자동증가)** ID를 사용하기로 결정됨. 구현 시 "id: string (UUID)"는 "id: number"로 대체한다. 자세한 근거는 `docs/work-log/investor-management.md` D-I-001 참고.
>
> **수정 사항(2026-09-21, SF-3 구현 중 확인)**: 아래 JSON 예시의 필드명을 초안의 snake_case(`display_name`, `account_id`, `deleted_at`, `created_at`, `updated_at`)에서 **camelCase**(`displayName`, `accountId`, `deletedAt`, `createdAt`, `updatedAt`)로 일괄 수정함. Epic 1(`SignupResponse` 등 기존 DTO)이 이미 camelCase를 사용 중이므로 코드베이스 일관성을 위함.
>
> **수정 사항(2026-10-22)**: 투자자 조회 응답에 `relationship`(SELF/PARTNER/FRIEND/PARENT/CHILD/OTHER, nullable)을 포함한다. 마이페이지와 투자자 현황에서 가입자 본인 정보를 표시할 때 사용한다. 신규 입력은 `PARTNER`(연인)를 사용하며 기존 `SPOUSE` 값은 저장 데이터와 구버전 클라이언트 호환을 위해 유지하고 화면에서는 연인으로 표시한다.

## API 엔드포인트 목록 (요청/응답 형식 포함)

### POST /investors (투자자 등록)
**요청:**
```json
{
  "displayName": "string (필수, 1-50자)",
  "relationship": "string (선택, enum: SELF, PARTNER, FRIEND, PARENT, CHILD, OTHER; 기존 SPOUSE 값 호환)"
}
```

**응답 (성공):**
```json
{
  "id": "string (UUID)",
  "displayName": "string",
  "relationship": "string (SELF/PARTNER/FRIEND/SPOUSE/PARENT/CHILD/OTHER, null 허용)",
  "accountId": "string (FK)",
  "status": "string (ACTIVE/INACTIVE)",
  "deletedAt": "null",
  "createdAt": "ISO 8601 datetime",
  "updatedAt": "ISO 8601 datetime"
}
```

**응답 (실패 - 400 Bad Request):**
```json
{
  "error": "VALIDATION_ERROR",
  "message": "표시 이름은 필수입니다."
}
```

### GET /investors (계정 소유 투자자 목록 조회)
**요청 파라미터:**
- `status`: string (선택, ACTIVE/INACTIVE/BOTH, 기본값: ACTIVE)

**응답 (성공):**
```json
[
  {
    "id": "string (UUID)",
    "displayName": "string",
    "relationship": "string (SELF/PARTNER/FRIEND/SPOUSE/PARENT/CHILD/OTHER, null 허용)",
    "accountId": "string (FK)",
    "status": "string (ACTIVE/INACTIVE)",
    "deletedAt": "null 또는 ISO 8601 datetime",
    "createdAt": "ISO 8601 datetime",
    "updatedAt": "ISO 8601 datetime"
  }
]
```

**응답 (실패 - 403 Forbidden):**
```json
{
  "error": "FORBIDDEN",
  "message": "계정 소유권이 없습니다."
}
```

### GET /investors/{id} (투자자 상세 조회)
**경로 파라미터:**
- `id`: string (UUID)

**응답 (성공):**
```json
{
  "id": "string (UUID)",
  "displayName": "string",
  "relationship": "string (SELF/PARTNER/FRIEND/SPOUSE/PARENT/CHILD/OTHER, null 허용)",
  "accountId": "string (FK)",
  "status": "string (ACTIVE/INACTIVE)",
  "deletedAt": "null 또는 ISO 8601 datetime",
  "createdAt": "ISO 8601 datetime",
  "updatedAt": "ISO 8601 datetime"
}
```

**응답 (실패 - 404 Not Found):**
```json
{
  "error": "NOT_FOUND",
  "message": "투자자를 찾을 수 없습니다."
}
```

**응답 (실패 - 403 Forbidden):**
```json
{
  "error": "FORBIDDEN",
  "message": "계정 소유권이 없습니다."
}
```

### PATCH /investors/{id} (투자자 수정)
**경로 파라미터:**
- `id`: string (UUID)

**요청:**
```json
{
  "displayName": "string (선택, 1-50자)",
  "relationship": "string (선택, enum: SELF, PARTNER, FRIEND, PARENT, CHILD, OTHER; 기존 SPOUSE 값 호환)"
}
```

**응답 (성공):**
```json
{
  "id": "string (UUID)",
  "displayName": "string",
  "accountId": "string (FK)",
  "status": "string (ACTIVE/INACTIVE)",
  "deletedAt": "null 또는 ISO 8601 datetime",
  "createdAt": "ISO 8601 datetime",
  "updatedAt": "ISO 8601 datetime"
}
```

**응답 (실패 - 400 Bad Request):**
```json
{
  "error": "VALIDATION_ERROR",
  "message": "표시 이름은 필수입니다."
}
```

**응답 (실패 - 404 Not Found):**
```json
{
  "error": "NOT_FOUND",
  "message": "투자자를 찾을 수 없습니다."
}
```

**응답 (실패 - 403 Forbidden):**
```json
{
  "error": "FORBIDDEN",
  "message": "계정 소유권이 없습니다."
}
```

### DELETE /investors/{id} (비활성화 - 실제로는 soft delete)
**경로 파라미터:**
- `id`: string (UUID)

**응답 (성공):**
```json
{
  "message": "투자자가 비활성화되었습니다."
}
```

**응답 (실패 - 404 Not Found):**
```json
{
  "error": "NOT_FOUND",
  "message": "투자자를 찾을 수 없습니다."
}
```

**응답 (실패 - 403 Forbidden):**
```json
{
  "error": "FORBIDDEN",
  "message": "계정 소유권이 없습니다."
}
```

## Acceptance Criteria (AC-001부터 번호 매겨서, Given/When/Then 형식)

### AC-001: 투자자 등록 성공
**Given:** 인증된 계정이 존재하고, 유효한 표시 이름이 제공됨  
**When:** POST /investors 요청을 보냄  
**Then:** 201 Created 응답을 반환하고, 신규 투자자 레코드가 생성됨  
**And:** 응답에 투자자 UUID, 표시 이름, accountId, status=ACTIVE, deletedAt=null이 포함됨

### AC-002: 투자자 등록 실패 (빈 이름)
**Given:** 인증된 계정이 존재함  
**When:** POST /investors 요청에 displayName이 빈 문자열인 경우  
**Then:** 400 Bad Request 응답을 반환하고, VALIDATION_ERROR 오류가 발생함  
**And:** 투자자 레코드가 생성되지 않음

### AC-003: 투자자 등록 실패 (계정 미인증)
**Given:** 인증되지 않은 요청이 들어옴  
**When:** POST /investors 요청을 보냄  
**Then:** 401 Unauthorized 응답을 반환함  
**And:** 투자자 레코드가 생성되지 않음

### AC-004: 투자자 목록 조회 (본인 계정 소유만)
**Given:** 인증된 계정이 2명의 투자자를 소유하고 있음  
**When:** GET /investors 요청을 보냄  
**Then:** 200 OK 응답을 반환하고, 해당 계정 소유 투자자 2명의 목록을 반환함  
**And:** 다른 계정의 투자자는 포함되지 않음

### AC-005: 투자자 목록 조회 (다른 계정 투자자 조회 시 403/404)
**Given:** 인증된 계정 A가 존재하고, 계정 B의 투자자 ID가 알려져 있음  
**When:** 계정 A가 GET /investors/{accountB_investor_id} 요청을 보냄  
**Then:** 403 Forbidden 응답을 반환함  
**And:** 계정 B의 투자자 정보를 반환하지 않음

### AC-006: 투자자 상세 조회 성공
**Given:** 인증된 계정이 특정 투자자를 소유하고 있음  
**When:** GET /investors/{valid_investor_id} 요청을 보냄  
**Then:** 200 OK 응답을 반환하고, 해당 투자자의 상세 정보를 반환함

### AC-007: 투자자 수정 성공
**Given:** 인증된 계정이 특정 투자자를 소유하고 있고, 새로운 표시 이름이 제공됨  
**When:** PATCH /investors/{investor_id} 요청을 보냄  
**Then:** 200 OK 응답을 반환하고, 투자자 정보가 업데이트됨  
**And:** 응답에 수정된 표시 이름과 updatedAt이 반영됨

### AC-008: 투자자 수정 실패 (빈 이름)
**Given:** 인증된 계정이 특정 투자자를 소유하고 있음  
**When:** PATCH /investors/{investor_id} 요청에 displayName이 빈 문자열인 경우  
**Then:** 400 Bad Request 응답을 반환하고, VALIDATION_ERROR 오류가 발생함  
**And:** 투자자 정보가 변경되지 않음

### AC-009: 투자자 비활성화 (soft delete)
**Given:** 인증된 계정이 특정 투자자를 소유하고 있음  
**When:** DELETE /investors/{investor_id} 요청을 보냄  
**Then:** 200 OK 응답을 반환하고, 투자자의 status가 INACTIVE로 변경됨  
**And:** deletedAt에 현재 타임스탬프가 설정됨

### AC-010: 비활성화된 투자자는 신규 청약 불가하지만 기존 기록은 조회 가능
**Given:** 비활성화된 투자자가 기존 청약 기록을 가지고 있음  
**When:** 해당 투자자로 신규 청약을 시도함  
**Then:** 400 Bad Request 또는 403 Forbidden 응답을 반환하고, 청약이 차단됨  
**And:** 기존 청약 기록은 정상적으로 조회 가능함

### AC-011: 이름 중복 허용 확인
**Given:** 인증된 계정이 "홍길동"이라는 투자자를 소유하고 있음  
**When:** 같은 계정으로 "홍길동"이라는 이름의 또 다른 투자자를 등록 시도함  
**Then:** 201 Created 응답을 반환하고, 두 번째 투자자가 성공적으로 생성됨  
**And:** 두 투자자는 서로 다른 UUID를 가짐

## 엣지케이스

1. **최대 길이 표시 이름**: 50자를 초과하는 표시 이름 입력 시 400 Bad Request 반환
2. **특수 문자 표시 이름**: 표시 이름에 특수 문자 포함 허용 (예: 홍길동™, 김·이)
3. **빈 관계 필드**: relationship 필드가 빈 문자열인 경우 NULL로 저장 처리
4. **잘못된 UUID 형식**: 투자자 ID에 잘못된 UUID 형식 제공 시 400 Bad Request 반환
5. **존재하지 않는 투자자 조회**: 존재하지 않는 투자자 ID로 GET 요청 시 404 Not Found 반환
6. **이미 비활성화된 투자자 재비활성화**: 이미 INACTIVE 상태인 투자자에 DELETE 요청 시 200 OK 반환 (멱등성)
7. **비활성화된 투자자 수정**: INACTIVE 상태 투자자도 표시 이름 및 관계 수락 가능
8. **동명이인 가족**: 동일한 계정 내에서 "아들"과 "아버지"가 같은 이름을 가질 수 있음
9. **관계 값 검증**: 관계 필드에 유효하지 않은 값 제공 시 400 Bad Request 반환
10. **계정 소유권 확인**: 토큰 위조 시도를 통한 다른 계정 투자자 접근 시 403 Forbidden 반환

## 데이터 흐름 (Account → Investor 관계도 간단히)

```
Account (로그인 계정) 1:N Investor (실제 투자자)
     │
     ├─ accountId (PK, FK) ────┐
     │                          ▼
     │                     Investor 엔티티
     │                          │
     │                          ├─ id (UUID, PK)
     │                          ├─ displayName (필수)
     │                          ├─ accountId (FK → Account.accountId)
     │                          ├─ status (ACTIVE/INACTIVE)
     │                          ├─ deletedAt (Soft Delete 타임스탬프)
     │                          ├─ relationship (선택적, enum)
     │                          ├─ createdAt (생성 일시)
     │                          └─ updatedAt (수정 일시)
     │
     ▼
Investor 1:N Subscription (청약 기록)
Investor 1:N Allocation (배정 결과)
Investor 1:N Sale (매도 기록)
```

**참고사항:**
- Account와 Investor는 1:N 관계로, 하나의 로그인 계정이 여러 실제 투자자를 보유할 수 있음
- Investor의 accountId는 Account의 외래키(FK)로 참조 무결성 유지
- 삭제 시 물리 삭제 대신 status=INACTIVE 및 deletedAt 타임스탬프 설정 (Soft Delete)
- 투자자 이름은 동일 계정 내에서 중복 허용 (동명이인 가족 상황 반영)
- UUID를 사용해 투자자를 유일하게 식별하며, 표시 이름은 보여지기 위한 용도만 사용
