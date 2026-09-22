# 작업 로그: 분할 매도 기록 (sale-management, Epic 8)

## 2026-09-21 — Master 결정 사항 (Phase 0 spec 작성 전 스코프 정정)

`docs/research/sale-management.md`도 Epic 6/7과 동일한 패턴의 스코프 오류를
반복하고 있다: 실시간 동시 매도 요청에 대한 낙관적/비관적 락, "매도수량·
매도일시는 수정 불가(새 기록으로 대체)"라는 실거래 관례상의 엄격한 불변
정책, 아카이브 전략 등을 후보로 제시했다. 그러나 이 서비스는 **이미
증권사를 통해 완료된 매도 결과를 사용자가 사후에 입력·기록**하는
서비스이며, [[subscription-management]]/[[allocation-management]]의
D-S/D-A 결정과 동일한 원칙을 적용해야 한다.

### D-SL-001: Sale은 별도 워크플로 상태를 갖지 않는다
"일부매도/전량매도/보유중" 같은 상태는 Allocation의 배정수량과 Sale
누적수량을 비교해 계산하는 **파생 뷰**이며(Epic 11/12 조회·대시보드
API의 책임), Sale 엔티티 자체에는 저장하지 않는다. Sale은 다른 기록
엔티티와 동일하게 ACTIVE/CANCELLED만 사용한다.

### D-SL-002: 매도 순서는 별도 순번 필드 없이 saleDate + createdAt으로 충분
명시적 sale_sequence 필드는 MVP에서 불필요하다. 매도일시(saleDate) 기준
정렬하고, 동일 일시인 경우 생성 시각(createdAt)으로 안정적 정렬한다.

### D-SL-003: 수정은 시점/필드 제약 없이 전체 허용 (D-S-005/D-A-007과 동일)
연구 문서가 제시한 "매도수량·매도일시는 수정 불가, 새 기록으로 대체"는
실거래 시스템의 관례이지 사후 개인 기록 서비스에는 불필요한 제약이다.
사용자는 매도수량을 포함한 모든 필드를 자유롭게 수정할 수 있다. 단,
수정 후에도 **Σ(해당 Allocation의 ACTIVE 매도수량) ≤ 배정수량** 불변식은
항상 재검증한다(데이터 무결성 규칙은 유지).

### D-SL-004: 동시성 제어 불필요 (D-A-006/D-S-006과 동일 원칙)
개인 사용자가 자신의 기록을 순차 입력하는 서비스이므로 낙관적/비관적
락, SERIALIZABLE 격리 수준, 분산 락을 도입하지 않는다. 등록/수정 시
Service 계층에서 표준 트랜잭션 경계 안에 "누적 매도수량 ≤ 배정수량"을
검증하는 것으로 충분하다.

### D-SL-005: 삭제 정책은 논리 삭제만 (별도 감사 로그·아카이브 불필요)
다른 금융 기록 엔티티와 동일하게 물리 삭제 금지 + 논리 삭제(status=
CANCELLED, deletedAt)만 적용한다. 별도의 변경 이력 테이블, 감사 로그
테이블, 아카이브 전략은 MVP 범위 밖이다(created_at/updated_at 감사
필드로 충분).

### D-SL-006: Allocation : Sale = 1 : N (유니크 제약 없음)
하나의 배정에 여러 번의 매도가 있을 수 있으므로(work-plan 13장), Sale은
allocation_id를 FK로 참조하되 유니크 제약을 두지 않는다. 등록/수정 시
누적 매도수량 검증(D-SL-003)으로 데이터 무결성을 보장한다.

### D-SL-007: 매도 비용(수수료/세금/기타비용)은 사실 기록만, 수익 계산 로직 없음
Sale은 quantity, price, amount, saleDate, fee, tax, otherCost를 사용자
입력값으로 그대로 저장한다. 순수익 계산(수익 = 매도금액 - 수수료 - 세금 -
기타비용 - 매입원가 등)은 Epic 9(비용 기록)/Epic 10(수익 계산)의 책임이며
Sale 도메인에는 계산 로직을 두지 않는다.

이 결정들은 `docs/spec/sale-management.md` / `docs/architecture/sale-management.md`
작성 태스크 지시에 반영되었다.

---

## 2026-09-22 — SF-1 구현 완료 (Sale 엔티티, Repository, V9 마이그레이션)

**티켓/작업**: SF-1 (Epic 8 분할 매도 기록)

### 완료 작업
1. **Sale 엔티티 생성** (`domain/model/sale/Sale.java`)
   - 필드: id (BIGINT GENERATED ALWAYS AS IDENTITY PK), allocationId (FK, NOT NULL), quantity (NOT NULL), price/amount/fee/tax/otherCost (NULL 허용), saleDate (NOT NULL), status (DEFAULT 'ACTIVE'), deletedAt, createdAt, updatedAt
   - Lombok 어노테이션: @Entity, @Table(name="sales"), @Getter, @Setter, @Builder, @NoArgsConstructor, @AllArgsConstructor

