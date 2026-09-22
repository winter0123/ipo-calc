# 작업 로그: 배정 결과 관리 (allocation-management, Epic 7)

## 2026-09-21 — Master 결정 사항 (Phase 0 spec 작성 전 스코프 정정)

`docs/research/allocation-management.md`도 Epic 6(Subscription)과 동일한 패턴의
스코프 오류를 갖고 있다. 조사 문서 자체가 "work-plan 12장은 배정 결과를
'관리'한다고 했지 '계산'한다고 하지 않았다"는 올바른 전제를 확인했음에도,
실제 결론부에서는 실시간 워크플로 상태 머신(배정대기→배정완료)과 낙관적/
비관적 락 등 실시간 동시성 제어를 여전히 후보로 제시했다. 이는 이 서비스가
**이미 증권사에서 확정된 배정 결과를 사용자가 사후에 입력·기록**하는
서비스라는 전제와 맞지 않는다([[subscription-management]]의 D-S-003/D-S-006과
동일한 논리). 또한 "잔여수량 = 배정수량 - 총매도수량" 계산은 매도(Sale,
Epic 8) 데이터에 의존하므로 Allocation 엔티티 자체의 책임이 아니다.

### D-A-001: Allocation은 상태 전이 머신을 갖지 않는다
Allocation 레코드는 "이미 확정된 배정 결과를 기록했다"는 사실 자체이므로
생성 시점에 이미 최종 상태다. 배정대기/배정완료 같은 전이는 불필요하다.
다른 기록 엔티티(Subscription 등)와 동일하게 **ACTIVE/CANCELLED**만
사용한다(잘못 입력한 기록을 취소하는 용도).

### D-A-002: Subscription 1건당 Allocation은 정확히 1건 (1:1)
한 청약에 대한 배정 결과는 하나만 존재한다(부분 배정도 하나의 배정수량 값으로
표현되며, 여러 차례에 걸친 배정 이력 관리는 MVP 범위 밖). `subscription_id`에
유니크 제약을 걸어 중복 배정 기록을 방지한다(연구 문서의 "중복 배정 방지"
요구를 이 방식으로 충족).

### D-A-003: 잔여수량 계산은 Allocation의 책임이 아니다
잔여수량(배정수량 - 총매도수량)은 매도(Sale, Epic 8) 데이터에 의존하는
파생값이다. Allocation은 **배정수량(사실 기록)만** 저장하고, 잔여수량
계산·조회는 Epic 8(Sale) 또는 Epic 11(조회 API)에서 Allocation과 Sale을
함께 조회해 계산한다.

### D-A-004: 배정 방식은 자유 텍스트 메모로 기록한다 (Enum 아님)
균등/비례/추첨 등 배정 방식은 증권사마다 표현이 다를 수 있는 참고 정보이므로
MVP에서는 `allocationMethod` 자유 텍스트(선택 필드)로 기록한다. Enum
제약은 두지 않는다.

### D-A-005: 배정 수량 검증 — 배정수량 ≤ 청약수량만 확인
조사 결론대로 "배정수량 ≤ 청약수량, 배정수량 ≥ 0"은 유효한 데이터 무결성
규칙이므로 유지한다. 이는 실시간 동시성 문제가 아니라 단순 값 검증이다.

### D-A-006: 동시성 제어 불필요 (D-S-006과 동일 원칙)
개인 사용자가 자신의 기록을 순차 입력하는 서비스이므로 낙관적/비관적 락,
분산 락 등을 도입하지 않는다. 표준 트랜잭션 경계 + DB 유니크 제약
(subscription_id 유니크)으로 충분하다.

### D-A-007: 배정 취소(수정)는 시점 제약 없이 허용
사후 기록 서비스이므로 "배정 확정 후 취소 제한" 같은 실시간 제약은
적용하지 않는다. 단, 이미 매도(Sale) 기록이 연결된 배정을 매도수량 미만으로
축소하는 수정은 참조 무결성 위반으로 차단한다(Subscription의 D-S-005와
동일 패턴, 세부 로직은 Epic 8 구현 시 확정).

