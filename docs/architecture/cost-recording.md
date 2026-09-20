# 아키텍처 설계: 청약 비용 기록 (cost-recording)

> **식별자 결정**: 본 기능은 기존 프로젝트 일관성을 따라 **Long(IDENTITY)** 식별자를 사용한다. DB 스키마에서도 `id`/`_id` 컬럼은 BIGINT로 구현한다. UUID를 사용하지 않는다.
>
> **동시성 결정**: 이 서비스는 개인 사용자가 자신의 기록을 순차 입력하는 **사후 기록 서비스**이므로, 낙관적 락(@Version), 비관적 락(SELECT FOR UPDATE), 분산 락 등 별도 동시성 제어 메커니즘을 도입하지 않는다. 표준 트랜잭션 경계와 DB 유니크 제약만으로 충분하다.

## 아키텍처 개요 (레이어 구조)

subscription-management과 동일한 관심사 분리 원칙을 따른다. Spring Boot 백엔드는 다음 레이어 구조를 사용한다:

```
controller
application/service
domain
repository
```

- **controller**: HTTP 요청 처리, 입력 검증(필수값, 금액 범위, 상태값), 응답 매핑. 복잡한 업무 규칙은 포함하지 않음.
- **application/service**: 업무 규칙 유스케이스 오케스트레이션, 트랜잭션 경계. Subscription 존재 여부 확인, 취소(CANCELLED) 기록 수정 차단 포함.
- **domain**: 엔티티, 밸류오브젝트, 상태 상수 정의. Cost 엔티티 및 상태(ACTIVE/CANCELLED) 상수 정의.
- **repository**: 데이터 접근 계층, Spring Data JPA 인터페이스 정의. 물리 삭제 메서드를 노출하지 않는다.

비용 기록 관련 컴포넌트는 다음과 같이 구성한다:
- `controller.cost`: CostController
- `service.cost`: CostService
- `domain.model.cost`: Cost 엔티티
- `repository.cost`: CostRepository

> **참고**: Cost는 Subscription을 외래 키로 참조한다. 사용자(Account) 소유 검증은 Subscription → SecuritiesAccount → Account 체인을 통해 간접적으로 적용된다 (SecuritiesAccount가 이미 사용자 소유 엔티티이므로, 이 체인을 통해 연결된 Cost는 해당 사용자의 것임).

## 데이터 흐름

### 비용 등록 흐름
1. 사용자가 POST /costs 요청 (subscriptionId, costType, amount, incurredAt 필수; memo 선택)
2. Controller가 입력값 검증 (필수값 누락 400, amount 0 미만 400, status값이 허용되지 않는 값이면 400)
3. Service가 Subscription 존재 여부 확인 (존재하지 않으면 404)
4. Cost 엔티티 생성 (status=ACTIVE), 저장
5. 저장된 정보 반환 (201 Created)

### 비용 목록 조회 흐름
1. 사용자가 GET /costs 요청 (subscriptionId, investorId, investorName 쿼리 파라미터 선택)
2. Service에서 삭제되지 않은(deletedAt IS NULL) ACTIVE 상태 비용 목록 조회
3. 쿼리 파라미터가 있으면 해당 조건으로 필터링 (investorId/investorName은 JOIN 체인으로 경유 조회)
4. 목록 반환 (incurredAt 내림차순, 동일 incurredAt인 경우 createdAt 내림차순)

### 비용 상세 조회 흐름
1. 사용자가 GET /costs/{id} 요청
2. Service에서 삭제되지 않은 비용 조회 (findById + deletedAt 확인 + status 확인)
3. 없거나 CANCELLED 상태이면 404 Not Found 반환
4. 성공 시 비용 정보 반환

### 비용 수정 흐름
1. 사용자가 PATCH /costs/{id} 요청
2. Controller가 입력값 검증 (제공된 필드만 수정, 유효성 오류 시 400)
3. Service에서 대상 비용 조회 (없으면 404, CANCELLED이면 404)
4. 필드 업데이트 및 저장, 결과 반환