2. **SaleRepository 생성** (`repository/sale/SaleRepository.java`)
   - `findByIdAndDeletedAtIsNull(Long id)` — 논리 삭제되지 않은 단건 조회
   - `findAllByDeletedAtIsNullOrderBySaleDateDescCreatedAtDesc()` — 전체 ACTIVE 목록 (saleDate DESC, createdAt DESC)
   - `findByAllocationIdAndDeletedAtIsNullAndStatusOrderBySaleDateDescCreatedAtDesc(Long allocationId, String status)` — 배정별 필터링
   - `sumActiveQuantityByAllocationId(Long allocationId)` — 누적 매도수량 합계 (ACTIVE만, FK 검증용)
   - `findActiveWithFilters(Long allocationId, Long investorId, String investorName)` — 3단계 JOIN (Sale→Allocation→Subscription→Investor) 필터링 쿼리

3. **Flyway 마이그레이션 V9 생성** (`V9__create_sales_table.sql`)
   - sales 테이블 생성 (모든 컬럼에 한글 COMMENT 포함)
   - FK 제약: `fk_sale_allocation` (ON DELETE RESTRICT) — allocations.id 참조
   - 인덱스: idx_sale_allocation_id, idx_sale_status, idx_sale_sale_date
   - allocation_id에 유니크 제약 없음 (D-SL-006, 하나의 배정에 여러 매도 가능)

4. **SaleRepositoryTest 작성** (6개 테스트, 모두 통과)
   - `saveSaleFindByIdReturnsSaleWithActiveStatus` — 저장/조회, status=ACTIVE, deletedAt=null 확인
   - `findAllByDeletedAtIsNullOrderBySaleDateDescCreatedAtDescReturnsOnlyNonDeletedSales` — CANCELLED 제외, 정렬 확인
   - `findByIdAndDeletedAtIsNullExcludesCancelledSale` — 취소된 기록 상세 조회 시 제외
   - `saveMultipleSalesWithSameAllocationIdAllowed` — 동일 allocationId로 여러 건 저장 가능 확인 (D-SL-006), 누적 합계 90 확인
   - `findByAllocationIdAndDeletedAtIsNullAndStatusReturnsActiveSalesOnly` — 배정별 ACTIVE만 조회
   - `sumActiveQuantityByAllocationIdReturnsCorrectSum` — ACTIVE 수량 합계만 반환 (CANCELLED 제외)

### 확인 사항
- `cd backend && DB_PASSWORD=vibe_local_password ./gradlew test --rerun` 실행 결과 **BUILD SUCCESSFUL**
- 전체 테스트 **247개 통과** (기존 241개 + 신규 6개)
- Flyway 마이그레이션 V9 정상 적용 (V8 allocations 다음 순서)
- FK 제약 `fk_sale_allocation` ON DELETE RESTRICT 정상 생성
- allocation_id 유니크 제약 없음 확인 (분할 매도 지원)

### 변경 파일
- `backend/src/main/java/com/vibeaiteam/salemanagement/domain/model/sale/Sale.java` (신규)
- `backend/src/main/java/com/vibeaiteam/salemanagement/repository/sale/SaleRepository.java` (신규)
- `backend/src/main/resources/db/migration/V9__create_sales_table.sql` (신규)
- `backend/src/test/java/com/vibeaiteam/salemanagement/repository/sale/SaleRepositoryTest.java` (신규)

### 남은 작업
- SF-2: 매도 등록 API (Controller, Service, DTO, 누적 매도수량 검증)
- SF-3: 매도 목록/상세 조회 API
- SF-4: 매도 수정 API (누적 매도수량 재검증)
- SF-5: 매도 취소 API (논리 삭제)

---

## 2026-09-22 — SF-2 구현 완료 (매도 등록 API, POST /sales, 누적 매도수량 검증)

**티켓/작업**: SF-2 (Epic 8 분할 매도 기록)

### 완료 작업
1. **SaleRegisterRequest DTO 생성** (`controller/sale/dto/SaleRegisterRequest.java`)
   - 필수 필드: allocationId, quantity, saleDate
   - 선택 필드: price, amount, fee, tax, otherCost
   - Lombok: @Getter, @Setter, @Builder, @NoArgsConstructor, @AllArgsConstructor

2. **SaleResponse DTO 생성** (`controller/sale/dto/SaleResponse.java`)
   - Sale 엔티티의 모든 필드 매핑 + 정적 팩토리 메서드 `from(Sale sale)`