이 결정들은 `docs/spec/allocation-management.md` /
`docs/architecture/allocation-management.md` 작성 태스크 지시에 반영되었다.

## 2026-09-22 — SF-1 구현 완료 (Allocation 엔티티/Repository/마이그레이션)

`docs/breakdown/allocation-management.md`의 SF-1을 구현했다. 브랜치
`winter0123/allocation-management` (backend-3 워크스페이스).

**산출물**:
- `backend/src/main/java/com/vibeaiteam/allocationmanagement/domain/model/allocation/Allocation.java`
  — subscriptionmanagement의 Subscription.java와 동일 스타일(Lombok
  Builder/Getter/Setter, status 기본값 "ACTIVE")
- `backend/src/main/java/com/vibeaiteam/allocationmanagement/repository/allocation/AllocationRepository.java`
  — `findByIdAndDeletedAtIsNull`, `findAllByDeletedAtIsNullOrderByCreatedAtDesc`,
  `findBySubscriptionIdAndStatusAndDeletedAtIsNull` (SF-2 중복 검사용)
- `backend/src/main/resources/db/migration/V8__create_allocations_table.sql`
  — 기존 최신이 V7(subscriptions)이므로 V8로 생성. architecture.md 제안 DDL을
  그대로 반영: `fk_allocation_subscription` (ON DELETE RESTRICT),
  부분 유니크 인덱스 `uk_allocation_active_subscription (subscription_id)
  WHERE status = 'ACTIVE'`, 조회 최적화 인덱스 2개. 테이블/컬럼 전체에
  한글 주석 추가(AGENTS.md 15장 규칙).
- `backend/src/test/java/com/vibeaiteam/allocationmanagement/repository/allocation/AllocationRepositoryTest.java`
  — 저장/조회, 부분 유니크 인덱스 동작(ACTIVE 중복 시
  `DataIntegrityViolationException`, CANCELLED 후 재등록 허용) 검증. 총 6개
  테스트.

**주의사항 발견**: subscriptions 테이블과 달리 allocations는 architecture.md
지시대로 `subscription_id`에 실제 FK 제약(`ON DELETE RESTRICT`)을 걸었다.
따라서 Repository 테스트에서 Allocation을 저장하기 전에 반드시 실제
Subscription 행을 먼저 저장해야 한다(FK 위반 방지). 또한 subscriptions
테이블 자체에 `(investor_id, securities_account_id, ipo_id)` 조합의 부분
유니크 제약이 있으므로, 테스트 헬퍼에서 Subscription을 여러 건 생성할 때는
`ipoId`를 매번 다르게 줘야 한다(동일 조합으로 여러 건 만들면 테스트 데이터
셋업 단계에서부터 유니크 위반 발생).

**검증**: `cd backend && DB_PASSWORD=vibe_local_password ./gradlew test --rerun`
→ BUILD SUCCESSFUL, 220 tests completed, 0 failed (기존 214개 + 신규 6개).

**남은 작업**: SF-2(등록 API, Subscription 존재 검증 + ACTIVE 중복 검사)부터
SF-5(취소)까지 순차 구현 필요. 커밋/PR/머지는 Master가 진행.

## 2026-09-22 — SF-2 구현 완료 (배정 등록 API, POST /allocations)

`docs/breakdown/allocation-management.md`의 SF-2를 구현했다. 브랜치
`winter0123/allocation-register` (backend-3 워크스페이스, origin/main 기준
생성, SF-1 포함). AC-001~006 커버.

**산출물**:
- `controller/allocation/AllocationController.java` — `POST /allocations`,
  한글 클래스/메서드 주석 포함(subscriptionmanagement SF-2 스타일)
- `service/allocation/AllocationService.java` — `register()`.
  SubscriptionService.register()와 동일한 순서(필수값 검증 → Subscription
  존재 확인 → 값 검증 → 중복 검사 → 저장, `DataIntegrityViolationException`
  catch)를 따르되 **소유권 검증(SecurityUtils) 단계는 제외**했다 — breakdown
  문서의 결정표대로 Allocation은 SecuritiesFirm/Ipo와 동일한 글로벌
  기준정보라 계정 소유 필터를 적용하지 않는다.