### 비용 취소 흐름 (논리 삭제)
1. 사용자가 DELETE /costs/{id} 요청
2. Service에서 대상 비용 조회 (없으면 404, 이미 CANCELLED이면 404)
3. status를 CANCELLED로 변경, deletedAt 설정
4. 저장 및 204 No Content 반환
5. FK(ON DELETE RESTRICT) 제약과 무관하게 항상 성공 — 물리 삭제가 아니라 status UPDATE이므로 기존 참조 관계가 유지됨

## API 설계

### 엔드포인트 목록
1. POST /costs - 비용 등록
2. GET /costs - 비용 목록 조회 (subscriptionId, investorId, investorName 쿼리 파라미터 선택)
3. GET /costs/{id} - 비용 상세 조회
4. PATCH /costs/{id} - 비용 수정
5. DELETE /costs/{id} - 비용 취소 (논리 삭제)

### 요청/응답 DTO (JPA 엔티티 직접 노출 금지, DTO 사용)

#### 비용 등록 요청 (POST /costs)
```json
{
  "subscriptionId": 1,
  "costType": "청약수수료",
  "amount": 25000,
  "incurredAt": "2026-09-10T10:00:00",
  "memo": "OO증권 청약수수료 (선택, null 허용)"
}
```

#### 비용 등록 응답 (성공, 201 Created)
```json
{
  "id": 1,
  "subscriptionId": 1,
  "costType": "청약수수료",
  "amount": 25000,
  "incurredAt": "2026-09-10T10:00:00",
  "memo": "OO증권 청약수수료",
  "status": "ACTIVE",
  "deletedAt": null,
  "createdAt": "2026-09-21T10:00:00Z",
  "updatedAt": "2026-09-21T10:00:00Z"
}
```

#### 비용 목록 조회 응답 (성공)
```json
[
  {
    "id": 1,
    "subscriptionId": 1,
    "costType": "청약수수료",
    "amount": 25000,
    "incurredAt": "2026-09-10T10:00:00",
    "memo": "OO증권 청약수수료",
    "status": "ACTIVE",
    "deletedAt": null,
    "createdAt": "2026-09-21T10:00:00Z",
    "updatedAt": "2026-09-21T10:00:00Z"
  }
]
```

#### 비용 수정 요청 (PATCH /costs/{id}) — 제공된 필드만 갱신
```json
{
  "costType": "청약수수료",
  "amount": 30000,
  "incurredAt": "2026-09-10T11:00:00",
  "memo": "정정된 청약수수료"
}
```

#### 비용 취소 응답 (성공, 204 No Content)
- 본문 없음. status=CANCELLED, deletedAt 설정.

### 에러 응답 형식 (subscription-management과 공통 형식 사용)
```json
{
  "error": "ERROR_CODE",
  "message": "사용자에게 표시할 메시지"
}
```

#### 주요 에러 코드
- VALIDATION_ERROR: 입력값 검증 실패 (필수값 누락, 금액 0 미만 등)
- NOT_FOUND: 비용을 찾을 수 없음 (미등록 또는 CANCELLED 상태)
- SUBSCRIPTION_NOT_FOUND: 참조하는 Subscription이 존재하지 않음

## DB 설계

### Cost 테이블

| 컬럼명 | 타입 | 제약 | 설명 |
|--------|------|------|------|
| id | BIGINT | PK, GENERATED ALWAYS AS IDENTITY | 비용 고유 식별자 (Long IDENTITY) |
| subscription_id | BIGINT | NOT NULL, FK -> subscriptions.id ON DELETE RESTRICT | 연결된 청약 참조 (N:1) |
| cost_type | VARCHAR(255) | NOT NULL | 비용종류 (자유 텍스트 — "청약수수료", "계좌개설비" 등) |
| amount | BIGINT | NOT NULL | 금액 (원 단위, 0 이상) |
| incurred_at | TIMESTAMP | NOT NULL | 발생일시 |
| memo | VARCHAR(255) | NULL 허용 | 메모 (선택) |
| status | VARCHAR(10) | NOT NULL, DEFAULT 'ACTIVE' | 상태 (ACTIVE/CANCELLED) |
| deleted_at | TIMESTAMP | NULL 허용 | 논리 삭제 시각 |
| created_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 생성 일시 |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 수정 일시 (JPA @LastModifiedDate로 갱신) |

### 인덱스
- PRIMARY KEY (id)
- INDEX idx_cost_subscription_id (subscription_id) — 청약별 목록 조회 최적화
- INDEX idx_cost_status (status) — 상태별 필터링 최적화

