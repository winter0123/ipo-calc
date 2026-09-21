# 아키텍처 설계: 증권계좌 관리 (securities-account-management)

> **식별자 결정**: Investor 및 SecuritiesFirm 아키텍처 문서와 동일하게 **Long(IDENTITY)** 식별자를 사용한다. 기존 Account 엔티티가 `@GeneratedValue(strategy = GenerationType.IDENTITY)`의 `Long id`를 사용 중이므로 코드베이스 일관성을 위해 UUID를 사용하지 않는다. DB 스키마에서도 `id`/`investor_id`/`securities_firm_id` 컬럼은 모두 **BIGINT**로 구현한다.

## 아키텍처 개요 (레이어 구조)

Investor 관리 및 SecuritiesFirm 기준정보 관리와 동일한 관심사 분리 원칙을 따른다. Spring Boot 백엔드는 다음 레이어 구조를 사용한다:

```
controller
application/service
domain
repository
infrastructure
```

- **controller**: HTTP 요청 처리, 입력 검증, 응답 매핑. 복잡한 업무 규칙은 포함하지 않음.
- **application/service**: 업무 규칙 유스케이스 오케스트레이션, 트랜잭션 경계. 계좌 소유권 검증·기본 계좌 동시성 제어·계좌번호 암호화/마스킹 로직 포함.
- **domain**: 엔티티, 밸류오브젝트, 도메인 이벤트, 불변식. SecuritiesAccount 엔티티 정의.
- **repository**: 데이터 접근 계층, Spring Data JPA 인터페이스 정의.
- **infrastructure**: 외부 시스템 연동, 설정, 공통 인프라 컴포넌트 (암호화 유틸리티 등).

증권계좌 관련 컴포넌트는 다음과 같이 구성한다:
- `controller.securitiesaccount`: SecuritiesAccountController
- `service.securitiesaccount`: SecuritiesAccountService
- `domain.model.securitiesaccount`: SecuritiesAccount 엔티티
- `repository.securitiesaccount`: SecuritiesAccountRepository
- `util.security`: SecurityUtils (계정 소유권 검증 헬퍼 — Investor 아키텍처에서 이미 정의)
- `infrastructure.encryption`: AccountNumberEncryptor (애플리케이션 레벨 암호화 유틸리티)

> **참고**: SecuritiesAccount는 사용자(Account) 소유 엔티티이므로, Investor 아키텍처에서 사용하는 계정 소유권 검증(SecurityUtils)을 적용한다. SecuritiesFirm은 글로벌 기준정보였으므로 소유권 검증이 불필요했으나, SecuritiesAccount는 반드시 소유 투자자와 연결되므로 소유권 검증이 모든 엔드포인트에 적용된다.

## 데이터 흐름

### 증권계좌 등록 흐름
1. 사용자가 POST /securities-accounts 요청 (investorId, securitiesFirmId, accountNumber, accountName, isDefault)
2. Controller가 입력값 검증 (필수 필드 확인, investorId/securitiesFirmId 존재 여부 확인)
3. Service에서 현재 인증된 계정 ID 획득 (SecurityContextHolder)
4. Service에서 요청의 investorId가 현재 인증된 계정에 소속된 투자자인지 소유권 검증 (403 방지)
5. Service에서 SecuritiesAccount 엔티티 생성 — accountNumber는 저장 전 애플리케이션 레벨 AES 암호화
6. 기본 계좌 지정(isDefault=true)인 경우, 기존 기본 계좌의 isDefault를 false로 변경하는 트랜잭션 내 원자적 처리 수행
7. SecuritiesAccountRepository.save로 저장
8. 저장된 정보 반환 (201 Created) — 응답 시 accountNumber는 마스킹 처리된 값으로 변환

### 증권계좌 목록 조회 흐름
1. 사용자가 GET /securities-accounts 요청 (investorId, status 파라미터)
2. Controller가 요청 파라미터 검증
3. Service에서 현재 인증된 계정 ID 획득
4. Service에서 해당 계정의 투자자 ID와 일치하는지 소유권 검증
5. Repository에서 해당 투자자의 계좌 목록 조회 (status 필터 적용)
6. 목록 반환 — 각 항목의 accountNumber는 마스킹 처리

