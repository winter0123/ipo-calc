# 아키텍처 설계: IPO 기준정보 관리 (ipo-catalog)

> **식별자 결정**: 본 기능은 SecuritiesFirm/Investor 아키텍처와 동일하게 **Long(IDENTITY)** 식별자를 사용한다. 기존 Account 엔티티가 `@GeneratedValue(strategy = GenerationType.IDENTITY)`의 `Long id`를 사용 중이므로 코드베이스 일관성을 위해 UUID를 사용하지 않는다. DB 스키마에서도 `id`/`ipo_id` 컬럼은 BIGINT로 구현한다.

## 아키텍처 개요 (레이어 구조)

SecuritiesFirm 관리와 동일한 관심사 분리 원칙을 따른다. Spring Boot 백엔드는 다음 레이어 구조를 사용한다:

```
controller
application/service
domain
repository
infrastructure
```

- **controller**: HTTP 요청 처리, 입력 검증(필수값/날짜 순서/가격 범위), 응답 매핑. 복잡한 업무 규칙은 포함하지 않음.
- **application/service**: 업무 규칙 유스케이스 오케스트레이션, 트랜잭션 경계. IPO 중복 검사·상태 전이 검증·CSV 파싱 및 행별 저장 로직 포함.
- **domain**: 엔티티, 밸류오브젝트, 도메인 이벤트, 불변식. Ipo 엔티티 및 상태(ACTIVE/INACTIVE) 상수 정의.
- **repository**: 데이터 접근 계층, Spring Data JPA 인터페이스 정의.
- **infrastructure**: 외부 시스템 연동, 설정, 공통 인프라 컴포넌트 (CSV 파싱 유틸 등).

IPO 기준정보 관련 컴포넌트는 다음과 같이 구성한다:
- `controller.ipo`: IpoController
- `service.ipo`: IpoService
- `domain.model.ipo`: Ipo 엔티티
- `repository.ipo`: IpoRepository

> **참고**: Ipo는 사용자(Account) 소유 엔티티가 아니라 글로벌 기준정보이므로 SecuritiesFirm과 동일하게 계정 소유권 검증(SecurityUtils)은 적용하지 않는다. 모든 인증된 사용자에게 동일하게 노출되는 공통 기준정보다.

## 데이터 흐름

### IPO 등록 흐름
1. 사용자가 POST /ipos 요청 (companyName, ipoName, subscriptionStartDate, subscriptionEndDate 필수; stockCode, roundLabel, lowerBandPrice, upperBandPrice, fixedPrice, listingDate, offeringShareCount 선택)
2. Controller가 입력값 검증 (필수값 누락 400, 청약 종료일 < 시작일 400, 하한 > 상한 400, 음수 가격 400, 255자 초과 400)
3. Service가 활성 상태(ACTIVE) 중복 검사 — stockCode 기준 또는 (ipoName, subscriptionStartDate) 조합 기준 (아래 "중복 검사 로직" 참고)
4. 중복이면 409 Conflict 반환 (DUPLICATE_IPO)
5. Ipo 엔티티 생성 (status=ACTIVE), 저장
6. 저장된 정보 반환 (201 Created)

### IPO 목록 조회 흐름
1. 사용자가 GET /ipos 요청
2. Service에서 삭제되지 않은(deletedAt IS NULL) 모든 IPO 목록 조회
3. 목록 반환 (createdAt 내림차순 등 안정적 정렬)

### IPO 상세 조회 흐름
1. 사용자가 GET /ipos/{id} 요청
2. Service에서 삭제되지 않은 IPO 조회 (findById + deletedAt 확인)
3. 없거나 논리 삭제 상태이면 404 Not Found 반환
4. 성공 시 IPO 정보 반환

### IPO 수정 흐름
1. 사용자가 PATCH /ipos/{id} 요청
2. Controller가 입력값 검증 (제공된 필드만 수정, 유효성 오류 시 400)
3. Service에서 대상 IPO 조회 (없으면 404)
4. 수정값 기준 활성 상태(ACTIVE) 중복 검사 (자기 자신 제외)
5. 중복이면 409 Conflict 반환
6. 필드 업데이트 및 저장, 결과 반환

