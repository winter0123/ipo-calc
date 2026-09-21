# 아키텍처 설계: 증권사 기준정보 관리 (securities-firm-catalog)

> **식별자 결정**: 본 기능은 Investor 아키텍처 문서(D-I-001)와 동일하게 **Long(IDENTITY)** 식별자를 사용한다. 기존 Account 엔티티가 `@GeneratedValue(strategy = GenerationType.IDENTITY)`의 `Long id`를 사용 중이므로 코드베이스 일관성을 위해 UUID를 사용하지 않는다. DB 스키마에서도 `id`/`securities_firm_id` 컬럼은 BIGINT로 구현한다.

## 아키텍처 개요 (레이어 구조)

Investor 관리와 동일한 관심사 분리 원칙을 따른다. Spring Boot 백엔드는 다음 레이어 구조를 사용한다:

```
controller
application/service
domain
repository
infrastructure
```

- **controller**: HTTP 요청 처리, 입력 검증, 응답 매핑. 복잡한 업무 규칙은 포함하지 않음.
- **application/service**: 업무 규칙 유스케이스 오케스트레이션, 트랜잭션 경계. 증권사명 중복 검사·상태 전이 검증 로직 포함.
- **domain**: 엔티티, 밸류오브젝트, 도메인 이벤트, 불변식. SecuritiesFirm 엔티티 정의.
- **repository**: 데이터 접근 계층, Spring Data JPA 인터페이스 정의.
- **infrastructure**: 외부 시스템 연동, 설정, 공통 인프라 컴포넌트.

증권사 기준정보 관련 컴포넌트는 다음과 같이 구성한다:
- `controller.securitiesfirm`: SecuritiesFirmController
- `service.securitiesfirm`: SecuritiesFirmService
- `domain.model.securitiesfirm`: SecuritiesFirm 엔티티
- `repository.securitiesfirm`: SecuritiesFirmRepository

> **참고**: SecuritiesFirm은 사용자(Account) 소유 엔티티가 아니라 글로벌 기준정보이므로 Investor에서 사용하는 계정 소유권 검증(SecurityUtils)은 적용하지 않는다. 모든 인증된 사용자에게 동일하게 노출되는 공통 기준정보다.

## 데이터 흐름

### 증권사 등록 흐름
1. 사용자가 POST /securities-firms 요청 (name, externalCode, businessRegistrationNumber 선택)
2. Controller가 입력값 검증 (name 필수, 길이 255자 이하)
3. Service에서 name 기준 활성 상태(ACTIVE) 증권사 중복 검사
4. 중복이면 409 Conflict 반환
5. SecuritiesFirm 엔티티 생성 (status=ACTIVE), 저장
6. 저장된 정보 반환 (201 Created)

### 증권사 목록 조회 흐름
1. 사용자가 GET /securities-firms 요청
2. Service에서 삭제되지 않은(deletedAt=NULL) 모든 증권사 목록 조회
3. 목록 반환

### 증권사 상세 조회 흐름
1. 사용자가 GET /securities-firms/{id} 요청
2. Service에서 삭제되지 않은 증권사 조회 (findById + deletedAt 확인)
3. 없거나 논리 삭제 상태이면 404 Not Found 반환
4. 성공 시 증권사 정보 반환

### 증권사 수정 흐름
1. 사용자가 PATCH /securities-firms/{id} 요청
2. Controller가 입력값 검증 (name이 제공되면 비어 있지 않은지 확인)
3. Service에서 대상 증권사 조회 (없으면 404)
4. name 변경 시 활성 상태(ACTIVE) 대상 중복 검사 (자기 자신 제외)
5. 중복이면 409 Conflict 반환
6. 필드 업데이트 및 저장, 결과 반환

### 증권사 비활성화 흐름 (논리 삭제)
1. 사용자가 DELETE /securities-firms/{id} 요청
2. Service에서 대상 증권사 조회 (없으면 404)
3. status를 INACTIVE로 변경, deletedAt 설정
4. 저장 및 204 No Content 반환
5. 연결된 SecuritiesAccount가 있어도 항상 성공 — 연관 계좌에 대한 물리 삭제와 FK 제약은 사용하지 않으므로 논리 삭제는 무조건 허용된다

## API 설계

### 엔드포인트 목록
1. POST /securities-firms - 증권사 등록
2. GET /securities-firms - 증권사 목록 조회
3. GET /securities-firms/{id} - 증권사 상세 조회
4. PATCH /securities-firms/{id} - 증권사 수정
5. DELETE /securities-firms/{id} - 증권사 비활성화 (논리 삭제)

