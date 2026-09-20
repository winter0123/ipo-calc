# 아키텍처 설계: 배정 결과 관리 (allocation-management)

> **식별자 결정**: 본 기능은 기존 프로젝트 일관성을 따라 **Long(IDENTITY)** 식별자를 사용한다. DB 스키마에서도 `id` 컬럼은 `BIGINT GENERATED ALWAYS AS IDENTITY`로 구현한다. UUID를 사용하지 않는다.
>
> **동시성 결정 (D-A-006)**: 이 서비스는 실시간 배정 처리 시스템이 아닌 **사후 기록 서비스**다. 개인 사용자가 자신의 배정 기록을 순차적으로 입력하는 시나리오이므로, 낙관적 락(@Version), 비관적 락(SELECT FOR UPDATE), 분산 락 등 별도 동시성 제어 메커니즘을 도입하지 않는다. 표준 트랜잭션 경계와 DB 유니크 제약만으로 충분하다.

## 아키텍처 개요 (레이어 구조)

Subscription 기록 관리(subscription-management)와 동일한 관심사 분리 원칙을 따른다. Spring Boot 백엔드는 다음 레이어 구조를 사용한다:

```
controller
application/service
domain
repository
```

- **controller**: HTTP 요청 처리, 입력 검증(필수값, 배정수량 범위, 상태값), 응답 매핑. 복잡한 업무 규칙은 포함하지 않음.
- **application/service**: 업무 규칙 유스케이스 오케스트레이션, 트랜잭션 경계. 배정수량 검증(≥0且≤청약수량), 중복 검사(ACTIVE 기준), 참조 무결성 확인(Subscription 존재 여부) 포함.
- **domain**: 엔티티, 상태 상수 정의. Allocation 엔티티 및 상태(ACTIVE/CANCELLED) 상수 정의.
- **repository**: 데이터 접근 계층, Spring Data JPA 인터페이스 정의. 물리 삭제 메서드를 노출하지 않는다.

배정 기록 관련 컴포넌트는 다음과 같이 구성한다:
- `controller.allocation`: AllocationController
- `service.allocation`: AllocationService
- `domain.model.allocation`: Allocation 엔티티
- `repository.allocation`: AllocationRepository

> **참고**: Allocation은 Subscription을 참조(FK)하며, 투자자별 조회는 Allocation → Subscription → Investor 경유로 수행한다. Subscription은 이미 Epic 6에서 구현 완료되었다.

## 데이터 흐름

### 배정 등록 흐름
1. 사용자가 POST /allocations 요청 (subscriptionId, allocatedQuantity, allocatedAt 필수; allocationMethod, allocationPrice, note 선택)
2. Controller가 입력값 검증 (필수값 누락 400, allocatedQuantity 0 미만 400, 상태값이 허용되지 않으면 400)
3. Service가 참조하는 Subscription 존재 여부 확인 (존재하지 않으면 404)
4. Service가 Subscription의 quantity 비교하여 allocatedQuantity 초과 검증 (초과 시 400)
5. Service가 동일 subscriptionId의 ACTIVE 상태 기록 중복 검사
6. 중복이면 409 Conflict 반환
7. Allocation 엔티티 생성 (status=ACTIVE), 저장
8. 저장된 정보 반환 (201 Created)

### 배정 목록 조회 흐름
1. 사용자가 GET /allocations 요청 (subscriptionId, investorId 쿼리 파라미터 선택)
2. Service에서 삭제되지 않은(deletedAt IS NULL) ACTIVE 상태 배정 목록 조회
3. subscriptionId 파라미터가 있으면 해당 청약의 배정만 필터링
4. investorId 파라미터가 있으면 Subscription 테이블과 JOIN하여 해당 투자자의 배정만 필터링
5. 목록 반환

### 배정 상세 조회 흐름
1. 사용자가 GET /allocations/{id} 요청
2. Service에서 삭제되지 않은 배정 조회 (findById + deletedAt 확인 + status 확인)
3. 없거나 CANCELLED 상태이면 404 Not Found 반환
4. 성공 시 배정 정보 반환

### 배정 수정 흐름
1. 사용자가 PATCH /allocations/{id} 요청
2. Controller가 입력값 검증 (제공된 필드만 수정, 유효성 오류 시 400)
3. Service에서 대상 배정 조회 (없으면 404, CANCELLED이면 404)
4. Service가 Subscription의 quantity 비교하여 배정수량 초과 검증
5. Service가 매도(Sale) 연결 검증 — Epic 8에서 확정 (향후 확장 지점)
6. 필드 업데이트 및 저장, 결과 반환