### IPO 비활성화 흐름 (논리 삭제)
1. 사용자가 DELETE /ipos/{id} 요청
2. Service에서 대상 IPO 조회 (없으면 404)
3. status를 INACTIVE로 변경, deletedAt 설정
4. 저장 및 204 No Content 반환
5. 연결된 Subscription이 있어도 항상 성공 — 물리 삭제가 아니라 status UPDATE이므로 FK(ON DELETE RESTRICT) 제약과 무관하게 허용된다 (spec AC-014, AC-015). 기존 Subscription은 그대로 유지됨.

### IPO CSV 일괄 등록 흐름
1. 사용자가 POST /ipos/import 요청 (multipart/form-data, file 필드에 CSV)
2. Controller가 파일 존재/크기/빈 파일 여부 확인 (빈 파일·크기 상한 초과 시 400)
3. Service가 헤더 검증 → 행 단위 파싱 → DTO 변환 → 행별 저장 시도
4. 각 행은 유효성 실패 시 실패 사유를 모으고, 성공 행은 개별 INSERT (한 행 실패가 전체를 롤백하지 않음 — spec AC-019)
5. successCount / failureCount / failures[](행 번호 + 사유) 집계 반환

## API 설계

### 엔드포인트 목록
1. POST /ipos - IPO 등록
2. GET /ipos - IPO 목록 조회
3. GET /ipos/{id} - IPO 상세 조회
4. PATCH /ipos/{id} - IPO 수정
5. DELETE /ipos/{id} - IPO 비활성화 (논리 삭제)
6. POST /ipos/import - IPO CSV 일괄 등록

### 요청/응답 DTO (JPA 엔티티 직접 노출 금지, DTO 사용)

#### IPO 등록 요청 (POST /ipos)
```json
{
  "companyName": "기업명",
  "ipoName": "공모주명",
  "stockCode": "123456 (선택)",
  "roundLabel": "재상장 (선택)",
  "lowerBandPrice": 10000,
  "upperBandPrice": 12000,
  "fixedPrice": 11000 (선택, 확정 전 null),
  "subscriptionStartDate": "2026-10-01",
  "subscriptionEndDate": "2026-10-02",
  "listingDate": "2026-10-15 (선택, 미정 시 null)",
  "offeringShareCount": 1000000 (선택)
}
```

#### IPO 등록 응답 (성공, 201 Created)
```json
{
  "id": 1,
  "companyName": "기업명",
  "ipoName": "공모주명",
  "stockCode": "123456",
  "roundLabel": "재상장",
  "lowerBandPrice": 10000,
  "upperBandPrice": 12000,
  "fixedPrice": 11000,
  "subscriptionStartDate": "2026-10-01",
  "subscriptionEndDate": "2026-10-02",
  "listingDate": "2026-10-15",
  "offeringShareCount": 1000000,
  "status": "ACTIVE",
  "createdAt": "2026-09-20T10:00:00Z",
  "updatedAt": "2026-09-20T10:00:00Z"
}
```

#### IPO 목록 조회 응답 (성공)
```json
[
  {
    "id": 1,
    "companyName": "기업명",
    "ipoName": "공모주명",
    "stockCode": "123456",
    "roundLabel": null,
    "lowerBandPrice": 10000,
    "upperBandPrice": 12000,
    "fixedPrice": 11000,
    "subscriptionStartDate": "2026-10-01",
    "subscriptionEndDate": "2026-10-02",
    "listingDate": "2026-10-15",
    "offeringShareCount": 1000000,
    "status": "ACTIVE",
    "createdAt": "2026-09-20T10:00:00Z",
    "updatedAt": "2026-09-20T10:00:00Z"
  }
]
```

#### IPO 수정 요청 (PATCH /ipos/{id}) — 제공된 필드만 갱신
```json
{
  "companyName": "변경된기업명",
  "ipoName": "변경된공모주명",
  "stockCode": "변경된종목코드",
  "roundLabel": "변경된회차라벨",
  "lowerBandPrice": 11000,
  "upperBandPrice": 13000,
  "fixedPrice": 12000,
  "subscriptionStartDate": "2026-10-01",
  "subscriptionEndDate": "2026-10-03",
  "listingDate": "2026-10-16",
  "offeringShareCount": 2000000
}
```

#### IPO 비활성화 응답 (성공, 204 No Content)
- 본문 없음. status=INACTIVE, deletedAt 설정.

#### CSV 일괄 등록 응답 (성공/부분 성공)
```json
{
  "successCount": 1,
  "failureCount": 1,
  "failures": [
    { "rowNumber": 3, "reason": "청약 종료일이 시작일보다 빠름" }
  ]
}
```

