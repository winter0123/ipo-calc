# 분할 매도 기록 — 세부 기능 분해 (sale-management breakdown)

> **Epic**: Epic 8 (분할 매도 기록)
> **작성일**: 2026-09-21
> **API 계약 출처**: docs/spec/sale-management.md, docs/architecture/sale-management.md
> **의존 관계**: Epic 7 (allocation-management) 구현 완료 필요 — Sale은 Allocation을 FK로 참조

---

## 세부 기능(SF) 개요

총 **5개 SF**로 분해한다. 순차 의존 관계이며 각 SF는 PR 하나로 리뷰 가능한 크기다.

```
SF-1 (엔티티/Repository/마이그레이션) → SF-2 (등록 API, 누적 수량 검증)
  → SF-3 (목록/상세 조회) → SF-4 (수정 API, 누적 수량 재검증) → SF-5 (취소/논리 삭제)
```

---

## SF-1: Sale 엔티티, Repository, Flyway 마이그레이션

**범위**: `sale` 모듈의 DB 계층을 구축한다. Sale 엔티티, SaleRepository 인터페이스, V8 Flyway 마이그레이션 SQL을 생성한다.

**커버하는 AC**: 이 SF 자체는 코드 레이어 설정이며, 이후 SF에서 AC-001~AC-018 전체가 커버한다.

**선행 SF**: 없음 (첫 번째 SF)

**산출물 파일 경로** (architecture.md "모듈 소유권" 참고):
- `domain/model/sale/Sale.java` — JPA 엔티티 (id BIGINT GENERATED ALWAYS AS IDENTITY, allocationId FK, quantity, price, amount, saleDate, fee, tax, otherCost, status, deletedAt, createdAt, updatedAt)
- `repository/sale/SaleRepository.java` — Spring Data JPA 인터페이스 (deletedAt IS NULL 필터 기본 쿼리, saleDate 내림차순 정렬)
- `flyway/V8__create_sales_table.sql` — 테이블 생성 + FK 제약(`fk_sale_allocation`, ON DELETE RESTRICT) + 인덱스(idx_sale_allocation_id, idx_sale_status, idx_sale_sale_date)

**비고**: allocation_id에는 유니크 제약을 두지 않는다(D-SL-006). 하나의 배정에 여러 건의 매도가 가능하므로 데이터 무결성은 Service 계층의 누적 매도수량 검증이 담당한다.

---

## SF-2: 매도 등록 API (POST /sales)

**범위**: 매도 등록 엔드포인트를 구현한다. Controller, Service, DTO를 포함한다. 핵심은 Allocation 존재 검증과 누적 매도수량 검증(Σ ACTIVE Sale quantity ≤ allocatedQuantity)이다.

**커버하는 AC**: AC-001 (등록 성공), AC-002 (필수값 누락), AC-003 (매도수량 0 이하), AC-004 (Allocation 미존재 404), AC-005 (누적 매도수량 초과 400), AC-006 (분할 매도 등록 성공)

**선행 SF**: SF-1 (엔티티/Repository/Migration 필수)

**산출물 파일 경로**:
- `controller/sale/SaleController.java` — `POST /sales`
- `service/sale/SaleService.java` — `register()` 메서드 (Allocation 존재 확인, 누적 매도수량 검증, Sale 생성)
- `controller/sale/dto/SaleRegisterRequest.java`, `SaleResponse.java`
- `controller/sale/exception/SaleValidationException.java`, `SaleAllocationExceededException.java`, `SaleNotFoundException.java`, `SaleExceptionHandler.java`

**특이사항 — 누적 매도수량 검증 위치 (가장 중요)**:
- **Service 계층**에서 `@Transactional` 경계 안에 누적 매도수량 검증을 구현한다
- 검증 순서: (1) Allocation 조회 → allocatedQuantity 확인 (2) 기존 ACTIVE Sale들의 quantity 합계 조회 (3) `(기존 합계 + 신규 quantity) ≤ allocatedQuantity` 검증 (초과 시 400)
- **분할 매도 지원**: 하나의 Allocation에 여러 건의 Sale이 등록될 수 있으므로, 각 등록 시 기존 매도량 합계를 재계산한다
- 동시성 제어는 불필요(D-SL-004) — 개인 사용자의 순차 입력 서비스이므로 표준 트랜잭션 경계(READ_COMMITTED)로 충분
- **수정 시 재검증은 SF-4에서 처리** (등록/수정이 모두 Service 계층에서 동일 패턴으로 수행됨)
- 이 검증 로직이 **이 Epic에서 가장 주의가 필요한 부분**이다. 잘못 구현하면 배정수량을 초과하는 매도가 허용되어 데이터 무결성이 깨진다

