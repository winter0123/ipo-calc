# 계좌 존재 여부 기반 등록/재사용 (account-presence) 기능 명세

## 개요
투자자별 증권사 체크박스 ON 시 활성 계좌 재사용(가장 오래된 것)/없으면 `accountNumber=null` 생성. OFF 시 개별 계좌 비활성화(청약 이력 없으면 허용). 가짜 번호 금지, UI "미입력". 번호 사후 1회 입력(`null`→실제), 기존 번호 교체 불가. 동일 증권사 실계좌 추가는 기존대로. 소유권 검증 필수.

## 용어 정의
- **계좌 존재 체크**: 투자자+증권사 조합으로 활성(`status=ACTIVE`+`deletedAt null`) 계좌 조회
- **번호 없는 계좌**: `account_number IS NULL`인 활성 계좌
- **사후 입력**: 번호 없는 계좌에 `PATCH /securities-accounts/{id}`로 `accountNumber` 최초 1회 등록

## 도메인 모델 변경
| 필드명 | 타입 | 필수 | 변경 사항 |
|--------|------|------|-----------|
| id | Long | Y | - |
| investorId | Long | Y | - |
| securitiesFirmId | Long | Y | - |
| accountNumber | String | N | `nullable=true` 변경, 암호문 저장 |
| accountName | String | N | - |
| status | String | Y | ACTIVE/INACTIVE |
| deletedAt | Timestamp | N | 논리 삭제 시각 |
| createdAt | Timestamp | Y | - |
| updatedAt | Timestamp | Y | - |

## API 엔드포인트 변경

### 1. POST /securities-accounts — 증권계좌 등록
**요청**: `accountNumber` optional(null/생략/공백 허용)
**동작**:
- 제공 시: 기존 검증(정규화, 10~16자리, 암호화) 후 신규 생성
- 미제공/공백: 동일 투자자+증권사+ACTIVE+deletedAt null 중 `createdAt`/`id` 최신순 역순(가장 오래된) 1개 재사용, **기존 활성 계좌 없으면 `account_number=NULL`로 신규 생성**
**응답(201)**: 기존 응답과 동일(`accountNumber` 마스킹/null), `reused` 필드 없음
**실패**: 400(필수값/형식), 404(투자자/증권사 없음), 403(소유권 없음)

### 2. GET /securities-accounts — 목록 조회
**응답**: `accountNumber: string | null` 포함(마스킹/null)

### 3. GET /securities-accounts/{id} — 상세 조회
**응답**: 목록과 동일

### 4. GET /securities-accounts/{id}/account-number — 평문 조회
**성공(200)**: 평문 반환+감사로그
**실패**: 400(`accountNumber=null` 시 "계좌번호가 등록되지 않았습니다. 계좌 수정에서 계좌번호를 입력해 주세요."), 404, 403

### 5. PATCH /securities-accounts/{id} — 수정
**요청**: `accountNumber` optional 추가
**검증**:
- 미전달: 번호 무변경
- 공백(`""`): 400 "계좌번호는 10~16자리 숫자여야 합니다."
- 기존 암호문 존재: 409 "이미 계좌번호가 등록되어 있습니다"
- 유효값: 정규화+10~16자리+암호화 저장
**응답(200)**: 수정된 계좌 정보(마스킹/포함)

### 6. DELETE /securities-accounts/{id} — 비활성화
**조건**: 해당 계좌의 `Subscription` 존재 여부만 체크
- 존재: 409 "연결된 청약 이력이 있어 비활성화할 수 없습니다"
- 미존재: 진행(status=INACTIVE, deletedAt 설정, API 자격정보 물리 삭제)
**동일 투자자+증권사 타 계좌 존재 여부는 개별 DELETE 차단 사유 아님**(UI 체크박스에서만 차단)

### 7. 기본 계좌 지정: **제거됨**(V41에서 삭제)

## Acceptance Criteria
- **AC-001**: 번호 미제공 등록 → 가장 오래된 활성 계좌 재사용(201), 신규 생성 안 함
- **AC-002**: 번호 미제공이고 기존 활성 계좌 없음 → `account_number=NULL` 신규 생성(201)
- **AC-003**: 번호 제공 등록 → 기존 검증 후 신규 생성(201)
- **AC-004**: 번호 형식 오류 → 400
- **AC-005**: 사후 입력 성공(null→실제) → 200, 암호화 저장
- **AC-006**: 사후 입력 공백 → 400
- **AC-007**: 사후 입력 기존 번호 있음 → 409 "이미 계좌번호가 등록되어 있습니다"
- **AC-008**: 평문 조회 번호 없음 → 400 명확 안내
- **AC-009**: 비활성화 성공(청약 이력 없음) → 204
- **AC-010**: 비활성화 실패(청약 이력 있음) → 409
- **AC-011**: 목록/상세 `accountNumber:null` 키 포함 반환
- **AC-012**: 동시 POST/PATCH → 투자자 행 PESSIMISTIC_WRITE로 직렬화, 중복/경합 없음
- **AC-013**: 소유권 검증 → 기존 `SecuritiesAccountOwnershipVerifier` 2단계 유지

## 에러 응답
| 상황 | HTTP | error | message |
|------|------|-------|---------|
| 필수값 누락 | 400 | VALIDATION_ERROR | "investorId, securitiesFirmId는 필수 입력값입니다." |
| 계좌번호 형식/공백 | 400 | VALIDATION_ERROR | "계좌번호는 10~16자리 숫자여야 합니다." |
| 계좌번호 이미 존재(수정) | 409 | CONFLICT | "이미 계좌번호가 등록되어 있습니다" |
| 계좌번호 미등록 조회 | 400 | VALIDATION_ERROR | "계좌번호가 등록되지 않았습니다. 계좌 수정에서 계좌번호를 입력해 주세요." |
| 청약 이력으로 비활성화 불가 | 409 | CONFLICT | "연결된 청약 이력이 있어 비활성화할 수 없습니다" |
| 존재하지 않는 자원 | 404 | NOT_FOUND | "존재하지 않는 ...입니다." |
| 소유권 없음 | 403 | FORBIDDEN | "계정 소유권이 없습니다." |