> **유니크 제약 없음**: spec AC-005, D-C-002에 따라 하나의 Subscription에 여러 건의 Cost가 허용되므로 유니크 제약을 두지 않는다.

## 참조 무결성 처리

### 외래 키 제약
단일 참조 키를 **ON DELETE RESTRICT**로 설정하여 물리 삭제를 DB 레벨에서 강제한다.

```sql
ALTER TABLE costs
    ADD CONSTRAINT fk_cost_subscription
    FOREIGN KEY (subscription_id) REFERENCES subscriptions (id)
    ON DELETE RESTRICT;
```

```
Subscription (One) <---- (Many) Cost
```

### 논리 삭제와 FK 관계
- **논리 삭제(status=CANCELLED, deletedAt 설정)는 FK 제약과 무관하게 항상 가능하다.** ON DELETE RESTRICT는 레코드 삭제(물리 삭제)만 제한하며 UPDATE를 통한 status 변경은 막지 않으므로, 연결된 Subscription이 있어도 취소는 항상 성공한다.
- 비용 논리 삭제 시 참조하는 Subscription은 변경하지 않는다.
- Subscription이 CANCELLED되어도 연결된 Cost 기록은 그대로 유지된다 — 비용은 이미 발생한 사실이므로 보존된다.

## 투자자별/투자자명 조회 (JOIN 체인 설계)

spec에서 정의한 `investorId`, `investorName` 쿼리 파라미터 필터링은 Cost → Subscription → Investor 경유로 처리한다.

### 조회 경로
```
Cost (costs.subscription_id)
  → Subscription (subscriptions.investor_id)
    → Investor (investors.id)
```

### JPQL/jQuery 쿼리 방향
- `investorId` 필터: `Cost JOIN Subscription s JOIN Investor i WHERE i.id = :investorId`
- `investorName` 필터: `Cost JOIN Subscription s JOIN Investor i WHERE i.name LIKE :investorName%`
- 두 파라미터 동시 사용: AND 조건으로 결합

> **주의**: 투자자별 필터는 INNER JOIN이므로, Subscription이 없는 Cost는 투자자 필터 적용 시 결과에서 자연스럽게 제외된다. Subscription을 직접 지정한 경우(`subscriptionId` 파라미터)는 투자자 경유 없이 직접 필터링한다.

## Sale과의 책임 경계 (D-C-001)

### 왜 Cost와 Sale이 별개인가

| 구분 | Cost (Epic 9) | Sale (Epic 8) |
|------|---------------|---------------|
| 대상 시점 | 청약/취득 단계 | 매도 단계 |
| 엔티티 | Cost | Sale |
| 필드 | costType, amount | fee, tax, otherCost |
| 비용 성격 | 취득 관련 비용 (청약수수료, 계좌개설비, 이체수수료 등) | 매도 관련 비용 (매도수수료, 증권거래세, 양도소득세 등) |
| 수익 계산 위치 | `매도금액 − 취득 관련 비용 − 매도 수수료 − 세금 − 기타 비용 = 순수익`의 차감 항목 | 같은 공식의 매도 수수료/세금/기타 비용 항목 |

- 수익 공식에서 "취득 관련 비용" 항목이 Cost 엔티티, "매도 수수료/세금/기타 비용" 항목이 Sale 엔티티의 fee/tax/otherCost 필드에 각각 대응한다.
- 두 도메인은 독립적으로 존재하며, Epic 10(수익 계산)에서 함께 조회되어 합산 계산된다.
- Sale의 비용 필드에 이미 매도 수수료/세금이 기록되므로, Cost에서 매도 관련 비용을 다시 설계하지 않는다.

## Profit 확장 지점 (Epic 10, 향후)

> **참고**: 이 섹션에는 코드가 아니라 설계 방향만 기술한다. Sale 아키텍처의 Cost/Profit 확장 지점 서술 방식을 따른다.

### Epic 10 구현 시 예상되는 확장

