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
