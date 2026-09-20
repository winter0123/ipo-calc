# 아키텍처 설계: 분할 매도 기록 관리 (sale-management)

> **식별자 결정**: 본 기능은 기존 프로젝트 일관성을 따라 **Long(IDENTITY)** 식별자를 사용한다. DB 스키마에서도 `id` 컬럼은 `BIGINT GENERATED ALWAYS AS IDENTITY`로 구현한다. UUID를 사용하지 않는다.
>
> **동시성 결정 (D-SL-004)**: 이 서비스는 실시간 매도 처리 시스템이 아닌 **사후 기록 서비스**다. 개인 사용자가 이미 완료된 매도 결과를 순차 입력하는 시나리오이므로, 낙관적 락(@Version), 비관적 락(SELECT FOR UPDATE), 분산 락 등 별도 동시성 제어 메커니즘을 도입하지 않는다. 표준 트랜잭션 경계(READ_COMMITTED) 안에서 누적 매도수량 검증만으로 충분하다.

## 아키텍처 개요 (레이어 구조)

배정 결과 관리(allocation-management, Epic 7)와 동일한 관심사 분리 원칙을 따른다. Spring Boot 백엔드는 다음 레이어 구조를 사용한다:

```
controller
application/service
domain
repository
```

- **controller**: HTTP 요청 처리, 입력 검증(필수값, 매도수량 범위, 상태값), 응답 매핑. 복잡한 업무 규칙은 포함하지 않음.
- **application/service**: 업무 규칙 유스케이스 오케스트레이션, 트랜잭션 경계. 참조 무결성 확인(Allocation 존재 여부), 누적 매도수량 검증(Σ(ACTIVE 매도수량) ≤ 배정수량), 상태값 검증 포함.
- **domain**: 엔티티, 상태 상수 정의. Sale 엔티티 및 상태(ACTIVE/CANCELLED) 상수 정의.
- **repository**: 데이터 접근 계층, Spring Data JPA 인터페이스 정의. 물리 삭제 메서드를 노출하지 않는다.

매도 기록 관련 컴포넌트는 다음과 같이 구성한다:
- `controller.sale`: SaleController
- `service.sale`: SaleService
- `domain.model.sale`: Sale 엔티티
- `repository.sale`: SaleRepository

> **참고**: Sale은 Allocation을 참조(FK)하며, 투자자별 조회는 Sale → Allocation → Subscription → Investor 경유로 수행한다. Allocation은 Epic 7에서, Subscription은 Epic 6에서 이미 구현 완료되었다.

## 데이터 흐름

### 매도 등록 흐름
1. 사용자가 POST /sales 요청 (allocationId, quantity, saleDate 필수; price, amount, fee, tax, otherCost 선택)
2. Controller가 입력값 검증 (필수값 누락 400, quantity 0 이하 400, 상태값이 허용되지 않으면 400)
3. Service가 참조하는 Allocation 존재 여부 확인 (존재하지 않으면 404)
4. Service가 Allocation의 allocatedQuantity 조회 (배정수량 확보)
5. Service가 해당 allocationId의 기존 ACTIVE Sale들의 quantity 합계 조회
6. Service가 누적 매도수량 검증 — (기존 합계 + 신규 quantity) ≤ allocatedQuantity (초과 시 400, D-SL-003)
7. Sale 엔티티 생성 (status=ACTIVE), 저장
8. 저장된 정보 반환 (201 Created)

### 매도 목록 조회 흐름
1. 사용자가 GET /sales 요청 (allocationId, investorId, investorName 쿼리 파라미터 선택, 셋 다 빈 값이면 전체 조회)
2. Service에서 삭제되지 않은(deletedAt IS NULL) ACTIVE 상태 매도 목록 조회 — CANCELLED 기록은 목록에서 항상 제외 (AC-018)
3. allocationId 파라미터가 있으면 해당 배정의 매도만 필터링
4. investorId/investorName 파라미터가 있으면 Allocation → Subscription → Investor JOIN 체인으로 해당 투자자의 매도만 필터링
5. 정렬: saleDate 내림차순, 동일 saleDate면 createdAt 내림차순 (D-SL-002)
6. 목록 반환