### 증권계좌 상세 조회 흐름
1. 사용자가 GET /securities-accounts/{id} 요청
2. Controller가 경로 파라미터 검증
3. Service에서 현재 인증된 계정 ID 획득
4. Service에서 계좌 조회 및 계정 소유권 검증
5. 소유권 확인 실패 시 403 Forbidden 반환
6. 존재하지 않는 ID면 404 Not Found 반환
7. 성공 시 계좌 정보 반환 — accountNumber 마스킹 처리

### 증권계좌 계좌번호 전체 노출 조회 흐름
1. 사용자가 GET /securities-accounts/{id}/account-number 요청
2. Controller가 경로 파라미터 검증
3. Service에서 현재 인증된 계정 ID 획득
4. Service에서 계좌 조회 및 소유권 검증 (소유 투자자 본인 또는 시스템 관리자만 접근 가능)
5. 감사 로그에 접근 기록 남김
6. 평문 accountNumber 반환

### 증권계좌 수정 흐름
1. 사용자가 PATCH /securities-accounts/{id} 요청 (accountName, securitiesFirmId)
2. Controller가 입력값 검증
3. Service에서 현재 인증된 계정 ID 획득
4. Service에서 계좌 조회 및 소유권 검증
5. securitiesFirmId 변경 시 존재하는 증권사인지 확인 (400 Bad Request)
6. 필드 업데이트 및 저장, 결과 반환
7. accountNumber는 수정 불가 — 계좌번호 변경은 삭제 후 재등록으로 처리

### 증권계좌 비활성화 흐름 (논리 삭제)
1. 사용자가 DELETE /securities-accounts/{id} 요청
2. Service에서 현재 인증된 계정 ID 획득
3. Service에서 계좌 조회 및 소유권 검증
4. Service에서 해당 계좌에 연결된 금융 기록(Subscription 등)이 있는지 확인 — 있으면 400 Bad Request 반환
5. status를 INACTIVE로 변경, deletedAt 설정
6. 저장 및 204 No Content 반환

### 기본 계좌 지정 흐름
1. 사용자가 PATCH /securities-accounts/{id}/set-default 요청
2. Controller가 경로 파라미터 검증
3. Service에서 현재 인증된 계정 ID 획득
4. Service에서 계좌 조회 및 소유권 검증
5. 이미 기본 계좌인 경우 409 Conflict 반환
6. 비활성화된 계좌인 경우 400 Bad Request 반환
7. **트랜잭션 내 원자적 처리**:
   - 동일 투자자의 기존 기본 계좌(isDefault=true)의 isDefault를 false로 업데이트
   - 대상 계좌의 isDefault를 true로 업데이트
   - 단일 트랜잭션으로 모두 커밋
8. 저장 및 결과 반환

## API 설계 (엔드포인트/요청/응답/에러)

### 엔드포인트 목록
1. POST /securities-accounts - 증권계좌 등록
2. GET /securities-accounts - 증권계좌 목록 조회 (마스킹)
3. GET /securities-accounts/{id} - 증권계좌 상세 조회 (마스킹)
4. GET /securities-accounts/{id}/account-number - 계좌번호 전체 노출 조회
5. PATCH /securities-accounts/{id} - 증권계좌 수정
6. DELETE /securities-accounts/{id} - 증권계좌 비활성화 (논리 삭제)
7. PATCH /securities-accounts/{id}/set-default - 기본 계좌 지정

### 요청/응답 DTO

#### 증권계좌 등록 요청 (POST /securities-accounts)
```json
{
  "investorId": 1,
  "securitiesFirmId": 3,
  "accountNumber": "123-456-789012",
  "accountName": "개인 MTS 계좌",
  "isDefault": false
}
```

#### 증권계좌 등록 응답 (성공, 201 Created)
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