- `controller/allocation/dto/AllocationRegisterRequest.java`,
  `AllocationResponse.java` (spec 응답 필드 그대로: id, subscriptionId,
  allocatedQuantity, allocatedAt, allocationMethod, allocationPrice, note,
  status, deletedAt, createdAt, updatedAt)
- `controller/allocation/exception/AllocationValidationException.java`,
  `AllocationConflictException.java`, `AllocationNotFoundException.java`,
  `AllocationExceptionHandler.java` (400/409/404 매핑,
  SubscriptionExceptionHandler와 동일 패턴이나 `FORBIDDEN` 핸들러는 없음 —
  소유권 검증을 하지 않으므로 403 자체가 발생하지 않음)

**검증 순서 설계 노트**: AC-002(필수값 누락)는 Subscription 조회 이전에
판별 가능하지만, AC-003(배정수량이 청약수량 초과)은 `subscription.quantity`
값이 필요하므로 Subscription 존재 확인(AC-004, 404) **이후**에 수행하도록
`register()`를 구성했다. 순서: 필수값 검증(400) → Subscription 조회(404) →
수량 범위 검증(400, 0 이상 & 청약수량 이하) → ACTIVE 중복 검사(409) → 저장.

**테스트**: `AllocationControllerTest.java` 신규 6개
(등록성공201/AC-001, 필수값누락400/AC-002, 배정수량값오류400/AC-003,
subscriptionId미존재404/AC-004, ACTIVE중복409/AC-005, CANCELLED후재등록
허용201/AC-006). SubscriptionControllerTest 패턴을 따르되, 소유권 검증이
없으므로 "다른 계정 소유" 시나리오(403) 테스트는 작성하지 않았다 — 투자자/
증권계좌/IPO/청약 등록은 모두 테스트 사전 준비 헬퍼로만 사용.

**검증**: `cd backend && DB_PASSWORD=vibe_local_password ./gradlew test
--rerun` → BUILD SUCCESSFUL, 226 tests completed, 0 failed (기존 220개 +
신규 6개).

**남은 작업**: SF-3(목록/상세 조회)부터 SF-5(취소)까지 순차 구현 필요.
커밋/PR/머지는 Master가 진행.

## 2026-09-22 — SF-3 구현 완료 (배정 목록/상세 조회 API, GET /allocations, GET /allocations/{id})

`docs/breakdown/allocation-management.md`의 SF-3을 구현했다. 브랜치
`winter0123/allocation-query` (backend-3 워크스페이스, origin/main 기준
생성, SF-1~2 포함). AC-007~011 커버.

**산출물**:
- `controller/allocation/AllocationController.java` — `GET /allocations`,
  `GET /allocations/{id}` 추가. 두 메서드 모두 한글 주석으로 동작 명시
  (AGENTS.md 15장 규칙: 클래스 주석은 리소스 전체 요약, 메서드 주석은
  해당 엔드포인트 하나의 동작).
- `service/allocation/AllocationService.java` — `list(Long subscriptionId,
  Long investorId)`, `getById(Long id)` 추가. `list()`는 파라미터에 따라
  전체/청약별/투자자별 분기, `getById()`는 `findByIdAndDeletedAtIsNull`로
  조회 후 없으면 `AllocationNotFoundException`(404).
- `repository/allocation/AllocationRepository.java` —
  `findBySubscriptionIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long)` (Spring
  Data 메서드명), `findByInvestorId(Long)` (JPQL 3단계 JOIN:
  Allocation → Subscription → Investor, `deletedAt IS NULL` 필터 적용).

**핵심 구현 포인트**:
- 투자자별 조회(`investorId` 필터)는 breakdown 문서에 명시된 대로
  Allocation→Subscription→Investor 3단계 JOIN이 필요했다. JPQL에서
  `JOIN Subscription s ON a.subscriptionId = s.id JOIN Investor i ON
  s.investorId = i.id`로 구현. 엔티티 간 관계는 FK로만 맺어져 있고
  JPA 연관관계(@ManyToOne 등)는 정의하지 않았으므로 직접 ID로 JOIN.