3. **예외 클래스 3종 생성** (`controller/sale/exception/`)
   - `SaleValidationException` — 필수값 누락, 값 검증 실패 (400, VALIDATION_ERROR)
   - `SaleAllocationExceededException` — 누적 매도수량 초과 (400, ALLOCATION_EXCEEDED)
   - `SaleNotFoundException` — Allocation 미존재/취소된 Sale (404, NOT_FOUND)

4. **SaleExceptionHandler 생성** (`controller/sale/exception/SaleExceptionHandler.java`)
   - 3개 예외 타입 각각 적절한 HTTP 상태 코드와 에러 코드로 매핑
   - ErrorResponse(JSON UTF-8) 반환 패턴 준수 (AllocationExceptionHandler와 동일)

5. **SaleService.register() 구현** (`service/sale/SaleService.java`)
   - `@Transactional` 경계 안에서 **누적 매도수량 검증과 저장을 동일 트랜잭션**으로 수행
   - 검증 순서 (breakdown.md 52-55행 준수):
     1. Allocation 조회 (`findByIdAndDeletedAtIsNull`) → 미존재 시 404
     2. 기존 ACTIVE Sale들의 quantity 합계 조회 (`sumActiveQuantityByAllocationId`)
     3. `(기존 합계 + 신규 quantity) ≤ allocatedQuantity` 검증 → 초과 시 400 (ALLOCATION_EXCEEDED)
   - quantity > 0 검증, 선택 필드(price/amount/fee/tax/otherCost) 0 이상 검증
   - 분할 매도 지원: 하나의 Allocation에 여러 건의 Sale 등록 가능, 각 등록 시 기존 합계 재계산

6. **SaleController 생성** (`controller/sale/SaleController.java`)
   - `POST /sales` 엔드포인트, 201 Created + SaleResponse 반환
   - 한글 클래스/메서드 주석 필수 (Controller 클래스 요약 + 각 엔드포인트별 상세)

7. **SaleControllerTest 작성** (`controller/sale/SaleControllerTest.java`, 7개 테스트)
   - `registerSaleWithValidRequestReturns201AndSavedSale` — AC-001 등록 성공 (201)
   - `registerSaleWithMissingRequiredFieldsReturns400` — AC-002 필수값 누락 (400)
   - `registerSaleWithInvalidQuantityReturns400` — AC-003 quantity ≤ 0 (400)
   - `registerSaleWithUnknownAllocationReturns404` — AC-004 Allocation 미존재 (404)
   - `registerSaleWithExceedingCumulativeQuantityReturns400` — AC-005 누적 초과 (400, ALLOCATION_EXCEEDED)
   - `registerMultipleSalesForSameAllocationSucceedsIfWithinLimit` — AC-006 분할 매도 성공 (누적 ≤ 배정수량)
   - `registerSaleWithInvalidPriceReturns400` — 선택 필드 음수값 검증 (400)

### 확인 사항
- `cd backend && DB_PASSWORD=vibe_local_password ./gradlew test --rerun` 실행 결과 **BUILD SUCCESSFUL**
- 전체 테스트 **254개 통과** (기존 247개 + 신규 7개)
- 누적 매도수량 검증 로직이 `@Transactional` 안에서 검증→저장 순서로 안전하게 수행됨
- 분할 매도(동일 Allocation에 여러 Sale 등록) 정상 동작 확인: 30+40+30=100 (배정수량 100)까지 등록 성공
- 초과 시 400 반환 및 롤백 확인: 기존 60 + 신규 50 = 110 > 100 시 400, 기존 1건만 유지

### 변경 파일
- `backend/src/main/java/com/vibeaiteam/salemanagement/controller/sale/dto/SaleRegisterRequest.java` (신규)
- `backend/src/main/java/com/vibeaiteam/salemanagement/controller/sale/dto/SaleResponse.java` (신규)
- `backend/src/main/java/com/vibeaiteam/salemanagement/controller/sale/exception/SaleValidationException.java` (신규)
- `backend/src/main/java/com/vibeaiteam/salemanagement/controller/sale/exception/SaleAllocationExceededException.java` (신규)
- `backend/src/main/java/com/vibeaiteam/salemanagement/controller/sale/exception/SaleNotFoundException.java` (신규)
- `backend/src/main/java/com/vibeaiteam/salemanagement/controller/sale/exception/SaleExceptionHandler.java` (신규)
- `backend/src/main/java/com/vibeaiteam/salemanagement/service/sale/SaleService.java` (신규)
- `backend/src/main/java/com/vibeaiteam/salemanagement/controller/sale/SaleController.java` (신규)
- `backend/src/test/java/com/vibeaiteam/salemanagement/controller/sale/SaleControllerTest.java` (신규)

### 남은 작업
- SF-3: 매도 목록/상세 조회 API
- SF-4: 매도 수정 API (누적 매도수량 재검증)
- SF-5: 매도 취소 API (논리 삭제)

---