### 에러 응답 형식 (Investor/SecuritiesFirm 아키텍처와 공통 형식 사용)
```json
{
  "error": "ERROR_CODE",
  "message": "사용자에게 표시할 메시지"
}
```

#### 주요 에러 코드
- VALIDATION_ERROR: 입력값 검증 실패 (필수값 누락, 날짜 순서 오류, 가격 범위 오류, 255자 초과 등)
- DUPLICATE_IPO: 활성 상태(ACTIVE) IPO 중복 (동일 stockCode 또는 동일 ipoName+subscriptionStartDate)
- NOT_FOUND: IPO를 찾을 수 없음 (미등록 또는 논리 삭제된 레코드)
- INVALID_CSV: 빈 파일/헤더 오류/파싱 불가 (400)
- FILE_TOO_LARGE: 파일 크기 상한 초과 (400)

## DB 설계

### Ipo 테이블
| 컬럼명 | 타입 | 제약 | 설명 |
|--------|------|------|------|
| id | BIGINT | PK, GENERATED ALWAYS AS IDENTITY | IPO 고유 식별자 (Long IDENTITY) |
| company_name | VARCHAR(255) | NOT NULL | 기업명 |
| ipo_name | VARCHAR(255) | NOT NULL | 공모주명 |
| stock_code | VARCHAR(255) | NULL 허용 | 종목코드 (동일 종목명 회차 구분용, 중복 검사에서 null 제외) |
| round_label | VARCHAR(255) | NULL 허용 | 회차 구분 라벨 (종목코드 대안/보완용) |
| lower_band_price | BIGINT | NULL 허용 | 희망공모가 하한 (원 단위) |
| upper_band_price | BIGINT | NULL 허용 | 희망공모가 상한 (원 단위) |
| fixed_price | BIGINT | NULL 허용 | 확정공모가 (원 단위, 확정 전 null) |
| subscription_start_date | DATE | NOT NULL | 청약 시작일 |
| subscription_end_date | DATE | NOT NULL | 청약 종료일 |
| listing_date | DATE | NULL 허용 | 상장일 (미정 시 null) |
| offering_share_count | BIGINT | NULL 허용 | 공모주식수 (입력 시 0보다 커야 함) |
| status | VARCHAR(10) | NOT NULL, DEFAULT 'ACTIVE' | IPO 상태 (ACTIVE/INACTIVE) |
| deleted_at | TIMESTAMP | NULL 허용 | 논리 삭제 시각 |
| created_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 생성 일시 |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 수정 일시 (JPA @LastModifiedDate로 갱신) |

### 인덱스
- PRIMARY KEY (id)
- INDEX idx_ipo_status (status) — 목록 조회 및 신규 청약 IPO 선택 목록(ACTIVE 필터링) 최적화
- INDEX idx_ipo_stock_code (stock_code) — stockCode 기준 증가 검사 최적화
- INDEX idx_ipo_name_start_date (ipo_name, subscription_start_date) — (ipoName, subscriptionStartDate) 조합 중복 검사 최적화

> **부분/조건부 유니크 인덱스 결정**: 사양상 중복 검사 대상은 **활성(ACTIVE) 레코드**이며 INACTIVE(논리 삭제) 레코드는 중복 검사에서 제외된다. PostgreSQL은 조건부 유니크 인덱스(partial unique index)를 지원하므로, DB 레벨 제약으로 검사하려면 두 개의 조건부 유니크 인덱스를 별도 `CREATE UNIQUE INDEX` 문으로 정의한다(자세한 SQL은 V5 마이그레이션 참고).

## 중복 검사 로직

spec AC-005 / AC-006을 반영한다. **중복은 항상 활성 상태(ACTIVE) 레코드에 대해서만 검사**하며, INACTIVE(논리 삭제) 레코드는 검사 대상에서 제외한다.

| 조건 | 중복 키 | 비고 |
|------|---------|------|
| stock_code 존재 | stock_code 단일 기준 | 동일 종목코드를 가진 ACTIVE IPO가 있으면 409 (AC-005) |
| stock_code 없음  | (ipo_name, subscription_start_date) 조합 | 동일 공모주명+청약 시작일인 ACTIVE IPO가 있으면 409 (AC-005) |