- `deletedAt IS NULL` 조건을 WHERE 절에 포함해 CANCELLED 기록이
  목록/상세에서 제외되도록 보장(AC-011, AC-018 일관성).
- Controller의 `list()`는 `ResponseEntity<List<AllocationResponse>>`를
  직접 반환(래퍼 객체 없이 배열 JSON), `getById()`는 단일 객체 반환.

**테스트**: `AllocationControllerTest.java`에 5개 신규
- `listAllocationsReturnsAllActiveAllocations()` — 전체 조회 + CANCELLED
  제외 + createdAt 내림차순 정렬 (AC-007, AC-018)
- `listAllocationsWithSubscriptionIdFilterReturnsFilteredList()` —
  `subscriptionId` 필터 동작 (AC-008)
- `listAllocationsWithInvestorIdFilterReturnsFilteredList()` —
  `investorId` 필터 동작, 3단계 JOIN 검증 (AC-009)
- `getAllocationByIdReturnsAllocation()` — 상세 조회 성공 (AC-010)
- `getAllocationByIdNotFoundReturns404()` — 미존재 ID 404 (AC-011)
테스트 헬퍼 `createSubscriptionForInvestor`, `readListBody` 추가.

**검증**: `cd backend && DB_PASSWORD=vibe_local_password ./gradlew test
--rerun` → BUILD SUCCESSFUL, 231 tests completed, 0 failed (기존 226개 +
신규 5개).

**남은 작업**: SF-4(수정 API, PATCH /allocations/{id}) → SF-5(취소/논리
삭제, DELETE /allocations/{id}) 순차 구현 필요. 커밋/PR/머지는 Master가
진행.

## 2026-09-22 — SF-4 구현 완료 (배정 수정 API, PATCH /allocations/{id})

`docs/breakdown/allocation-management.md`의 SF-4를 구현했다. 브랜치
`winter0123/allocation-update` (backend-3 워크스페이스, origin/main 기준
생성, SF-1~3 포함). AC-012~015 커버.

**산출물**:
- `controller/allocation/dto/AllocationUpdateRequest.java` — 부분 수정을 위한
  DTO (모든 필드 Optional, SubscriptionUpdateRequest 패턴과 동일).
- `service/allocation/AllocationService.java` — `update(Long id,
  AllocationUpdateRequest request)` 추가. SubscriptionService.update() /
  IpoService.update()와 동일한 부분 수정 패턴:
  1) `findByIdAndDeletedAtIsNull`로 기존 기록 조회(없으면 404)
  2) 제공된 필드만 검증(`allocatedQuantity < 0` 시 400)
  3) `subscriptionId`가 새로 제공된 경우에만 Subscription 존재 재검증(404)
  4) `subscriptionId` 변경 시와 변경 없으나 `allocatedQuantity` 변경 시
     모두 기존/새 Subscription의 `quantity`와 비교해 초과 시 400
  4) 제공된 필드만 setter 호출해 갱신, `updatedAt` 갱신
  5) 저장 후 `AllocationResponse` 반환, `DataIntegrityViolationException`
     catch 시 409 변환
- `controller/allocation/AllocationController.java` — `PATCH
  /allocations/{id}` 추가. 한글 클래스/메서드 주석 포함(AGENTS.md 15장
  규칙: 클래스 주석은 리소스 전체 요약, 메서드 주석은 해당 엔드포인트
  하나의 동작).

**특이사항 — 매도(Sale) 연결 시 수량 축소 제한 (AC-015)**:
breakdown 문서 명시대로 Epic 7 구현 시점에는 Sale 테이블이 없으므로,
이 검증 로직은 현재 구현하지 않고 **확장 지점(주석 + TODO)**으로만 남겨
둔다. `work-log`에 "Epic 8 구현 시 추가 예정"을 명시했고, Service
시그니처를 나중에 `SaleService`를 주입받아 확장할 수 있도록 설계했다.
현재 단계에서는 allocationId 기반 Subscription의 `quantity` 비교 검증만
수행한다.

