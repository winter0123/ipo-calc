# 배정 결과 관리 — 세부 기능 분해 (allocation-management breakdown)

> **Epic**: Epic 7 (배정 결과 관리)
> **작성일**: 2026-09-21
> **API 계약 출처**: docs/spec/allocation-management.md, docs/architecture/allocation-management.md
> **의존 관계**: Epic 6 (subscription-management) 구현 완료 예정 — Allocation은 Subscription을 FK로 참조

---

## 세부 기능(SF) 개요

총 **5개 SF**로 분해한다. 순차 의존 관계이며 각 SF는 PR 하나로 리뷰 가능한 크기다.

```
SF-1 (엔티티/Repository/마이그레이션) → SF-2 (등록 API, Subscription 존재 검증)
  → SF-3 (목록/상세 조회) → SF-4 (수정 API) → SF-5 (취소/논리 삭제)
```

---

## SF-1: Allocation 엔티티, Repository, Flyway 마이그레이션

**범위**: `allocation` 모듈의 DB 계층을 구축한다. Allocation 엔티티, AllocationRepository 인터페이스, V7 Flyway 마이그레이션 SQL을 생성한다.

**커버하는 AC**: 이 SF 자체는 코드 레이어 설정이며, 이후 SF에서 AC-001~AC-018 전체가 커버한다.

**선행 SF**: 없음 (첫 번째 SF)

**산출물 파일 경로** (architecture.md "모듈 소유권" 참고):
- `domain/model/allocation/Allocation.java` — JPA 엔티티 (id BIGINT GENERATED ALWAYS AS IDENTITY, subscriptionId FK, allocatedQuantity, allocatedAt, allocationMethod, allocationPrice, note, status, deletedAt, createdAt, updatedAt)
- `repository/allocation/AllocationRepository.java` — Spring Data JPA 인터페이스 (deletedAt IS NULL 필터 기본 쿼리, partial unique index 대응)
- `flyway/V7__create_allocations_table.sql` — 테이블 생성 + FK 제약 + 부분 유니크 인덱스 `uk_allocation_active_subscription`

**비고**: 부분 유니크 인덱스 `ON allocations (subscription_id) WHERE status = 'ACTIVE'`는 CANCELLED 재등록 허용(AC-006)을 DB 레벨에서도 보장한다.

---

## SF-2: 배정 등록 API (POST /allocations)

**범위**: 배정 등록 엔드포인트를 구현한다. Controller, Service, DTO를 포함한다. 핵심은 subscriptionId 존재 검증과 중복 검사(ACTIVE 기준)이다.

**커버하는 AC**: AC-001 (등록 성공), AC-002 (필수값 누락), AC-003 (배정수량 값 오류), AC-004 (Subscription 미존재), AC-005 (중복 409), AC-006 (취소 후 재등록 허용)

**선행 SF**: SF-1 (엔티티/Repository/Migration 필수)

**산출물 파일 경로**:
- `controller/allocation/AllocationController.java` — `POST /allocations`
- `service/allocation/AllocationService.java` — `register()` 메서드 (Subscription 존재 확인, allocatedQuantity 유효성 검증, ACTIVE 중복 검사)
- `controller/allocation/dto/AllocationRegisterRequest.java`, `AllocationResponse.java`
- `controller/allocation/exception/AllocationValidationException.java`, `AllocationConflictException.java`, `AllocationNotFoundException.java`, `AllocationExceptionHandler.java`

**특이사항 — Subscription 존재 검증 (가장 중요)**:
- POST 요청의 `subscriptionId`가 실제 존재하는 Subscription을 참조하는지 Service 계층에서 반드시 확인한다 (존재하지 않으면 404)
- **계정 소유권 검증 여부**: **필요함 (403, 보안 점검 후 정정)**. Allocation은 Subscription → Investor.accountId 체인으로 계정 소유 데이터다. 등록·조회·수정·취소 모두 소유권을 검증하고 목록은 현재 계정으로 한정한다. Subscription 존재 여부 확인(404)만으로 참조 무결성을 보장한다.
- **참조 경로 단계 수**: Allocation → Subscription (1단계 FK). investorId 쿼리 파라미터로 필터링할 때만 Allocation → Subscription → Investor (3단계 JOIN)이 필요하지만, 이는 조회 시의 참조 경로이지 등록 시의 검증 단계가 아니다.
- 중복 검사는 Service 계층 헬퍼(1차 방어) + DB 부분 유니크 인덱스(보조 방어) 이중으로 수행한다 (architecture.md "이중 방어" 원칙, Epic 5 D-IPO-001 패턴)
- CANCELLED 기록이 있어도 재등록은 허용 (AC-006)

---

## SF-3: 배정 목록/상세 조회 API (GET /allocations, GET /allocations/{id})

**범위**: 배정 목록 조회와 상세 조회 엔드포인트를 구현한다. `deletedAt IS NULL` 필터, `subscriptionId`/`investorId` 쿼리 파라미터 필터링, investorId JOIN 쿼리를 포함한다.

**커버하는 AC**: AC-007 (목록 조회), AC-008 (청약별 필터), AC-009 (투자자별 필터), AC-010 (상세 조회 성공), AC-011 (상세 조회 실패 404)

**선행 SF**: SF-1 (필수)