---

## SF-3: 매도 목록/상세 조회 API (GET /sales, GET /sales/{id})

**범위**: 매도 목록 조회와 상세 조회 엔드포인트를 구현한다. `deletedAt IS NULL` 필터, `allocationId`/`investorId`/`investorName` 쿼리 파라미터 필터링, Sale → Allocation → Subscription → Investor JOIN 쿼리를 포함한다.

**커버하는 AC**: AC-007 (목록 조회), AC-008 (배정별 필터), AC-009 (투자자별 필터), AC-010 (상세 조회 성공), AC-011 (상세 조회 실패 404)

**선행 SF**: SF-1 (필수)

**산출물 파일 경로**:
- `controller/sale/SaleController.java` — `GET /sales`, `GET /sales/{id}` 추가
- `service/sale/SaleService.java` — `list()`, `getById()` 메서드 추가
- `repository/sale/SaleRepository.java` — `findByAllocationId()`, `findActiveWithFilters()` (JPQL JOIN 쿼리 — Sale → Allocation → Subscription → Investor 3단계 JOIN) 메서드 추가
- `controller/sale/exception/SaleNotFoundException.java` (이미 SF-2에서 생성되었으면 재사용)

**특이사항 — 투자자별 조회 JOIN 경로**:
- investorId/investorName 필터는 **3단계 JOIN 경로**(Sale → Allocation → Subscription → Investor)를 사용한다
- 이 3단계 JOIN의 성능이 리스크 요소이므로, 구현 단계에서 EXPLAIN 검증이 필요하다 (architecture.md 리스크 4)
- allocationId 필터는 Sale 직접 조회로 충분하다 (N:1 관계)
- 정렬은 `saleDate DESC, createdAt DESC` (D-SL-002)
- CANCELLED 기록은 목록에서 항상 제외 (AC-018)

---

## SF-4: 매도 수정 API (PATCH /sales/{id})

**범위**: 매도 수정 엔드포인트를 구현한다. 시점/필드 제약 없이 전체 허용(D-SL-003), 누적 매도수량 재검증을 포함한다.

**커버하는 AC**: AC-012 (수정 성공), AC-013 (유효성 오류), AC-014 (존재하지 않는 ID 404), AC-015 (누적 매도수량 재검증 초과 400), AC-016 (취소된 매도 수정 404)

**선행 SF**: SF-1, SF-2, SF-3 (필수)

**산출물 파일 경로**:
- `controller/sale/SaleController.java` — `PATCH /sales/{id}` 추가
- `service/sale/SaleService.java` — `update()` 메서드 추가 (부분 수정, 자기 자신 제외한 누적 합계 재계산, 재검증)
- `controller/sale/dto/SaleUpdateRequest.java`

**특이사항 — 누적 매도수량 재검증 위치**:
- **Service 계층**에서 `@Transactional` 경계 안에 재검증을 구현한다
- 검증 순서: (1) 대상 Sale 조회 (CANCELLED면 404) (2) 참조하는 Allocation의 allocatedQuantity 확인 (3) 기존 ACTIVE Sale들의 quantity 합계를 구하되 **자기 자신은 제외** (4) `(기존 합계 - 자기 자신 quantity + 수정 quantity) ≤ allocatedQuantity` 검증 (초과 시 400)
- **수정 시 재검증은 이 SF에서 가장 주의가 필요한 부분**이다. 이미 등록된 매도건의 수량을 수정할 때, 기존에 포함되었던 자기 자신의 수량을 합계에서 빼고 새 수량을 더해야 정확한 검증이 된다
- 취소된 매도(CANCELLED)는 조회에서 제외되므로 수정/취소 요청 시 404로 처리한다 (D-SL-005)

---

## SF-5: 매도 취소 API (DELETE /sales/{id})

**범위**: 매도 논리 삭제(취소) 엔드포인트를 구현한다. status를 CANCELLED로 변경하고 deletedAt을 설정한다.