#### 증권계좌 목록/상세 조회 응답 (성공)
- 마스킹된 계좌번호(`********9012`) 포함 (상세 조회 엔드포인트는 단일 객체, 목록은 배열)
- `securitiesFirmName` 필드 포함 (증권사명 조회)

#### 증권계좌 계좌번호 노출 응답 (성공)
```json
{
  "id": 1,
  "accountNumber": "123-456-789012",
  "exposedAt": "2026-09-21T10:05:00Z"
}
```

#### 증권계좌 수정 요청 (PATCH /securities-accounts/{id})
```json
{
  "accountName": "변경된 계좌 별칭",
  "securitiesFirmId": 5
}
```

#### 기본 계좌 지정 응답 (성공)
- 증권계좌 상세 조회와 동일한 응답 구조, isDefault=true로 변경됨

### 에러 응답 형식 (공통)
```json
{
  "error": "ERROR_CODE",
  "message": "사용자에게 표시할 메시지"
}
```

#### 주요 에러 코드
- VALIDATION_ERROR: 입력값 검증 실패 (필수 값 누락, 길이 초과 등)
- NOT_FOUND: 증권계좌를 찾을 수 없음
- FORBIDDEN: 계정 소유권 없음
- CONFLICT: 이미 기본 계좌로 지정되어 있음 (409)
- BAD_REQUEST: 비활성화된 계좌 지정 시도, 존재하지 않는 증권사 참조 등

## DB 설계

### SecuritiesAccount 테이블

| 컬럼명 | 타입 | 제약 | 설명 |
|--------|------|------|------|
| id | BIGINT | PK, GENERATED ALWAYS AS IDENTITY | 내부 식별자 (Long IDENTITY) |
| investor_id | BIGINT | NOT NULL, FK → investors.id, ON DELETE RESTRICT | 투자자 외부키 |
| securities_firm_id | BIGINT | NOT NULL, FK → securities_firms.id, ON DELETE RESTRICT | 증권사 외부키 |
| account_number | VARCHAR(255) | NOT NULL | 계좌번호 (암호화 저장) |
| account_name | VARCHAR(255) | NULL 허용 | 계좌 별칭 |
| is_default | BOOLEAN | NOT NULL, DEFAULT FALSE | 기본 계좌 여부 |
| status | VARCHAR(10) | NOT NULL, DEFAULT 'ACTIVE' | 계좌 상태 (ACTIVE/INACTIVE) |
| deleted_at | TIMESTAMP | NULL 허용 | 논리 삭제 시각 |
| created_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 생성 일시 |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 수정 일시 (애플리케이션 코드에서 갱신, DB 트리거 없음) |

### 인덱스
- PRIMARY KEY (id)
- INDEX idx_securities_account_investor_id (investor_id) — 투자자별 계좌 조회 최적화
- INDEX idx_securities_account_status (status) — 상태별 조회 최적화
- **PARTIAL UNIQUE INDEX**: `UNIQUE(investor_id) WHERE is_default = TRUE` — 투자자당 기본 계좌는 정확히 하나 (PostgreSQL partial index 문법)

### 유니크 제약사항
- **부분 유니크 인덱스 (Partial Unique Index)**: PostgreSQL 문법으로 `CREATE UNIQUE INDEX uk_securities_account_default ON securities_accounts(investor_id) WHERE is_default = TRUE;`
  - **설계 근거**: 투자자당 기본 계좌(is_default=true)는 정확히 하나만 존재해야 한다. PostgreSQL의 partial index는 `WHERE` 조건절로 조건부 유니크를 구현하며, `is_default = FALSE`인 레코드들은 제약에서 제외되므로 여러 개의 비기본 계좌가 동일 investor_id를 가질 수 있다.
  - **적용 DB**: PostgreSQL. 다른 DB(MySQL 등)에서는 `(investor_id, is_default)` 복합 유니크 인덱스나 서비스 레벨 검증으로 대체 고려 가능.

### Audit 필드
- created_at, updated_at: 생성 및 수정 시간 추적
- deleted_at: 논리 삭제 시점 추적 (status=INACTIVE와 함께 설정)