**산출물 파일 경로**:
- `controller/allocation/AllocationController.java` — `GET /allocations`, `GET /allocations/{id}` 추가
- `service/allocation/AllocationService.java` — `list()`, `getById()` 메서드 추가
- `repository/allocation/AllocationRepository.java` — `findBySubscriptionId()`, `findByInvestorId()` (JPQL JOIN 쿼리) 메서드 추가
- `controller/allocation/exception/AllocationNotFoundException.java` (이미 SF-2에서 생성되었으면 재사용)

**비고**: 투자자별 조회는 Allocation → Subscription → Investor JOIN 경유로 수행하며, 이 경로에서 3단계 참조가 발생한다. 하지만 이는 조회 시의 데이터 경로일 뿐, 엔티티 소유권 관점에서는 여전히 글로벌 기준정보다.

---

## SF-4: 배정 수정 API (PATCH /allocations/{id})

**범위**: 배정 수정 엔드포인트를 구현한다. 부분 수정(제공된 필드만 갱신), 시점 제약 없음(D-A-007), 매도 연결 시 수량 축소 제한의 확장 지점을 포함한다.

**커버하는 AC**: AC-012 (수정 성공), AC-013 (유효성 오류), AC-014 (존재하지 않는 ID 404), AC-015 (매도 연결 시 수량 축소 제한)

**선행 SF**: SF-1, SF-2, SF-3 (필수)

**산출물 파일 경로**:
- `controller/allocation/AllocationController.java` — `PATCH /allocations/{id}` 추가
- `service/allocation/AllocationService.java` — `update()` 메서드 추가 (부분 수정, 유효성/중복 검사, 자기 자신 제외)
- `controller/allocation/dto/AllocationUpdateRequest.java`

**특이사항 — 매도(Sale) 연결 시 수량 축소 제한**:
- spec AC-015에 따라, 배정에 매도(Sale) 기록이 연결되어 있고 신규 배정수량이 연결된 매도수량 합계 미만으로 요청되면 400 Bad Request로 제한해야 한다
- **그러나 Epic 7 구현 시점에는 Sale 테이블이 존재하지 않으므로**, 이 검증 로직은 현재 SF-4에서는 구현하지 않고 **확장 지점으로만 남겨 둔다**
- Service 시그니처를 나중에 SaleService를 주입받아 확장할 수 있는 구조로 설계한다 (architecture.md "향후 확장 지점" 참고)
- 현재 단계에서는 allocationId 기반 Subscription의 quantity 비교 검증만 수행한다

---

## SF-5: 배정 취소 API (DELETE /allocations/{id})

**범위**: 배정 논리 삭제(취소) 엔드포인트를 구현한다. status를 CANCELLED로 변경하고 deletedAt을 설정한다.

**커버하는 AC**: AC-016 (취소 성공), AC-017 (존재하지 않는 ID 404), AC-018 (취소된 배정 목록에서 제외)

**선행 SF**: SF-1 (필수)

**산출물 파일 경로**:
- `controller/allocation/AllocationController.java` — `DELETE /allocations/{id}` 추가
- `service/allocation/AllocationService.java` — `cancel()` 메서드 추가 (조회, 상태 확인, status 변경, deletedAt 설정)

**비고**: 논리 삭제이므로 물리 삭제가 아니며, FK ON DELETE RESTRICT 제약과 무관하게 항상 성공한다. CANCELLED 기록은 조회 API에서 항상 제외된다.

---

## 계정 소유권 검증 관련 결정

| 항목 | 결정 | 근거 |
|------|------|------|
| Account 소유권 검증 필요 여부 | **필요 (403)** | Allocation → Subscription → Investor.accountId 체인으로 계정 소유 데이터 (보안 점검 정정) |
| Subscription 존재 검증 | **필수** | POST/PATCH 시 subscriptionId가 실제 존재하는지 확인, 미존재 시 404 |
| 참조 경로 단계 수 | **1단계 (등록/수정 시)** | Allocation → Subscription FK 직접 참조 |
| investorId 조회 시 참조 경로 | **3단계 (조회 시)** | Allocation → Subscription → Investor JOIN |
| `SecurityUtils.getCurrentAccountId()` 사용 여부 | **사용함** | 목록 조회를 현재 계정으로 한정하고 OwnershipGuard로 단건을 검증 |

---

## 의존성 요약

```
SF-1 (엔티티/Repository/마이그레이션)
  └→ SF-2 (등록 API) ── Subscription 존재 검증 (404), 중복 검사 (409)
  └→ SF-3 (목록/상세 조회) ── investorId JOIN 쿼존 (3단계 경유)
  └→ SF-4 (수정 API) ── 매도 연결 시 수량 축소 제한 (확장 지점, Epic 8 연동 시 확정)
  └→ SF-5 (취소/논리 삭제)
```

각 SF는 완료 후 PR을 생성하고 Master 검증 후 main에 병합한다. SF-2가 가장 주의가 필요한데, subscriptionId 존재 검증(404)과 ACTIVE 중복 검사(409)의 이중 방어가 정확히 동작해야 하기 때문이다.

---

## 참고

- **문서 의존성**: `docs/spec/allocation-management.md`, `docs/architecture/allocation-management.md`
- **금지사항**: 코드/엔티티/DB 스키마 작성 금지 — 이 문서는 "무엇을 어떤 순서로 만들지"를 정하는 계획 문서
- **부분 유니크 인덱스 검증**: 구현 시 반드시 CANCELLED 후 재등록 시나리오를 테스트로 확인해야 함 (architecture.md 리스크 1)
- **논리 삭제 필터 누락 방지**: 모든 Repository 쿼리에 `deletedAt IS NULL` 필터가 반드시 포함되는지 검증해야 함