### 매도 상세 조회 흐름
1. 사용자가 GET /sales/{id} 요청
2. Service에서 삭제되지 않은 매도 조회 (findById + deletedAt 확인 + status 확인)
3. 없거나 CANCELLED 상태이면 404 Not Found 반환
4. 성공 시 매도 정보 반환

### 매도 수정 흐름
1. 사용자가 PATCH /sales/{id} 요청 (시점/필드 제약 없이 전체 허용 — D-SL-003)
2. Controller가 입력값 검증 (제공된 필드만 수정, 유효성 오류 시 400)
3. Service에서 대상 매도 조회 (없으면 404, CANCELLED이면 404)
4. Service가 참조하는 Allocation의 allocatedQuantity 조회 (수정이 allocationId 변경을 포함하는 경우 변경 대상 기준)
5. Service가 해당 allocationId의 기존 ACTIVE Sale들의 quantity 합계 조회 (자기 자신은 제외)
6. Service가 누적 매도수량 재검증 — (기존 합계 - 자기자신 + 수정 quantity) ≤ allocatedQuantity (초과 시 400, D-SL-003)
7. 필드 업데이트 및 저장, updatedAt 갱신, 결과 반환

### 매도 취소 흐름 (논리 삭제)
1. 사용자가 DELETE /sales/{id} 요청
2. Service에서 대상 매도 조회 (없으면 404, 이미 CANCELLED이면 404)
3. status를 CANCELLED로 변경, deletedAt 설정
4. 저장 및 204 No Content 반환
5. FK(ON DELETE RESTRICT) 제약과 무관하게 항상 성공 — 물리 삭제가 아니라 status UPDATE이므로 기존 참조 관계가 유지됨 (D-SL-005)
6. 취소된 매도(CANCELLED)는 다시 ACTIVE로 되돌릴 수 없다. 정정이 필요하면 별도로 새 매도를 등록한다

## API 설계

### 엔드포인트 목록
1. POST /sales - 매도 등록
2. GET /sales - 매도 목록 조회 (allocationId, investorId, investorName 쿼리 파라미터 선택)
3. GET /sales/{id} - 매도 상세 조회
4. PATCH /sales/{id} - 매도 수정
5. DELETE /sales/{id} - 매도 취소 (논리 삭제)

### 요청/응답 DTO (JPA 엔티티 직접 노출 금지, DTO 사용)

#### 매도 등록 요청 (POST /sales)
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

#### 매도 등록 응답 (성공, 201 Created)
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

#### 매도 목록 조회 응답 (성공)
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

#### 매도 수정 요청 (PATCH /sales/{id}) — 시점/필드 제약 없이 전체 허용
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

#### 매도 취소 응답 (성공, 204 No Content)
- 본문 없음. status=CANCELLED, deletedAt 설정. (물리 삭제 수행하지 않음 — D-SL-005)

### 에러 응답 형식
```json
{
  "error": "ERROR_CODE",
  "message": "사용자에게 표시할 메시지"
}
```

#### 주요 에러 코드
- VALIDATION_ERROR: 입력값 검증 실패 (필수값 누락, 매도수량 0 이하 등)
- ALLOCATION_QUANTITY_EXCEEDED: 누적 매도수량이 배정수량을 초과
- NOT_FOUND: 매도를 찾을 수 없음 (미등록 또는 CANCELLED 상태)
- REFERENCE_NOT_FOUND: 참조하는 Allocation이 존재하지 않음

## DB 설계

### Sale 테이블