### 계좌번호 암호화 저장
- **저장 전 암호화**: `account_number`는 DB에 저장되기 전 애플리케이션 레벨에서 AES-256 등의 대칭 암호화 알고리즘으로 암호화되어야 한다.
- **구현 방향**: `infrastructure.encryption.AccountNumberEncryptor` 유틸리티 클래스를 생성하며, Service 계층에서 엔티티 저장 전 `encrypt()`를 호출하고, DTO 매핑 시 `decrypt()`를 호출한다.
- **키 관리**: 암호화 키는 환경 변수 또는 전용 시크릿 관리 시스템에서 제공받으며, `.env` 파일이나 코드에 하드코딩하지 않는다.
- **복호화**: 전체 노출 엔드포인트(`/securities-accounts/{id}/account-number`)에서만 복호화하여 평문 반환하며, 이 경우 감사 로그가 기록된다.
- **마스킹**: 일반 조회 엔드포인트(목록/상세)에서는 응답 DTO 매핑 단계에서 뒷 4자리만 노출하고 나머지는 `*`로 마스킹한다 (예: `********9012`). 4자리 미만인 경우 전체 마스킹.

## 기본 계좌(is_default) 동시성 처리

### 부분 유니크 인덱스 (Partial Unique Index)

PostgreSQL의 partial index 문법을 사용하여 DB 레벨에서 투자자당 기본 계좌 1개를 보장한다:

```sql
CREATE UNIQUE INDEX uk_securities_account_default 
ON securities_accounts(investor_id) 
WHERE is_default = TRUE;
```

- 이 인덱스는 `is_default = TRUE`인 레코드에 대해서만 `investor_id`에 유니크 제약을 적용한다.
- 동일 투자자에 대해 `is_default = TRUE`인 레코드가 두 번째로 삽입/업데이트 시도하면 DB 레벨에서 유니크 위반 오류가 발생한다.
- `is_default = FALSE`인 레코드에는 제약이 적용되지 않으므로, 투자자가 여러 개의 비기본 계좌를 가질 수 있다.

### set-default 트랜잭션 처리 방식

기본 계좌 지정(`PATCH /securities-accounts/{id}/set-default`)은 다음과 같이 **단일 트랜잭션 내에서 원자적으로** 처리된다:

1. **트랜잭션 시작**: `@Transactional` 어노테이션이 적용된 Service 메서드에서 트랜잭션을 시작한다.
2. **낙관적 락 적용**: 대상 SecuritiesAccount를 조회할 때 `SELECT ... FOR UPDATE` 또는 버전 컬럼(@Version)을 이용한 낙관적 락을 적용하여 동시 요청을 차단한다.
3. **기존 기본 계좌 해제**: 동일 `investor_id`를 가진 `is_default = TRUE`인 기존 레코드의 `is_default`를 `FALSE`로 업데이트한다.
4. **신규 기본 계좌 지정**: 대상 SecuritiesAccount의 `is_default`를 `TRUE`로 업데이트한다.
5. **트랜잭션 커밋**: 두 업데이트가 모두 성공하면 트랜잭션을 커밋한다. 하나라도 실패하면 전체 롤백된다.

**동시성 시나리오**:
- 투자자가 두 개의 계좌 A, B를 동시에 기본 계좌로 지정하려고 요청하면:
  - 먼저 도착한 요청이 락을 획득하고 트랜잭션을 완료한다.
  - 나중 도착한 요청은 락 대기 후 기존 기본 계좌가 이미 false로 변경된 상태에서 처리되거나, 409 Conflict(이미 기본 계좌)를 반환한다.
- **부분 유니크 인덱스의 역할**: 트랜잭션 레이트의 동시성 문제가 발생하더라도 DB 레벨에서 최종 무결성을 보장한다. 두 트랜잭션이 동시에 `is_default = TRUE`를 삽입하려 하면 하나가 유니크 위반으로 실패한다.

### 이미 기본 계좌인 경우
- 대상 계좌의 `isDefault`가 이미 `true`이면 409 Conflict를 반환한다 (AC-015).
- 이 경우 트랜잭션을 시작하지 않고 즉시 응답한다.