### 배정 취소 흐름 (논리 삭제)
1. 사용자가 DELETE /allocations/{id} 요청
2. Service에서 대상 배정 조회 (없으면 404, 이미 CANCELLED이면 404)
3. status를 CANCELLED로 변경, deletedAt 설정
4. 저장 및 204 No Content 반환
5. FK(ON DELETE RESTRICT) 제약과 무관하게 항상 성공 — 물리 삭제가 아니라 status UPDATE이므로 기존 참조 관계가 유지됨

## API 설계

### 엔드포인트 목록
1. POST /allocations - 배정 등록
2. GET /allocations - 배정 목록 조회 (subscriptionId, investorId 쿼리 파라미터 선택)
3. GET /allocations/{id} - 배정 상세 조회
4. PATCH /allocations/{id} - 배정 수정
5. DELETE /allocations/{id} - 배정 취소 (논리 삭제)

### 요청/응답 DTO (JPA 엔티티 직접 노출 금지, DTO 사용)

#### 배정 등록 요청 (POST /allocations)
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

#### 배정 등록 응답 (성공, 201 Created)
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

#### 배정 목록 조회 응답 (성공)
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

#### 배정 수정 요청 (PATCH /allocations/{id}) — 제공된 필드만 갱신
```json
{
  "allocatedQuantity": 40,
  "allocatedAt": "2026-10-05T19:00:00",
  "allocationMethod": "비례배정",
  "allocationPrice": 15000,
  "note": "수정된 비고"
}
```

#### 배정 취소 응답 (성공, 204 No Content)
- 본문 없음. status=CANCELLED, deletedAt 설정.

### 에러 응답 형식
```json
{
  "error": "ERROR_CODE",
  "message": "사용자에게 표시할 메시지"
}
```

#### 주요 에러 코드
- VALIDATION_ERROR: 입력값 검증 실패 (필수값 누락, 배정수량 0 미만, 청약수량 초과 등)
- DUPLICATE_ALLOCATION: 활성 상태(ACTIVE) 배정 중복 (동일 subscriptionId)
- NOT_FOUND: 배정을 찾을 수 없음 (미등록 또는 CANCELLED 상태)
- REFERENCE_NOT_FOUND: 참조하는 Subscription이 존재하지 않음

## DB 설계

### Allocation 테이블

| 컬럼명 | 타입 | 제약 | 설명 |
|--------|------|------|------|
| id | BIGINT | PK, GENERATED ALWAYS AS IDENTITY | 배정 고유 식별자 (Long IDENTITY) |
| subscription_id | BIGINT | NOT NULL, FK -> subscriptions.id ON DELETE RESTRICT | 연결된 청약 기록 참조 |
| allocated_quantity | BIGINT | NOT NULL | 배정수량 (주 단위, 0 이상) |
| allocated_at | TIMESTAMP | NOT NULL | 배정일시 |
| allocation_method | VARCHAR(255) | NULL 허용 | 배정방식메모 (선택, 자유 텍스트 — Enum 아님) |
| allocation_price | BIGINT | NULL 허용 | 배정단가 (원 단위, 0 이상) |
| note | VARCHAR(255) | NULL 허용 | 비고 (선택) |
| status | VARCHAR(10) | NOT NULL, DEFAULT 'ACTIVE' | 상태 (ACTIVE/CANCELLED) |
| deleted_at | TIMESTAMP | NULL 허용 | 논리 삭제 시각 |
| created_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 생성 일시 |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 수정 일시 (JPA @LastModifiedDate로 갱신) |

### 인덱스
- PRIMARY KEY (id)
- UNIQUE INDEX uk_allocation_active_subscription ON allocations (subscription_id) WHERE status = 'ACTIVE' — ACTIVE 상태 기준 부분 유니크 (AC-005, AC-006)
- INDEX idx_allocation_subscription_id (subscription_id) — 청약별 조회 최적화
- INDEX idx_allocation_status (status) — 상태별 필터링 최적화

> **부분/조건부 유니크 인덱스 결정**: spec AC-005, AC-006을 반영한다. 중복 검사는 **활성 상태(ACTIVE) 레코드에 대해서만** 수행하며, CANCELLED 레코드는 중복 검사에서 제외된다. PostgreSQL의 조건부 유니크 인덱스(partial unique index)를 활용하여 DB 레벨에서 중복을 이중 방어한다.

## 중복 검사 로직

spec AC-005 / AC-006을 반영한다. **중복은 항상 활성 상태(ACTIVE) 레코드에 대해서만 검사**하며, CANCELLED 레코드는 검사 대상에서 제외한다.

| 조건 | 중복 키 | 비고 |
|------|---------|------|
| subscription_id ACTIVE 기록 존재 | 단일 컬럼 | 동일 subscriptionId의 ACTIVE 기록이 있으면 409 (AC-005) |

- **등록(AC-005)과 수정(AC-014) 모두 동일 규칙**을 적용한다. 수정 시 자신의 id는 제외하고 검사한다.
- **CANCELLED 기록이 있어도 재등록은 가능**하다 (AC-006) — ACTIVE 기록만 중복 검사 대상.