| 컬럼명 | 타입 | 제약 | 설명 |
|--------|------|------|------|
| id | BIGINT | PK, GENERATED ALWAYS AS IDENTITY | 매도 고유 식별자 (Long IDENTITY) |
| allocation_id | BIGINT | NOT NULL, FK -> allocations.id ON DELETE RESTRICT | 연결된 배정 기록 참조 (1:N, 유니크 아님 — D-SL-006) |
| quantity | BIGINT | NOT NULL | 매도수량 (주 단위, 0 초과) |
| price | BIGINT | NULL 허용 | 매도단가 (원 단위, 0 이상, 선택) |
| amount | BIGINT | NULL 허용 | 매도금액 (원 단위, 0 이상, 선택) |
| sale_date | TIMESTAMP | NOT NULL | 매도일시 |
| fee | BIGINT | NULL 허용 | 수수료 (원 단위, 선택 — D-SL-007) |
| tax | BIGINT | NULL 허용 | 세금 (원 단위, 선택 — D-SL-007) |
| other_cost | BIGINT | NULL 허용 | 기타비용 (원 단위, 선택 — D-SL-007) |
| status | VARCHAR(10) | NOT NULL, DEFAULT 'ACTIVE' | 상태 (ACTIVE/CANCELLED만 사용 — D-SL-001) |
| deleted_at | TIMESTAMP | NULL 허용 | 논리 삭제 시각 |
| created_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 생성 일시 |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 수정 일시 (JPA @LastModifiedDate로 갱신) |

### 인덱스
- PRIMARY KEY (id)
- INDEX idx_sale_allocation_id (allocation_id) — 배정별 조회 및 누적 매도수량 합계 조회 최적화
- INDEX idx_sale_status (status) — 상태별 필터링 최적화
- INDEX idx_sale_sale_date (sale_date) — 목록 정렬(saleDate 내림차순) 최적화

> **유니크 제약 없는 설계 (D-SL-006)**: 하나의 배정에 여러 건의 매도가 있을 수 있으므로 allocation_id에 유니크 제약을 두지 않는다. 데이터 무결성은 등록/수정 시 Service 계층의 누적 매도수량 검증으로 보장한다.

## 누적 매도수량 검증 로직 (락 없이, D-SL-003 / D-SL-004)

등록(AC-005)과 수정(AC-015) 모두 Service 계층에서 **표준 트랜잭션(@Transactional) 경계 안에** 다음 순서로 누적 매도수량을 검증한다.

1. 대상 Allocation 조회하여 `allocatedQuantity` 확인 — 존재하지 않으면 404 (등록 시)
2. 해당 allocation_id의 기존 ACTIVE Sale들의 quantity 합계 조회
3. 누적 매도수량 검증:
   - **등록**: `(기존 ACTIVE 합계 + 신규 quantity) <= allocatedQuantity` 초과 시 400
   - **수정**: `(기존 ACTIVE 합계 - 자기자신 quantity + 수정 quantity) <= allocatedQuantity` 초과 시 400 (자기 자신은 합계에서 제외)
4. 검증 실패 시 400 Bad Request 반환, 트랜잭션 롤백 — 데이터 무결성 유지

```
Σ(ACTIVE Sale.quantity, allocation_id = X) − (자기자신, 수정 시만) + (신규/수정 quantity) ≤ Allocation.allocatedQuantity
```

### 동시성 제어 미도입 근거 (D-SL-004)
- 이 서비스는 실시간 매도 처리 시스템이 아니라, 개인 사용자가 자신의 매도 결과를 순차 입력하는 **사후 기록 서비스**다.
- 한 사용자가 동시에 다수의 등록/수정 요청을 보내 자기 자신과 경합하는 시나리오는 실질적으로 발생하지 않는다.
- **표준 트랜잭션 격리 수준(READ_COMMITTED)** 으로 충분하다. 검증과 저장이 동일 트랜잭션 안에서 수행되므로, 별도 낙관적 락(@Version), 비관적 락(SELECT FOR UPDATE), 분산 락이 불필요하다.
- 이는 배정 결과 관리(allocation-management, D-A-005/D-A-006)와 동일한 원칙이다.

## 참조 무결성 처리

### 외래 키 제약
`allocation_id`는 **ON DELETE RESTRICT**로 설정하여 배정 레코드의 물리 삭제를 DB 레벨에서 강제한다.

```sql
ALTER TABLE sales
    ADD CONSTRAINT fk_sale_allocation
    FOREIGN KEY (allocation_id) REFERENCES allocations (id)
    ON DELETE RESTRICT;
```

```
Subscription (One) <---- (1:1) Allocation ----> (Many) Sale
```