- **stock_code가 null이면 중복 검사에서 제외**하고 ipo_name+subscriptionStartDate 조합으로 검사한다 (spec 엣지케이스).
- **roundLabel은 중복 검사에 노출되지 않는다** — 종목코드 대안/보완용 구분 라벨이므로 공모주명+시작일 조합으로 판단한다 (spec AC-006, 엣지케이스: "회차 라벨이 null인 경우 중복 검사에 영향 없음").
- **등록(AC-005)과 수정(AC-012) 모두 동일 규칙**을 적용한다. 수정 시 자신의 id는 제외하고 검사한다.
- **삭제된 IPO 재등록**: status=INACTIVE(+deletedAt 설정)인 레코드는 중복 검사에서 제외되므로, 동일 종목코드/공모주명으로 재등록이 가능하다 — SecuritiesFirm의 (name, status) 복합 유니크 정책과 동일한 논리.

### Service 계층 검증 vs DB 제약 (이중 방어)
- **Service 계층**: 트랜잭션 안에서 위 규칙을 구현한다. 등록/수정 진입점(단건 + CSV 행별)마다 동일한 검증 헬퍼를 재사용한다.
- **DB 제약(부분 유니크)**: 데이터 무결성 이중 방어로 조건부 유니크 인덱스를 추가한다. 단, 조건부 유니크는 완전히 동일한 동작을 보장하지 못하는 경우가 있으므로(stock_code가 NULL인 레코드는 `(ipo_name, start, status)` 조합 비교 시 NULL 비교 문제) **Service 계층 검증을 1차 방어선으로 삼고, DB 유니크는 보조 제약으로 둔다.** 두 방식을 함께 쓰되 구현 단계에서 실데이터 기준으로 최종 확인한다.
  - 예: `CREATE UNIQUE INDEX uk_ipo_stock_code_active ON ipos (stock_code) WHERE status = 'ACTIVE' AND stock_code IS NOT NULL;` — stock_code가 있는 ACTIVE 레코드 간 중복을 부분 유니크로 차단.
  - `(ipo_name, subscription_start_date)` 조합의 경우 PostgreSQL의 NULL 처리상 `status = 'ACTIVE'` 조건부 유니크로 같은 조합 중복을 차단할 수 있다. 단 첫 번째 규칙(stock_code 우선)과의 상호작용(같은 회사 재상장 등)은 구현 단계에서 TC로 반드시 검증한다.

## CSV 일괄 등록 처리

### 동작 원리
1. **헤더 검증**: 필수 헤더(companyName, ipoName, subscriptionStartDate, subscriptionEndDate)가 누락되면 400 (AC-020).
2. **파싱 실패 처리**: 빈 파일, 헤더 오류, 파일 자체 파싱 불가 → 400 (AC-020). 그외 **행 단위 유효성 오류는 전체 롤백하지 않는다** (AC-019).
3. **행별 처리**: 각 행을 DTO로 파싱 → 단건 등록과 동일한 유효성/중복 검증 → 성공 시 개별 저장, 실패 시 (rowNumber, reason) 수집.
4. **집계 반환**: successCount, failureCount, failures[](rowNumber, reason).

### 유효성 오류 예시 (행 실패 처리 대상)
- 필수값 누락 (companyName/ipoName/청약 시작일·종료일)
- 날짜 형식이 ISO-8601(YYYY-MM-DD)이 아닌 경우 (spec 엣지케이스)
- 청약 종료일이 시작일보다 빠른 경우
- 숫자 필드에 숫자가 아닌 값이 있는 경우 (spec 엣지케이스)
- 가격이 음수인 경우, 하한 > 상한인 경우
- 255문자 초과 기업명/공모주명
- 기존 활성 ACTIVE IPO와 중복되는 경우

### 트랜잭션 경계
- **행 단위 저장**(각 행마다 개별 트랜잭션/저장)을 원칙으로 하여 한 행의 실패가 다른 행의 성공 저장에 영향을 주지 않는다 (AC-019).
- CSV 파일 자체의 파싱/헤더 오류는 파일 전체 레벨 오류로 400을 반환한다 (AC-020).
- 중복 검사는 행별로 즉시 수행하므로, 파일 내 동일 종목코드 중복 행도 두 번째 행은 실패로 집계된다 (첫 행 저장 후 중복 감지).