**커버하는 AC**: AC-016 (취소 성공), AC-017 (존재하지 않는 ID 404), AC-018 (취소된 매도는 목록에서 제외), AC-019 (취소된 매도 재활성화 불가)

**선행 SF**: SF-1 (필수)

**산출물 파일 경로**:
- `controller/sale/SaleController.java` — `DELETE /sales/{id}` 추가
- `service/sale/SaleService.java` — `cancel()` 메서드 추가 (조회, CANCELLED 확인, status 변경, deletedAt 설정)

**비고**:
- 논리 삭제이므로 물리 삭제가 아니며, FK ON DELETE RESTRICT 제약과 무관하게 항상 성공한다 (D-SL-005)
- 취소된 매도(CANCELLED)는 다시 ACTIVE로 되돌릴 수 없다 — 정정이 필요하면 별도로 새 매도를 등록한다
- Allocation이 CANCELLED되어도 연결된 Sale 기록은 유지된다 (매도는 이미 발생한 사실)

---

## 수량 검증 위치 관련 결정

| 항목 | 결정 | 근거 |
|------|------|------|
| 누적 매도수량 검증 위치 | **Service 계층 (@Transactional 내)** | D-SL-003/D-SL-004, standard transaction boundary 내에서 검증과 저장이 동일 트랜잭션 |
| 등록 시 검증 시점 | **SAVE 전에 검증, 실패 시 400 + 트랜잭션 롤백** | `(기존 ACTIVE 합계 + 신규 quantity) ≤ allocatedQuantity` |
| 수정 시 검증 시점 | **SAVE 전에 재검증, 실패 시 400 + 트랜잭션 롤백** | `(기존 합계 - 자기자신 + 수정 quantity) ≤ allocatedQuantity` |
| 동시성 제어 | **불필요** | 개인 사용자의 순차 입력 서비스이므로 락 불필요 (D-SL-004) |
| 계정 소유권 검증 여부 | **필요 (403, 보안 점검 후 정정)** | Sale → Allocation → Subscription → Investor.accountId 체인으로 계정 소유 데이터 |
| 참조 경로 단계 수 | **1단계 (등록/수정 시)** | Sale → Allocation FK 직접 참조 |
| investorId 조회 시 참조 경로 | **3단계 (조회 시)** | Sale → Allocation → Subscription → Investor JOIN |
| `SecurityUtils.getCurrentAccountId()` 사용 여부 | **사용함** | 목록 조회를 현재 계정으로 한정 |

---

## 의존성 요약

```
SF-1 (엔티티/Repository/마이그레이션)
  └→ SF-2 (등록 API) ── Allocation 존재 검증 (404), 누적 매도수량 검증 (400) ★ 가장 중요
  └→ SF-3 (목록/상세 조회) ── 3단계 JOIN 쿼리 (Sale→Allocation→Subscription→Investor)
  └→ SF-4 (수정 API) ── 누적 매도수량 재검증 (자기자신 제외) ★ 가장 중요
  └→ SF-5 (취소/논리 삭제)
```

**가장 주의가 필요한 부분**: SF-2와 SF-4의 **누적 매도수량 검증 로직**이다. 이 검증이 잘못되면 배정수량을 초과하는 매도가 허용되어 데이터 무결성이 근본적으로 깨진다. 특히 SF-4의 수정 시 재검증은 자기 자신을 합계에서 제외하는 로직이 까다롭다. 구현 시 반드시 테스트로 검증해야 한다.

---

## 참고

- **문서 의존성**: `docs/spec/sale-management.md`, `docs/architecture/sale-management.md`
- **금지사항**: 코드/엔티티/DB 스키마 작성 금지 — 이 문서는 "무엇을 어떤 순서로 만들지"를 정하는 계획 문서
- **누적 매도수량 검증 리스크**: Service 계층에서 검증과 저장이 동일 트랜잭션 안에서 이루어져야 검증 누락이 없다 (architecture.md 리스크 3)
- **Investor JOIN 성능**: 3단계 JOIN 쿼리에 적절한 인덱스가 없는 경우 성능 저하 가능 — EXPLAIN 검증 필요 (architecture.md 리스크 4)
- **분할 매도**: 하나의 Allocation에 여러 건의 Sale이 등록될 수 있으므로, 목록 조회 시 페이지네이션은 MVP 범위 밖 (architecture.md 리스크 5)