### 논리 삭제와 FK 관계
- **논리 삭제(status=CANCELLED, deletedAt 설정)는 FK 제약과 무관하게 항상 가능하다.** ON DELETE RESTRICT는 레코드 삭제(물리 삭제)만 제한하며 UPDATE를 통한 status 변경은 막지 않으므로, 연결된 Allocation이 있어도 취소는 항상 성공한다 (D-SL-005).
- **Allocation이 CANCELLED(논리 삭제)되어도 연결된 Sale 기록은 그대로 유지된다** — 매도는 이미 발생한 사실이므로 보존된다.
- Allocation이 삭제(물리 삭제)되는 경우는 ON DELETE RESTRICT에 의해 불가능하며, 따라서 Sale의 참조 무결성은 항상 보장된다.

## 투자자별/투자자명 조회 (Sale → Allocation → Subscription → Investor 경유)

investorId / investorName 쿼리 파라미터로 필터링 시, Sale 테이블을 시작으로 Allocation → Subscription → Investor로 이어지는 JOIN 체인을 사용하는 Repository 쿼리를 설계한다.

```java
@Query("SELECT s FROM Sale s JOIN s.allocation a JOIN a.subscription sub JOIN sub.investor i " +
       "WHERE (:investorId IS NULL OR i.id = :investorId) " +
       "AND (:investorName IS NULL OR i.name = :investorName) " +
       "AND s.deletedAt IS NULL AND s.status = 'ACTIVE' " +
       "ORDER BY s.saleDate DESC, s.createdAt DESC")
List<Sale> findActiveWithFilters(@Param("investorId") Long investorId, @Param("investorName") String investorName);
```

- allocationId 파라미터로 필터링할 때는 Sale 직접 조회로 충분하다 (N:1 — 해당 배정의 모든 매도 반환).
- investorName 필터는 부분 일치 여부를 구현 단계에서 확정한다 (정확 일치 vs LIKE).
- 두/세 파라미터를 함께 사용하면 AND 조건으로 결합한다.
- 모든 파라미터가 빈 값이면 삭제되지 않은 ACTIVE 매도 전체를 조회한다.
- 삭제된 기록(deletedAt이 설정된 CANCELLED)은 목록에서 항상 제외된다 (AC-018).

## Cost/Profit 확장 지점 (Epic 9/10, 아직 미구현)

> 코드를 작성하지 않고 설계 방향만 서술한다.

- 매도 기록(Sale)에서 발생하는 비용(Cost)과 수익(Profit) 계산은 **Epic 9(비용 기록) / Epic 10(수익 계산)에서 별도 엔티티로 설계**할 예정이다.
- Sale의 `fee`, `tax`, `otherCost` 필드는 이번 Epic에서는 **사실 기록으로만 저장**하며, 이 값들로부터 수익을 계산하는 로직은 Sale 도메인에 두지 않는다 (D-SL-007).
- Epic 9/10 구현 시 Sale 엔티티를 참조해 비용/수익을 계산하는 N:1(또는 N:N) 참조 흐름이 추가될 예정이다. 이때 Sale 도메인 수정 없이 신규 엔티티(Cost/Profit)가 Sale을 참조하는 방향으로 설계해 확장성을 확보한다 — Subscription/Allocation 아키텍처의 확장 지점 서술 방식과 동일하다.

## Flyway 마이그레이션 파일명 제안

V1=accounts, V2=investors, V3=securities_firms, V4=securities_accounts, V5=ipos, V6=subscriptions, V7=allocations 순서 가정했을 때 다음 파일을 추가한다.

### V8__create_sales_table.sql (제안)

> **주의(PostgreSQL 문법)**: 이 프로젝트의 DB는 PostgreSQL이다. PK 자동증가는 `GENERATED ALWAYS AS IDENTITY`를 사용하며, MySQL 문법(`AUTO_INCREMENT`, `ENGINE=InnoDB`, `ON UPDATE CURRENT_TIMESTAMP`)을 사용하지 않는다. 조건부/부분 유니크 인덱스가 필요한 경우 CREATE TABLE과 분리된 별도의 `CREATE UNIQUE INDEX ... WHERE ...` 문으로 작성한다 (본 기능에는 부분 유니크가 필요 없다).