### 파일 크기 상한 (스펙에서 아키텍처 확정 위임)
- 기본 상한은 **5MB**로 제안한다. Spring Boot `spring.servlet.multipart.max-file-size` 설정으로 적용한다. 백만 행 수준의 대용량 파일은 요구 범위 밖이므로 MVP 불필요.

## 참조 무결성 처리

### Subscription.ipo_id FK
- **ON DELETE RESTRICT** 제약으로 물리 삭제를 DB 레벨에서도 강제한다.
- Subscription이 참조 중인 IPO는 FK 제약에 의해 물리 삭제(레코드 완전 제거)가 **DB 레벨에서 차단**된다. 애플리케이션 로직만으로 방지하는 방어뿐 아니라, DDL 제약으로 이중 방어가 된다.
- **논리 삭제(status=INACTIVE, deletedAt 설정)는 FK 제약과 무관하게 항상 가능하다.** ON DELETE RESTRICT는 레코드 삭제(물리 삭제)만 제한하며 UPDATE를 통한 status 변경은 막지 않으므로, 연결된 Subscription이 있어도 비활성화는 항상 성공한다 (spec AC-014, AC-015).

```
Subscription (Many) --ipo_id FK (ON DELETE RESTRICT)--> Ipo (One)
```

- 청약은 정확히 하나의 IPO를 참조한다.
- IPO 비활성화 시 기존 Subscription은 그대로 유지된다 (논리 삭제는 청약 데이터를 건드리지 않음).
- INACTIVE IPO는 신규 청약 등록 시 선택 목록에서 제외된다 (spec AC-017) — 신규 청약 등록에서 ACTIVE 상태 IPO만 노출하도록 조회를 필터링하여 해결.

## Flyway 마이그레이션

기존 마이그레이션 순서를 가정 (V1=accounts, V2=investors, V3=securities_firms, V4=securities_accounts)했을 때 다음 파일을 추가한다.

> **주의**: 실제로는 기존 마이그레이션 번호를 확인한 뒤 이어지는 번호를 사용한다. V5 제안은 V1~V4가 accounts/investors/securities_firms/securities_accounts임을 전제로 한 것이며, 실제 적용 시 마지막 버전을 확인해 번호를 맞춘다.

### V5__create_ipos_table.sql (제안)

> **주의(PostgreSQL 문법)**: 이 프로젝트의 DB는 PostgreSQL이다. PK 자동증가는 `GENERATED ALWAYS AS IDENTITY`를 사용하며, MySQL 문법(`AUTO_INCREMENT`, `ENGINE=InnoDB`, `ON UPDATE CURRENT_TIMESTAMP`)을 사용하지 않는다. 조건부/부분 유니크 인덱스는 CREATE TABLE과 분리된 별도의 `CREATE UNIQUE INDEX ... WHERE ...` 문으로 작성한다. securities-firm-catalog 문서의 최신 DDL 패턴을 그대로 따른다.

```sql
CREATE TABLE ipos (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_name VARCHAR(255) NOT NULL,
    ipo_name VARCHAR(255) NOT NULL,
    stock_code VARCHAR(255) NULL,
    round_label VARCHAR(255) NULL,
    lower_band_price BIGINT NULL,
    upper_band_price BIGINT NULL,
    fixed_price BIGINT NULL,
    subscription_start_date DATE NOT NULL,
    subscription_end_date DATE NOT NULL,
    listing_date DATE NULL,
    offering_share_count BIGINT NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
    deleted_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 활성(ACTIVE) 레코드 간 stock_code 중복 방지 (부분 유니크, stock_code는 NULL이면 제외)
CREATE UNIQUE INDEX uk_ipo_stock_code_active ON ipos (stock_code)
    WHERE status = 'ACTIVE' AND stock_code IS NOT NULL;

-- 활성(ACTIVE) 레코드 간 (ipo_name, subscription_start_date) 조합 중복 방지 (부분 유니크)
CREATE UNIQUE INDEX uk_ipo_name_start_date_active ON ipos (ipo_name, subscription_start_date)
    WHERE status = 'ACTIVE';

-- 조회/필터링 최적화 인덱스
CREATE INDEX idx_ipo_status ON ipos (status);
```

### Subscription.ipo_id FK 추가 마이그레이션 (참조)
Subscription 테이블에 ipo_id 컬럼이 추가되는 시점의 마이그레이션(별도 버전)에서 아래 제약을 정의한다. IPO 기능 자체의 V5 마이그레이션과는 분리한다.
```sql
ALTER TABLE subscriptions
    ADD CONSTRAINT fk_subscription_ipo
    FOREIGN KEY (ipo_id) REFERENCES ipos (id)
    ON DELETE RESTRICT;
```