### Service 계층 검증 vs DB 제약 (이중 방어)
- **Service 계층**: 트랜잭션 안에서 위 규칙을 구현한다. 등록/수정 진입점마다 동일한 검증 헬퍼를 재사용한다.
- **DB 제약(부분 유니크)**: 데이터 무결성 이중 방어로 조건부 유니크 인덱스를 추가한다.
  - `CREATE UNIQUE INDEX uk_allocation_active_subscription ON allocations (subscription_id) WHERE status = 'ACTIVE';`
  - Service 계층 검증을 1차 방어선으로 삼고, DB 유니크는 보조 제약으로 둔다.

## 1:1 관계 및 중복 방지

Allocation은 Subscription과 **정확히 1:1** 관계다(D-A-002). 한 청약에 대한 ACTIVE 배정 결과는 하나만 존재하며, `subscription_id`의 ACTIVE 상태 기준 부분 유니크 제약으로 중복 배정 기록을 방지한다. CANCELLED 기록이 있어도 재등록은 허용된다.

### 배정수량 검증 (Service 계층)
- `allocatedQuantity >= 0` (음수 불가)
- `allocatedQuantity <= subscription.quantity` (청약수량 초과 불가)
- Service가 Allocation 저장 전 참조하는 Subscription을 조회하여 quantity를 확인하고, 초과 시 400 Bad Request 반환
- 단순 값 검증이며, 실시간 동시성이 아닌 사후 기록 서비스이므로 별도 락 없이 표준 트랜잭션 경계로 처리 (D-A-005)

## 참조 무결성 처리

### 외래 키 제약
`subscription_id`는 **ON DELETE RESTRICT**로 설정하여 물리 삭제를 DB 레벨에서 강제한다.

```sql
ALTER TABLE allocations
    ADD CONSTRAINT fk_allocation_subscription
    FOREIGN KEY (subscription_id) REFERENCES subscriptions (id)
    ON DELETE RESTRICT;
```

```
Subscription (One) <---- (1:1) Allocation
```

### 논리 삭제와 FK 관계
- **논리 삭제(status=CANCELLED, deletedAt 설정)는 FK 제약과 무관하게 항상 가능하다.** ON DELETE RESTRICT는 레코드 삭제(물리 삭제)만 제한하며 UPDATE를 통한 status 변경은 막지 않으므로, 연결된 Subscription이 있어도 취소는 항상 성공한다.
- Subscription이 CANCELLED(논리 삭제)되어도 연결된 Allocation 기록은 그대로 유지된다 — 배정은 이미 발생한 사실이므로 보존된다 (spec 참고 사항).
- Subscription이 삭제(물리 삭제)되는 경우는 ON DELETE RESTRICT에 의해 불가능하며, 따라서 Allocation의 참조 무결성은 항상 보장된다.

## 투자자별 조회 (Allocation → Subscription → Investor 경유)

investorId 쿼리 파라미터로 필터링 시, Allocation 테이블과 Subscription 테이블을 JOIN하여 Subscription의 investorId로 필터링하는 Repository 쿼리를 설계한다.

```java
@Query("SELECT a FROM Allocation a JOIN a.subscription s WHERE s.investorId = :investorId AND a.deletedAt IS NULL AND a.status = 'ACTIVE'")
List<Allocation> findByInvestorId(@Param("investorId") Long investorId);
```

- Allocation은 Subscription을 참조하므로, JPQL/SQL에서 JOIN을 통해 investorId 기반 필터링이 가능하다.
- subscriptionId 파라미터로 필터링할 때는 Allocation 직접 조회로 충분하다 (1:1 관계이므로 최대 1건).
- 두 파라미터가 모두 없으면 전체 ACTIVE 목록을 조회한다.
- 삭제된 기록(deletedAt이 설정된 CANCELLED)은 목록에서 항상 제외된다.

## 매도 연결 시 수정 제한 (spec AC-015, 향후 확장 지점)

spec AC-015에 따라, 배정에 매도(Sale) 기록이 연결되어 있고 신규 배정수량이 연결된 매도수량 합계 미만으로 요청되면 400 Bad Request로 제한해야 한다. 그러나 **이번 Epic(Epic 7)에는 Sale 테이블이 없으므로 실제 검증 로직은 Epic 8에서 구현된다.**

설계 방향:
- Epic 8에서 Sale 엔티티가 도입되면, AllocationService의 수정 메서드에서 Sale 참조를 주입받아 배정 수량 검증을 수행한다.
- 현재 단계에서는 AllocationService에 Sale 관련 의존성을 두지 않는다. 검증 로직이 추가될 때 Service 시그니처가 확장되는 구조로 설계한다.
- 구체적인 Sale 테이블 구조와 검증 로직은 Epic 8 아키텍처 설계 시 확정한다.