## 계좌번호 암호화/마스킹 처리

### 암호화 (저장 시)
- **방식**: 애플리케이션 레벨 AES-256-GCM (또는 동등한 대칭 암호화)
- **적용 시점**: Service 계층에서 `SecuritiesAccount` 엔티티를 `Repository.save()`하기 전, `accountNumber` 필드를 암호화하여 저장
- **구현체**: `AccountNumberEncryptor` 클래스 (infrastructure 레이어)
  - `encrypt(String plainText) → String` (암호화된 문자열 반환)
  - `decrypt(String encryptedText) → String` (복호화된 평문 반환)
- **키 관리**: 환경 변수(`APP_ENCRYPTION_KEY`) 또는 시크릿 매니저에서 관리. 코드에 하드코딩 금지
- **DB에 저장되는 값**: 암호화된 텍스트 (예: `GcmCipherText:base64encoded...`)

### 마스킹 (응답 시)
- **적용 시점**: Service 계층에서 DTO로 변환하는 단계 (또는 Controller의 ResponseMapper)
- **규칙**: 계좌번호 뒷 4자리만 평문 노출, 나머지는 `*`로 대체
  - 예: `123-456-789012` → `********9012`
  - 계좌번호가 4자리 미만인 경우: 전체 `*`로 마스킹 (예: `123` → `***`)
- **적용 엔드포인트**: 목록 조회, 상세 조회, 수정 응답, 기본 계좌 지정 응답
- **미적용 엔드포인트**: `/securities-accounts/{id}/account-number` (전체 노출 — 이 경우에만 평문 반환)

### 암호화/마스킹 흐름 요약
```
[입력] "123-456-789012"
  ↓ [Service: encrypt()]
"GcmCipherText:..." (DB 저장)
  ↓ [Service: decrypt() + mask()]
"********9012" (API 응답)
```

## 참조 무결성 처리

### SecuritiesAccount.investor_id FK (ON DELETE RESTRICT)
- **ON DELETE RESTRICT**: 투자자(Investor)가 물리 삭제(DELETE) 시도 시, 해당 투자자를 참조하는 SecuritiesAccount가 존재하면 DB 레벨에서 삭제를 **차단**한다.
- **논리 삭제와의 관계**: Investor의 소프트 삭제(status=INACTIVE, deletedAt 설정)는 UPDATE이므로 FK 제약과 무관하다. 따라서 투자자가 비활성화되어도 연결된 증권계좌는 유지된다.
- **시나리오**: 투자자가 soft delete되면 → SecuritiesAccount는 그대로 유지 → 기존 금융 기록(Subscription 등)과 연결 유지

### SecuritiesAccount.securities_firm_id FK (ON DELETE RESTRICT)
- **ON DELETE RESTRICT**: 증권사(SecuritiesFirm)가 물리 삭제 시도 시, 해당 증권사를 참조하는 SecuritiesAccount가 존재하면 DB 레벨에서 삭제를 **차단**한다.
- **논리 삭제와의 관계**: SecuritiesFirm의 비활성화(status=INACTIVE, deletedAt 설정)는 UPDATE이므로 FK 제약과 무관하다. 연결된 계좌는 유지된다.
- **시나리오**: 증권사가 비활성화되면 → SecuritiesAccount는 그대로 유지 → 기존 계좌 데이터 유지, 수정 시 INACTIVE 증권사로 변경 불가 (서비스 레벨에서 ACTIVE만 허용)

### 물리 삭제 vs 논리 삭제 정리
| 작업 | FK 제약 영향 | 결과 |
|------|-------------|------|
| Investor DELETE (물리) | RESTRICT로 차단 | 500/외래키 위반 에러 |
| Investor soft delete (UPDATE status=INACTIVE) | FK와 무관 | SecuritiesAccount 유지 |
| SecuritiesFirm DELETE (물리) | RESTRICT로 차단 | 500/외래키 위반 에러 |
| SecuritiesFirm soft delete (UPDATE status=INACTIVE) | FK와 무관 | SecuritiesAccount 유지 |
| SecuritiesAccount DELETE (물리) | — | 가능하나 서비스 레벨에서 금지 (논리 삭제만 허용) |
| SecuritiesAccount soft delete (UPDATE status=INACTIVE) | FK와 무관 | deletedAt 설정, 유지 |