### 요청/응답 DTO (JPA 엔티티 직접 노출 금지, DTO 사용)

#### 증권사 등록 요청 (POST /securities-firms)
```json
{
  "name": "증권사명",
  "externalCode": "외부표준코드 (선택)",
  "businessRegistrationNumber": "사업자등록번호 (선택)"
}
```

#### 증권사 등록 응답 (성공, 201 Created)
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

#### 증권사 목록 조회 응답 (성공)
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

#### 증권사 수정 요청 (PATCH /securities-firms/{id})
```json
{
  "name": "변경된증권사명",
  "externalCode": "변경된외부표준코드",
  "businessRegistrationNumber": "변경된사업자등록번호"
}
```

#### 증권사 비활성화 응답 (성공, 204 No Content)
- 본문 없음. status=INACTIVE, deletedAt 설정.

### 에러 응답 형식 (Investor 아키텍처와 공통 형식 사용)
```json
{
  "error": "ERROR_CODE",
  "message": "사용자에게 표시할 메시지"
}
```

#### 주요 에러 코드
- VALIDATION_ERROR: 입력값 검증 실패 (name 누락, 255자 초과 등)
- DUPLICATE_NAME: 활성 상태(ACTIVE) 증권사명 중복
- NOT_FOUND: 증권사를 찾을 수 없음 (미등록 또는 논리 삭제된 레코드)

## DB 설계

### SecuritiesFirm 테이블
| 컬럼명 | 타입 | 제약 | 설명 |
|--------|------|------|------|
| id | BIGINT | PK, GENERATED ALWAYS AS IDENTITY | 증권사 고유 식별자 (Long IDENTITY) |
| name | VARCHAR(255) | NOT NULL | 증권사명 |
| external_code | VARCHAR | NULL 허용 | 외부 표준코드 (금융투자협회 표준코드 등) |
| business_registration_number | VARCHAR | NULL 허용 | 사업자등록번호 |
| status | VARCHAR(10) | NOT NULL, DEFAULT 'ACTIVE' | 증권사 상태 (ACTIVE/INACTIVE) |
| deleted_at | TIMESTAMP | NULL 허용 | 논리 삭제 시각 |
| created_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 생성 일시 |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 수정 일시 (애플리케이션 코드에서 갱신, DB 트리거 없음) |

### 인덱스
- PRIMARY KEY (id)
- UNIQUE INDEX uk_securities_firm_name_active (name, status) — 활성 상태(ACTIVE)에서만 중복 방지
  - **설계 근거**: INACTIVE(논리 삭제)된 증권사는 동일 이름 재등록이 가능해야 하므로, name 단독 UNIQUE 대신 `(name, status)` 복합 유니크로 활성 증권사 간의 중복만 방지한다.
  - MySQL 등 `deleted_at IS NULL` 조건부 유니크 인덱스(partial index) 미지원 DB에서는 `(name, status)` 복합 유니크가 동일 효과를 낸다.
- INDEX idx_securities_firm_status (status) — 상태별 조회 최적화

### 유니크 제약사항
- (name, status) 복합 유니크: status='ACTIVE'인 레코드 간에만 name 중복 방지
- INACTIVE 레코드는 유니크 제약에 영향받지 않음 (동일 이름 논리 삭제 → 재등록 가능)

### Audit 필드
- created_at, updated_at: 생성 및 수정 시간 추적
- deleted_at: 논리 삭제 시점 추적 (status=INACTIVE와 함께 설정)

## 참조 무결성 처리

### SecuritiesAccount.securities_firm_id FK
- **ON DELETE RESTRICT** 제약으로 물리 삭제를 DB 레벨에서도 강제한다.
- 증권계좌(SecuritiesAccount)가 참조 중인 증권사는 FK 제약에 의해 물리 삭제(레코드 완전 제거)가 **DB 레벨에서 차단**된다. 애플리케이션 로직만으로 방지하는 방어뿐 아니라, DDL 제약으로 이중 방어가 된다.
- **논리 삭제(status=INACTIVE, deletedAt 설정)는 FK 제약과 무관하게 항상 가능하다.** ON DELETE RESTRICT는 레코드 삭제(물리 삭제)만 제한하며 UPDATE를 통한 status 변경은 막지 않으므로, 연결된 계좌가 있어도 비활성화는 항상 성공한다(spec AC-012).

```
SecuritiesAccount (Many) --securities_firm_id FK (ON DELETE RESTRICT)--> SecuritiesFirm (One)
```