1. **Cost 조회 API 활용**: ProfitService에서 Cost 테이블을 직접 조회하여 `WHERE subscription_id = :subId AND status = 'ACTIVE'` 조건으로 취득 관련 비용 합계를 산출한다.
2. **수익 계산 공식 반영**: `매도금액(Sale.amount) − 취득 관련 비용(Cost 합계) − 매도 수수료(Sale.fee) − 세금(Sale.tax) − 기타 비용(Sale.otherCost) = 순수익`
3. **Cost 도메인 변경 없음**: Epic 10은 Cost 엔티티를 변경하지 않고, CostRepository를 통해 읽기 전용으로 합계를 조회한다.
4. **Profit 엔티티 신규 생성**: 수익 계산 결과를 저장하는 Profit 엔티티가 추가되며, 이 Profit이 Cost와 Sale을 모두 참조한다.

### 현재 단계에서의 제약
- Cost 도메인에 수익 계산 로직을 두지 않는다 (D-C-005).
- Cost의 ACTIVE 레코드만 취득 관련 비용 합계에 포함된다 (CANCELLED은 제외).

## Flyway 마이그레이션 파일명 제안

> **주의**: 실제로는 기존 마이그레이션 번호를 확인한 뒤 이어지는 번호를 사용한다. 아래 제안은 V1=accounts, V2=investors, V3=securities_firms, V4=securities_accounts, V5=ipos, V6=subscriptions, V7=allocations, V8=sales 순서를 가정한 것이다.

### V9__create_costs_table.sql (제안)

> **주의(PostgreSQL 문법)**: 이 프로젝트의 DB는 PostgreSQL이다. PK 자동증가는 `GENERATED ALWAYS AS IDENTITY`를 사용하며, MySQL 문법(`AUTO_INCREMENT`, `ENGINE=InnoDB`, `ON UPDATE CURRENT_TIMESTAMP`)을 사용하지 않는다.

```sql
CREATE TABLE costs (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    subscription_id BIGINT NOT NULL,
    cost_type VARCHAR(255) NOT NULL,
    amount BIGINT NOT NULL,
    incurred_at TIMESTAMP NOT NULL,
    memo VARCHAR(255) NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
    deleted_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 외래 키 제약
ALTER TABLE costs
    ADD CONSTRAINT fk_cost_subscription
    FOREIGN KEY (subscription_id) REFERENCES subscriptions (id)
    ON DELETE RESTRICT;

-- 조회/필터링 최적화 인덱스
CREATE INDEX idx_cost_subscription_id ON costs (subscription_id);
CREATE INDEX idx_cost_status ON costs (status);
```

## 모듈 소유권

- **Cost 엔티티**: domain.model.cost 패키지 소유
- **CostRepository**: repository.cost 패키지 소유
- **CostController**: controller.cost 패키지 소유
- **CostService**: service.cost 패키지 소유
- **DTO 클래스**: service.cost.dto 또는 controller.cost.dto 패키지 소유 (기존 프로젝트 DTO 규칙에 따름)

## 의존성

- **JPA/Hibernate**: subscription-management 아키텍처에서 이미 사용하는 spring-boot-starter-data-jpa 재사용 (신규 라이브러리 없음)
- **추가 라이브러리 불필요**: 외부 파싱 라이브러리나 별도 동시성 제어 라이브러리가 없다. 표준 Spring Boot + JPA 구성만으로 구현 가능.

## 리스크

1. **costType 자유 텍스트 관리**: costType이 Enum이 아니므로 동일한 비용종류를 사용자가 다른 이름으로 입력할 수 있다. 현재 MVP에서는 이 문제를 애플리케이션 단에서 해결하지 않으며, 향후 자동 완성 또는 사전 정의 목록이 필요해질 수 있다.
2. **FK ON DELETE RESTRICT 의존**: 논리 삭제가 애플리케이션 로직(Service)에서만 수행된다는 점에서, 실수로 물리 delete를 호출하면 500/외래키 위반 오류가 발생하므로 Repository에 물리 삭제 메서드를 노출하지 않아야 한다.
3. **논리 삭제 필터 누락 위험**: 목록/상세 조회에서 deletedAt IS NULL 및 status 필터를 누락하면 CANCELLED 기록이 노출될 수 있다. Repository 쿼리에서 항상 필터가 적용되도록 설계.
4. **memo 길이 상한**: VARCHAR(255)로 상한을 두며, 이를 초과하는 입력은 Controller에서 400으로 처리한다.