```
Investor (1) ───────────────────┐
  │                              │
  │ investor_id (FK, RESTRICT)   │
  │                              ▼
  │              SecuritiesAccount (N)
  │                              │
  │                              │ securities_firm_id (FK, RESTRICT)
  │                              ▼
  └────────────── SecuritiesFirm (1)
```

### SecuritiesAccount의 논리 삭제와 FK의 관계
- SecuritiesAccount 자체가 soft delete(status=INACTIVE)되어도, 다른 엔티티에서 이 계좌를 참조하는 FK는 동작한다 (RESTRICT는 대상 레코드의 물리 삭제만 방지).
- 따라서 SecuritiesAccount의 soft delete 후에도 하위 금융 기록(Subscription 등)에서 해당 account_id를 FK로 참조할 수 있다.

## 모듈 소유권

- **SecuritiesAccount 엔티티**: `domain.model.securitiesaccount` 패키지 소유
- **SecuritiesAccountRepository**: `repository.securitiesaccount` 패키지 소유
- **SecuritiesAccountController**: `controller.securitiesaccount` 패키지 소유
- **SecuritiesAccountService**: `service.securitiesaccount` 패키지 소유
- **AccountNumberEncryptor**: `infrastructure.encryption` 패키지 소유
- **DTO 클래스**: `service.securitiesaccount.dto` 또는 `controller.securitiesaccount.dto` 패키지 소유 (기존 프로젝트 DTO 규칙에 따름)
- **보안 유틸리티 (SecurityUtils)**: `util.security` 패키지 소유 (Investor 아키텍처에서 이미 정의, 재사용)

## 의존성

새로 추가해야 할 라이브러리:
- `org.springframework.boot:spring-boot-starter-data-jpa` — Spring Data JPA (이미 Investor/SecuritiesFirm에서 사용 중, 재사용)
- `org.hibernate.orm:hibernate-core` — JPA 구현체 (이미 사용 중, 재사용)
- **암호화 라이브러리**: Java 표준 `javax.crypto.Cipher` (AES-256-GCM) — 추가 의존성 불필요 (Java SDK 내장)
  - 참고: BouncyCastle 등 추가 라이브러리 사용 시 `org.bouncycastle` 의존성 추가 가능하나, MVP에서는 JDK 내장 `javax.crypto`로 충분

## Flyway 마이그레이션 파일명 제안

기존 마이그레이션 순서를 가정 (V1=accounts, V2=investors, V3=securities_firms)했을 때, SecuritiesAccount 테이블 생성 마이그레이션은 다음 파일을 제안한다:

### V4__create_securities_accounts_table.sql (제안)

> **수정(Master)**: 원안이 MySQL 문법(`AUTO_INCREMENT`, `ENGINE=InnoDB`)과 PostgreSQL 전용 partial index(`WHERE` 절)를 한 `CREATE TABLE`에 섞어 놓아 어느 DB에서도 실행되지 않았다. 이 프로젝트는 PostgreSQL 기준이므로 `GENERATED ALWAYS AS IDENTITY`를 사용하고, partial unique index는 별도의 `CREATE UNIQUE INDEX` 문으로 분리한다.

```sql
CREATE TABLE securities_accounts (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    investor_id BIGINT NOT NULL
        REFERENCES investors(id) ON DELETE RESTRICT,
    securities_firm_id BIGINT NOT NULL
        REFERENCES securities_firms(id) ON DELETE RESTRICT,
    account_number VARCHAR(255) NOT NULL,
    account_name VARCHAR(255) NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
    deleted_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_securities_account_investor_id ON securities_accounts(investor_id);
CREATE INDEX idx_securities_account_status ON securities_accounts(status);

-- 투자자당 기본 계좌(is_default=TRUE)는 정확히 하나만 허용 (PostgreSQL partial unique index)
CREATE UNIQUE INDEX uk_securities_account_default
    ON securities_accounts(investor_id)
    WHERE is_default = TRUE;
```