```sql
CREATE TABLE sales (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    allocation_id BIGINT NOT NULL,
    quantity BIGINT NOT NULL,
    price BIGINT NULL,
    amount BIGINT NULL,
    sale_date TIMESTAMP NOT NULL,
    fee BIGINT NULL,
    tax BIGINT NULL,
    other_cost BIGINT NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
    deleted_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 외래 키 제약
ALTER TABLE sales
    ADD CONSTRAINT fk_sale_allocation
    FOREIGN KEY (allocation_id) REFERENCES allocations (id)
    ON DELETE RESTRICT;

-- 조회/필터링/정렬 최적화 인덱스
CREATE INDEX idx_sale_allocation_id ON sales (allocation_id);
CREATE INDEX idx_sale_status ON sales (status);
CREATE INDEX idx_sale_sale_date ON sales (sale_date);
```

> allocation_id FK는 ON DELETE RESTRICT이므로 물리 삭제는 차단되고, 배정(A)은 유니크 아님(1:N)이므로 부분 유니크 인덱스는 필요 없다. 데이터 무결성은 Service 계층의 누적 매도수량 검증이 담당한다.

## 모듈 소유권

- **Sale 엔티티**: domain.model.sale 패키지 소유
- **SaleRepository**: repository.sale 패키지 소유
- **SaleController**: controller.sale 패키지 소유
- **SaleService**: service.sale 패키지 소유
- **DTO 클래스**: service.sale.dto 또는 controller.sale.dto 패키지 소유 (기존 프로젝트 DTO 규칙에 따름)

## 의존성

- **JPA/Hibernate**: Subscription/Allocation 기록 관리에서 이미 사용하는 spring-boot-starter-data-jpa 재사용 (신규 라이브러리 없음)
- **추가 라이브러리 불필요**: 외부 파싱 라이브러리나 별도 동시성 제어 라이브러리가 없다. 표준 Spring Boot + JPA 구성만으로 구현 가능.

## 리스크

1. **FK ON DELETE RESTRICT 의존**: 논리 삭제가 애플리케이션 로직(Service)에서만 수행된다는 점에서, 실수로 물리 delete를 호출하면 500/외래키 위반 오류가 발생하므로 Repository에 물리 삭제 메서드를 노출하지 않아야 한다.
2. **논리 삭제 필터 누락 위험**: 목록/상세 조회에서 deletedAt IS NULL 및 status 필터를 누락하면 CANCELLED 기록이 노출될 수 있다. Repository 쿼리에서 항상 필터가 적용되도록 설계.
3. **누적 매도수량 검증의 트랜잭션 경계**: Service에서 Allocation 조회(allocatedQuantity)와 ACTIVE Sale 합계 조회, 검증, 저장이 동일 트랜잭션 안에서 이루어져야 검증 누락이 없다.
4. **Investor JOIN 체인 쿼리 성능**: Sale → Allocation → Subscription → Investor 3단 JOIN 쿼리에 적절한 인덱스(특히 allocation_id)가 없는 경우 성능 저하 가능. 구현 단계에서 EXPLAIN 검증 필요.
5. **분할 매도 개수 증가**: 하나의 배정에 매도 기록이 많이 쌓이면 목록 조회 건수가 늘어날 수 있다. MVP에서는 전체 조회로 충분하며, 필요 시 Epic 11/12 조회 API에서 페이지네이션을 도입한다.

## 결정 필요 사항

1. **investorName 필터 일치 방식**: 정확 일치(EXACT) vs 부분 일치(LIKE). spec D-SL에서 명시되지 않아 구현 전 확정 필요.
2. **fee/tax/otherCost 값 검증**: 음수 검증 여부 및 값 상한(예: amount를 초과하지 않는지)을 구현 단계에서 확정.
3. **amount/price 0값 허용**: spec에서 0원 매도를 허용하므로 0 이상 검증만 적용하고 하한을 확정.
4. **Epic 9/10 연동**: Cost/Profit 엔티티가 도입될 때 Sale 도메인 수정 없이 신규 엔티티가 Sale을 참조하는 방향인지 재확인.