## 모듈 소유권

- **Ipo 엔티티**: domain.model.ipo 패키지 소유
- **IpoRepository**: repository.ipo 패키지 소유
- **IpoController**: controller.ipo 패키지 소유
- **IpoService**: service.ipo 패키지 소유
- **DTO 클래스**: service.ipo.dto 또는 controller.ipo.dto 패키지 소유 (기존 프로젝트 DTO 규칙에 따름)

## 의존성

- **JPA/Hibernate**: Investor/SecuritiesFirm 아키텍처에서 이미 사용하는 spring-boot-starter-data-jpa 재사용 (신규 라이브러리 없음)
- **CSV 파싱**: Apache Commons CSV(com.opencsv 또는 commons-csv)를 새로 추가하거나, 단순 헤더 형태(쉼표 구분, 구문자 이스케이프 없음)라면 직접 파서로 구현 가능 — 구현 단계에서 기존 프로젝트 관례를 우선 확인 후 결정. 헤더가 고정 11컬럼이고 MVP 용도가 단순하므로 외부 라이브러리는 최소화하는 방향을 권장.

## 리스크

1. **중복 검사 규칙의 복잡성**: stock_code 유무에 따라 중복 키 분기가 달라 Service 계층 검증이 복잡하다. 동일 검증 헬퍼를 등록/수정/CSV 행별 세 경로에 재사용해 규칙 이탈을 방지한다. 부분 유니크 인덱스와 Service 검증의 동작이 어긋나는 경우(spec 엣지케이스 상호작용) 구현 단계 테스트로 확정한다.
2. **CSV 행별 트랜잭션 비용**: 행 단위 개별 저장은 N건 INSERT가 되지만 MVP 규모(수백~수천 행)에선 문제없다. 대용량 일괄 등록은 범위 밖으로 명시한다.
3. **FK ON DELETE RESTRICT 의존**: 논리 삭제가 애플리케이션 로직(Service)에서만 수행된다는 점에서, 실수로 물리 delete를 호출하면 500/외래키 위반 오류가 발생하므로 Repository에 물리 삭제 메서드를 노출하지 않아야 한다 (SecuritiesFirm과 동일).
4. **INACTIVE 재등록 여부 정책**: 삭제된 IPO와 동일한 종목코드/공모주명 재등록은 중복 검사에서 제외되므로 재등록이 가능하게 설계했다. 정책 변경 시 유니크 제약 재설계 필요.
5. **논리 삭제 필터 누락 위험**: 목록/상세 조회 및 청약 IPO 선택 목록에서 deletedAt(deletedAt IS NOT NULL) 필터를 누락하면 삭제된 IPO가 노출될 수 있다. Repository 쿼리에서 항상 필터가 적용되도록 설계.
6. **CSV 인코딩/EOL 이슈**: 한글 기업명 등 특수 문자(콤마, 큰따옴표)와 인코딩(UTF-8 BOM) 처리 — 파일 인코딩과 구분자 정책을 구현 단계에서 확정한다 (spec 엣지케이스: 특수 문자가 포함된 기업명/공모주명).

## 결정 필요 사항

1. **중복 검사 이중화 확정**: DB 부분 유니크 인덱스(권장, PostgreSQL 조건부 유니크 활용)와 Service 계층 검증을 모두 적용할지, Service 검증만 할지. 보안/데이터 무결성 관점에서 이중 방어 적용을 권장하며, 구현 단계에서 부분 유니크가 의도대로 동작하지 않는 케이스가 확인되면 Service 검증을 1차로 두고 DB 제약은 정책 결정 후 재검토한다.
2. **CSV 파일 크기 상한**: 5MB 기본값(제안)을 확정할지 여부.
3. **CSV 파서 라이브러리**: 외부 라이브러리(commons-csv 등) 도입 vs 자체 파서. 구현 단계에서 기존 프로젝트 사용 라이브러리 확인 후 결정.
4. **조회 필터 기본값**: 목록 조회 시 ACTIVE/INACTIVE 전부 노출할지, ACTIVE만 노출할지 (spec은 "삭제된 IPO 제외"만 명시 — status=INACTIVE이면서 deletedAt이 설정된 레코드는 조회 제외가 기본).