**테스트**: `AllocationControllerTest.java`에 6개 신규
- `updateAllocationWithPartialFieldsReturns200AndUpdatedAllocation()` —
  제공된 필드만 갱신, 미제공 필드는 기존값 유지 (AC-012)
- `updateAllocationWithNewSubscriptionIdReturns200AndValidatesQuantity()` —
  subscriptionId 변경 시 존재 검증 + 수량 검증 (AC-012, AC-013)
- `updateAllocationWithUnknownSubscriptionReturns404()` — 미존재
  subscriptionId 404 (AC-014)
- `updateAllocationWithInvalidQuantityReturns400()` — allocatedQuantity
  < 0 또는 청약수량 초과 시 400, subscriptionId 변경 시에도 검증 (AC-013)
- `updateAllocationNotFoundReturns404()` — 미존재 배정 ID 404 (AC-014)
- `updateCancelledAllocationReturns404()` — CANCELLED(deletedAt set)
  기록은 `findByIdAndDeletedAtIsNull`로 조회 안 되어 404 (일관성 검증)

**검증**: `cd backend && DB_PASSWORD=vibe_local_password ./gradlew test
--rerun` → BUILD SUCCESSFUL, 237 tests completed, 0 failed (기존 231개 +
신규 6개).

**남은 작업**: SF-5(취소/논리 삭제, DELETE /allocations/{id}) 구현 필요.
커밋/PR/머지는 Master가 진행.

## 2026-09-22 — SF-5 구현 완료 (배정 취소 API, DELETE /allocations/{id})

`docs/breakdown/allocation-management.md`의 SF-5를 구현했다. 브랜치
`winter0123/allocation-cancel` (backend-3 워크스페이스, origin/main 기준
생성, SF-1~4 포함). AC-016~018 커버.

**산출물**:
- `service/allocation/AllocationService.java` — `cancel(Long id)` 추가.
  SubscriptionService.cancel() / IpoService.deactivate()와 동일한 패턴:
  1) `findByIdAndDeletedAtIsNull`로 기존 기록 조회(없거나 이미 CANCELLED면
     404 반환, 재취소 자연 배제)
  2) `status = "CANCELLED"`, `deletedAt = now`, `updatedAt = now` 설정 후 저장
  3) 반환값 없음(void) — Controller에서 204 No Content로 매핑
- `controller/allocation/AllocationController.java` — `DELETE /allocations/{id}`
  추가. 한글 클래스/메서드 주석 포함(AGENTS.md 15장 규칙).

**핵심 구현 포인트**:
- SubscriptionService.cancel()과 로직을 동일하게 유지: 물리 삭제는
  수행하지 않으며 논리 삭제(CANCELLED + deletedAt)만 수행.
- 이미 CANCELLED된 기록에 대한 재요청은 `findByIdAndDeletedAtIsNull` 필터로
  조회되지 않아 자연스럽게 404 반환(AC-017, AC-018 일관성).
- `deletedAt IS NULL` 필터가 모든 조회 쿼리에 포함되어 있어
  CANCELLED 기록은 목록/상세에서 자동 제외(AC-018 보장).

**테스트**: `AllocationControllerTest.java`에 4개 신규
- `cancelAllocationReturns204AndMarksCancelled()` — 취소 성공 204,
  status=CANCELLED+deletedAt 설정 확인 (AC-016)
- `cancelAllocationNotFoundReturns404()` — 미존재 배정 ID 404 (AC-017)
- `cancelledAllocationExcludedFromList()` — CANCELLED 기록이
  GET /allocations 목록에서 제외됨 (AC-018)
- `cancelAlreadyCancelledAllocationReturns404()` — 이미 취소된 배정
  재취소 시 404 (일관성/자연 배제 검증)

**검증**: `cd backend && DB_PASSWORD=vibe_local_password ./gradlew test
--rerun` → BUILD SUCCESSFUL, 241 tests completed, 0 failed (기존 237개 +
신규 4개). **Epic 7(배정 결과 관리) 전체 SF-1~5 완료.**

커밋/PR/머지는 Master가 진행.