> **주의**: 실제로는 기존 마이그레이션 번호를 확인한 뒤 이어지는 번호를 사용한다. V4 제안은 V1=accounts, V2=investors, V3=securities_firms 순서를 전제로 한 것이다.

### 추가 마이그레이션 (별도 버전, 필요 시)
- FK 제약이 별도 마이그레이션에서 추가되는 경우 (SecuritiesFirm 테이블 생성 이후 SecuritiesAccount FK 추가 시), `V5__add_securities_account_fk.sql` 등으로 제안.

## 리스크

1. **부분 유니크 인덱스 DB 호환성**: PostgreSQL의 `WHERE` 절 partial index는 MySQL 등 다른 DB에서 지원되지 않음. MySQL에서는 `(investor_id, is_default)` 복합 유니크 인덱스로 대체 필요 — 단, `is_default = FALSE`인 레코드가 여러 개 허용되어야 하므로 복합 유니크는 MySQL에서 동일 효과를 내지 못함. MySQL 사용 시 서비스 레벨 검증이 추가로 필요.
2. **계좌번호 암호화 키 관리**: 애플리케이션 레벨 암호화의 핵심은 키 관리. 키 유출 시 모든 계좌번호가 노출되므로, 시크릿 관리 시스템(예: AWS KMS, Vault) 활용을 권장한다.
3. **마스킹 누락 위험**: 모든 응답 DTO에서 마스킹이 적용되었는지 검증해야 한다. 하나의 엔드포인트에서 마스킹을 누락하면 계좌번호가 평문으로 노출된다. 통합 테스트에서 반드시 검증.
4. **기본 계좌 동시성**: 부분 유니크 인덱스가 DB 레벨에서 무결성을 보장하지만, 동시 요청 시 409 Conflict 응답이 필요한 경우 서비스 레벨에서 사전 검증 로직과 DB 레벨 제약이 이중으로 작동.
5. **논리 삭제된 기본 계좌**: `status=INACTIVE`인 계좌의 `is_default`가 `true`일 수 있음 — 기본 계좌 지정 시 비활성화된 계좌를 선택하면 안 되므로, Service에서 `is_default=true AND status='ACTIVE'` 조건으로 기존 기본 계좌를 해제해야 함.
6. **FK ON DELETE RESTRICT의 오용 방지**: 물리 DELETE를 실수로 호출하면 500 에러가 발생하므로, Repository에 물리 삭제 메서드를 노출하지 않아야 한다. 소프트 삭제만 허용하는 Service 메서드만 제공.

## 결정 필요 사항

1. **암호화 알고리즘 확정**: AES-256-GCM 외 대안(예: RSA 비대칭 혼합) 검토 필요 — MVP에서는 AES-256-GCM으로 확정 권장
2. **MySQL 호환성**: 프로젝트가 PostgreSQL만 사용하는지, 또는 MySQL도 지원해야 하는지 — partial index가 MySQL에서 필요하면 서비스 레벨 검증 추가
3. **기본 계좌 자동 지정 정책**: 투자자의 첫 번째 계좌 등록 시 `isDefault=true`로 자동 설정할지 (spec에서 기본값: 첫 계좌 등록 시 자동으로 isDefault=true 설정) — 서비스 로직에 반영
4. **비활성화된 기본 계좌 처리**: `is_default=true`인 레코드가 `status=INACTIVE`가 된 경우, 새로운 기본 계좌 지정 시 이 레코드를 해제하는지, 아니면 가장 최근 생성된 ACTIVE 계좌를 기본으로 할지
5. **마스킹 패턴 확정**: 뒷 4자리 마스킹이 아니라 다른 패턴(예: 앞 4자리 + 뒷 4자리 노출)이 필요할 경우 spec과 조율