- 증권계좌는 정확히 하나의 증권사를 참조한다.
- 증권사 비활성화 시 기존 증권계좌는 그대로 유지된다 (논리 삭제는 계좌 데이터를 건드리지 않음).
- INACTIVE 증권사는 신규 계좌 등록 시 선택 목록에서 제외된다 (spec AC-014) — 목록 조회 시 ACTIVE 상태만 필터링하여 해결.

## Flyway 마이그레이션

기존 마이그레이션 순서를 가정 (V1=accounts, V2=investors)했을 때 다음 파일을 추가한다.

> **주의**: 실제로는 기존 마이그레이션 번호를 확인한 뒤 이어지는 번호를 사용한다. V3 제안은 V1, V2가 각각 accounts/investors임을 전제로 한 것이며, 실제 적용 시 마지막 버전을 확인해 번호를 맞춘다.

### V3__create_securities_firms_table.sql (제안)

> **수정(2026-09-21, Master)**: 원안이 MySQL 문법(`AUTO_INCREMENT`, `ENGINE=InnoDB`)을 사용했으나 이 프로젝트는 PostgreSQL 기준이므로 `GENERATED ALWAYS AS IDENTITY`로 교체한다.

```sql
CREATE TABLE securities_firms (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    external_code VARCHAR(255) NULL,
    business_registration_number VARCHAR(255) NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
    deleted_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_securities_firm_name_active ON securities_firms(name, status);
CREATE INDEX idx_securities_firm_status ON securities_firms(status);
```

### SecuritiesAccount FK 추가 마이그레이션 (참조)
SecuritiesAccount 테이블에 securities_firm_id 컬럼이 추가될 시점의 마이그레이션(별도 버전)에서 아래 제약을 정의한다.
```sql
ALTER TABLE securities_accounts
    ADD CONSTRAINT fk_securities_account_firm
    FOREIGN KEY (securities_firm_id) REFERENCES securities_firms (id)
    ON DELETE RESTRICT;
```

## 모듈 소유권

- **SecuritiesFirm 엔티티**: domain.model.securitiesfirm 패키지 소유
- **SecuritiesFirmRepository**: repository.securitiesfirm 패키지 소유
- **SecuritiesFirmController**: controller.securitiesfirm 패키지 소유
- **SecuritiesFirmService**: service.securitiesfirm 패키지 소유
- **DTO 클래스**: service.securitiesfirm.dto 또는 controller.securitiesfirm.dto 패키지 소유 (기존 프로젝트 DTO 규칙에 따름)

## 의존성

새로 추가해야 할 라이브러리는 없다. Investor 아키텍처에서 이미 사용하는 Spring Data JPA(spring-boot-starter-data-jpa)와 Hibernate(JPA 구현체)를 그대로 재사용한다.

## 리스크

1. **(name, status) 복합 유니크 대안**: 외부 표준코드/사업자등록번호의 중복 정책은 MVP에서 검사하지 않음 — 추후 실데이터 기준으로 정책 재검토 가능
2. **FK ON DELETE RESTRICT 의존**: 논리 삭제가 애플리케이션 로직(Service)에서만 수행된다는 점에서, 실수로 물리 delete를 호출하면 500/외래키 위반 오류가 발생하므로 Repository에 물리 삭제 메서드를 노출하지 않아야 함
3. **INACTIVE 재등록 여부 정책**: 동일 이름 재등록 허용 여부는 spec/조사에서 허용 방향으로 확정 — 추후 정책 변경 시 유니크 제약 재설계 필요
4. **논리 삭제 필터 누락 위험**: 목록/상세 조회에서 deletedAt(deletedAt IS NOT NULL) 필터를 누락하면 삭제된 증권사가 노출될 수 있음. Repository 쿼리에서 항상 필터가 적용되도록 설계
5. **이름 길이/특수문자**: 255자 초과·특수문자 처리 — VALIDATION_ERROR로 방어

## 결정 필요 사항

1. **(name, status) 복합 유니크 대안 확정**: 활성 증권사 간 이름 중복 방지를 DB 유니크 제약으로 강제할지(권장), Service 계층 검증으로만 할지
2. **external_code/business_registration_number 중복 검사**: MVP 범위에서 제외(검사하지 않음)할지, 추후 검사 추가할지
3. **조회 필터 기본값**: 목록 조회 시 ACTIVE/INACTIVE 전부 노출할지, ACTIVE만 노출할지 (spec은 "삭제된 증권사 제외"만 명시 — status=INACTIVE이지만 deletedAt이 설정된 레코드는 조회 제외가 기본)