## Flyway 마이그레이션 파일명 제안

V1=accounts, V2=investors, V3=securities_firms, V4=securities_accounts, V5=ipos, V6=subscriptions 순서 가정)했을 때 다음 파일을 추가한다.

### V7__create_allocations_table.sql (제안)

> **주의(PostgreSQL 문법)**: 이 프로젝트의 DB는 PostgreSQL이다. PK 자동증가는 `GENERATED ALWAYS AS IDENTITY`를 사용하며, MySQL 문법(`AUTO_INCREMENT`, `ENGINE=InnoDB`, `ON UPDATE CURRENT_TIMESTAMP`)을 사용하지 않는다. 조건부/부분 유니크 인덱스는 CREATE TABLE과 분리된 별도의 `CREATE UNIQUE INDEX ... WHERE ...` 문으로 작성한다.

```sql
CREATE TABLE allocations (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    subscription_id BIGINT NOT NULL,
    allocated_quantity BIGINT NOT NULL,
    allocated_at TIMESTAMP NOT NULL,
    allocation_method VARCHAR(255) NULL,
    allocation_price BIGINT NULL,
    note VARCHAR(255) NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
    deleted_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 외래 키 제약
ALTER TABLE allocations
    ADD CONSTRAINT fk_allocation_subscription
    FOREIGN KEY (subscription_id) REFERENCES subscriptions (id)
    ON DELETE RESTRICT;

-- 활성(ACTIVE) 레코드 간 subscription_id 중복 방지 (부분 유니크)
CREATE UNIQUE INDEX uk_allocation_active_subscription ON allocations (subscription_id)
    WHERE status = 'ACTIVE';

-- 조회/필터링 최적화 인덱스
CREATE INDEX idx_allocation_subscription_id ON allocations (subscription_id);
CREATE INDEX idx_allocation_status ON allocations (status);
```

## 모듈 소유권

- **Allocation 엔티티**: domain.model.allocation 패키지 소유
- **AllocationRepository**: repository.allocation 패키지 소유
- **AllocationController**: controller.allocation 패키지 소유
- **AllocationService**: service.allocation 패키지 소유
- **DTO 클래스**: service.allocation.dto 또는 controller.allocation.dto 패키지 소유 (기존 프로젝트 DTO 규칙에 따름)

## 의존성

- **JPA/Hibernate**: Subscription 기록 관리에서 이미 사용하는 spring-boot-starter-data-jpa 재사용 (신규 라이브러리 없음)
- **추가 라이브러리 불필요**: 외부 파싱 라이브러리나 별도 동시성 제어 라이브러리가 없다. 표준 Spring Boot + JPA 구성만으로 구현 가능.

## 리스크

1. **부분 유니크 인덱스 동작 검증 필요**: PostgreSQL 조건부 유니크가 Service 계층 검증과 동일하게 동작하는지 구현 단계에서 반드시 TC로 검증한다. 특히 CANCELLED 기록이 여러 개 존재할 때 재등록이 정상 동작하는지 확인.
2. **FK ON DELETE RESTRICT 의존**: 논리 삭제가 애플리케이션 로직(Service)에서만 수행된다는 점에서, 실수로 물리 delete를 호출하면 500/외래키 위반 오류가 발생하므로 Repository에 물리 삭제 메서드를 노출하지 않아야 한다.
3. **논리 삭제 필터 누락 위험**: 목록/상세 조회에서 deletedAt IS NULL 및 status 필터를 누락하면 CANCELLED 기록이 노출될 수 있다. Repository 쿼리에서 항상 필터가 적용되도록 설계.
4. **배정수량 검증의 트랜잭션 경계**: Service에서 Subscription 조회 후 quantity 비교가 트랜잭션 안에서 이루어져야 경합 상태에서 검증이 누락되지 않는다.
5. **investorId JOIN 쿼리 성능**: Allocation-Subscription-JOIN 쿼리에 적절한 인덱스가 없는 경우 성능 저하 가능. 구현 단계에서 EXPLAIN 검증 필요.

## 결정 필요 사항

1. **allocation_method / note 컬럼 길이**: 255자로 제안. 스펙에서 "매우 긴 경우 상한을 아키텍처 단계에서 확정"이라고 명시했으므로, 구현 전 확정 필요.
2. **부분 유니크 인덱스 동작**: PostgreSQL에서 `WHERE status = 'ACTIVE'` 조건부 유니크가 Service 검증과 정확히 동일하게 동작하는지 구현 단계에서 실데이터 기준 검증 필요.
3. **Sale 검증 시점**: Epic 8에서 Sale 엔티티가 도입될 때, AllocationService에 Sale 관련 검증을 추가할지 결정.
