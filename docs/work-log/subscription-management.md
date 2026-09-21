# 작업 로그: 청약 기록 (subscription-management, Epic 6)

## 2026-09-21 — Master 결정 사항 (Phase 0 spec 작성 전 스코프 정정)

`docs/research/subscription-management.md`가 실시간 청약 접수·배정 처리 시스템을
전제로 조사되어 있었다(상태 전이 머신, 증거금 납입/환불 계산 로직, 동시 청약 요청
경쟁 조건에 대한 낙관적/비관적 락 등). 그러나 이 프로젝트는 **이미 증권사를 통해
완료된 청약·배정·매도 결과를 사용자가 사후에 수동으로 입력해 기록·조회하는
서비스**이며(work-plan-ipo-investment.md 로드맵 전제, 이전 Epic 14/15 조사 초안에서
동일한 스코프 오류를 Master가 정정한 바 있음), 실시간 주문 접수/체결 프로세스를
시스템 내에서 처리하지 않는다. 이에 따라 조사 문서의 "사람 결정 필요" 항목들을
아래와 같이 Master가 결정하고 spec 작업에 반영한다.

### D-S-001: Subscription은 배정 상태를 포함하지 않는다
로드맵상 청약(Epic 6)과 배정 결과(Epic 7)는 별도 Epic으로 분리되어 있다.
Subscription 엔티티는 "청약했다"는 사실만 기록하며, 배정 관련 필드/상태
(배정대기/배정완료)는 Epic 7의 별도 Allocation 엔티티(Subscription을 참조하는
N:1 또는 1:1 관계)에서 다룬다.

### D-S-002: 증거금/환불 금액 계산 로직은 Subscription에 두지 않는다
환불 금액은 배정 결과(몇 주가 배정되었는지)에 의존하는 값이므로 계산 로직은
Allocation(Epic 7)에 속한다. Subscription은 사용자가 입력한 **증거금 납입액
(사실 기록)** 만 필드로 가지며, 환불액 계산/자동화 로직은 갖지 않는다.

### D-S-003: 상태값은 ACTIVE/CANCELLED만 사용한다 (복잡한 상태 머신 불채택)
이미 완료된 사실을 기록하는 도메인이므로 "청약대기→청약완료→배정대기→배정완료"
같은 실시간 워크플로 상태 머신은 불필요하다. SecuritiesFirm/IPO와 동일한
논리 삭제 패턴(ACTIVE/CANCELLED, 물리 삭제 금지)을 따른다. CANCELLED는
"사용자가 잘못 입력해 취소한 기록"을 의미하며, 실시간 청약 취소 프로세스가
아니다.

### D-S-004: 중복 방지는 (investor_id, securities_account_id, ipo_id) 활성 상태 부분 유니크
동일 투자자·계좌·IPO 조합의 ACTIVE 청약 기록 중복을 DB 레벨 부분 유니크
인덱스로 방지한다(SecuritiesFirm의 (name,status) 패턴과 동일 원리). 이는
사용자의 실수(같은 청약을 중복 입력)를 막기 위함이며, 여러 사용자가
동시에 같은 자원을 놓고 경쟁하는 실시간 상황을 가정하지 않는다(각자 자신의
기록만 입력하는 개인용 서비스).

### D-S-005: 수정 가능 범위 — 청약 마감 등 시점 제약 없이 전체 필드 수정 허용
사후 기록 서비스이므로 "청약 마감 전까지만 수정 가능" 같은 실시간 제약은
적용하지 않는다. 사용자는 자신이 입력한 청약 기록의 모든 필드(수량, 증거금,
일시, 메모)를 자유롭게 수정할 수 있다. 단, 이미 배정 기록(Allocation)이
연결된 경우 청약 수량을 배정 수량 미만으로 줄이는 수정은 참조 무결성
위반으로 차단한다(세부 검증 로직은 Epic 7 구현 시 함께 확정).

### D-S-006: 동시성 제어 — 낙관적/비관적 락 등 특수 메커니즘 불필요
단일 사용자가 자신의 기록을 순차적으로 입력하는 개인용 서비스이므로,
DB 유니크 제약(D-S-004) + 표준 트랜잭션 경계로 충분하다. 별도의 분산 락,
SELECT FOR UPDATE, 낙관적 버전 관리는 도입하지 않는다(SecuritiesAccount의
기본계좌 지정처럼 여러 레코드를 원자적으로 함께 바꿔야 하는 특수 케이스가
Subscription에는 없음).

이 결정들은 `docs/spec/subscription-management.md` / `docs/architecture/subscription-management.md`
작성 태스크 지시에 반영되었다.

---

## 2026-09-21 — SF-1 완료 (Subscription 엔티티/Repository/마이그레이션)

> 워크스페이스: backend-3, 브랜치: `winter0123/subscription-management`

### 구현 내용 (Test-First: RED → GREEN)
- **RED**: `SubscriptionRepositoryTest`를 먼저 작성하고 실행 — `Subscription`/`SubscriptionRepository`가 없어 컴파일 실패(20 errors)로 RED 확인.
- **패키지 구조**: `com.vibeaiteam.subscriptionmanagement` 루트 패키지 (architecture.md 의 `domain.model.subscription`/`repository.subscription` 패키지 소유)
  - `domain/model/subscription/Subscription.java` — JPA 엔티티 (id Long IDENTITY, investorId/securitiesAccountId/ipoId/quantity/depositAmount/subscribedAt 필수, memo 선택, status 기본값 ACTIVE, deletedAt/createdAt/updatedAt). 기존 `Ipo` 엔티티의 Lombok `@Builder`/`@Getter`/`@Setter`/`@NoArgsConstructor`/`@AllArgsConstructor` 패턴 동일 적용. memo는 마이그레이션의 `TEXT` 컬럼과 매핑.
  - `repository/subscription/SubscriptionRepository.java` — 다음 SF(등록 API)에서 즉시 쓸 조회 메서드 5종:
    1. `findByIdAndDeletedAtIsNull(Long)` — 상세 조회(논리 삭제 필터)
    2. `findAllByDeletedAtIsNullOrderByCreatedAtDesc()` — 목록 조회(전체)
    3. `findAllByInvestorIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long)` — 투자자별 목록(AC-008)
    4. `findAllBySecuritiesAccountIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long)` — 계좌별 목록(AC-009)
    5. `findByInvestorIdAndSecuritiesAccountIdAndIpoIdAndStatusAndDeletedAtIsNull(Long, Long, Long, String)` — 중복 검사용(AC-005/006, ACTIVE 기준)
- **Flyway 마이그레이션**: `V7__create_subscriptions_table.sql` (기존 V6 다음 버전, PostgreSQL 전용 문법)
  - PK 자동증가 `GENERATED ALWAYS AS IDENTITY`, `memo TEXT NULL`, `status VARCHAR(10) DEFAULT 'ACTIVE'`
  - **부분 유니크 인덱스**: `uk_subscription_active_combo ON subscriptions (investor_id, securities_account_id, ipo_id) WHERE status = 'ACTIVE' AND deleted_at IS NULL` — CREATE TABLE 밖 별도 `CREATE UNIQUE INDEX` (AC-005: ACTIVE 중복만 차단, AC-006: CANCELLED 재등록 허용). Epic 5 V6 수정 사례(D-IPO-001)를 참고해 최초부터 DB 제약과 Service 계층 중복 검사 규칙을 일치시켜 `deleted_at IS NULL` 조건까지 포함.
  - 조회 최적화 인덱스: `idx_subscription_investor_id`, `idx_subscription_securities_account_id`, `idx_subscription_status`
  - **외래 키 제약 미적용**: investor_id/securities_account_id/ipo_id는 BIGINT 컬럼으로만 둠(다른 Epic 참조이며 참조 무결성은 다음 SF의 애플리케이션 레벨 존재 검증으로 처리 — dispatch 지시)
  - **한글 COMMENT 필수화(AGENTS 15장 신규 규칙) 적용**: `COMMENT ON TABLE subscriptions IS '...'` + 모든 컬럼에 `COMMENT ON COLUMN`, 그리고 CREATE TABLE 위 테이블 한줄 주석 + 각 컬럼 라인 끝 `--` 주석 + 각 인덱스 용도 주석 모두 반영.
- **테스트**: `SubscriptionRepositoryTest.java` 신규 8개 (IpoRepositoryTest 패턴 그대로: `@DataJpaTest` + `@AutoConfigureTestDatabase(NONE)` + `@ActiveProfiles("test")` + `@ContextConfiguration(AccountAuthenticationApplication.class)`)

### 테스트 케이스 (8개)
1. `saveSubscriptionFindByIdReturnsSubscriptionWithActiveStatus` — 저장/조회 정상, status=ACTIVE, memo 등 필드 확인 (AC-001)
2. `findAllByDeletedAtIsNullOrderByCreatedAtDescReturnsOnlyNonDeletedSubscriptions` — 취소(CANCELLED+deletedAt) 제외, createdAt 내림차순 (AC-007/018)
3. `findByIdAndDeletedAtIsNullExcludesCancelledSubscription` — 취소된 기록 상세 조회 제외 (AC-016/017)
4. `findAllByInvestorIdAndDeletedAtIsNullFiltersByInvestor` — 투자자별 필터 (AC-008)
5. `findAllBySecuritiesAccountIdAndDeletedAtIsNullFiltersByAccount` — 계좌별 필터 (AC-009)
6. `findByInvestorAccountIpoAndStatusReturnsActiveDuplicateRecord` — Service 중복 검사용 ACTIVE 탐지 메서드 검증 (AC-005)
7. `saveDuplicateActiveComboThrowsDataIntegrityViolationException` — 동일 조합 ACTIVE 중복 저장 시 `DataIntegrityViolationException` (AC-005, 부분 유니크 인덱스 동작)
8. `saveSameActiveComboAfterCancelAllowed` — CANCELLED 후 동일 조합 재등록 허용 (AC-006, 부분 유니크 인덱스가 ACTIVE만 제약)

### 테스트 실행 결과
```bash
DB_PASSWORD=vibe_local_password ./gradlew test --rerun
```
- **전체 테스트 스위트 192개 통과** (기존 184개 + 신규 8개, skipped 0 / failures 0 / errors 0)
- 테스트 DB(vibe_ai_team@5433)에 Flyway V7 적용 확인(부분 유니크 인덱스 동작을 테스트 7·8로 검증, `ddl-auto: validate` 통과)

### 다음 SF 예고 — SF-2: 청약 등록 API (POST /subscriptions)
- `controller/subscription/SubscriptionController`, `service/subscription/SubscriptionService` 생성
- 이번 SF에서 만들어 둔 `SubscriptionRepository` 조회 메서드 재사용: `findByInvestorIdAndSecuritiesAccountIdAndIpoIdAndStatusAndDeletedAtIsNull`(중복 검사) 및 `findByIdAndDeletedAtIsNull`(상세/수정/취소 대상 조회)
- 참조 무결성(Investor/SecuritiesAccount/IPO 존재 여부) 애플리케이션 레벨 검증, 필수값/수량/증거금 검증(400), 중복 시 409(AC-004/005), 성공 시 201(AC-001)
- 이후 SF-3(목록/상세), SF-4(수정), SF-5(취소) 순차 진행
- 참고: build.gradle.kts에 checkstyle 플러그인이 아직 없음(AGENTS 15장 지시) — 기존 코드 스타일(4칸 인덴트 등)을 수동 준수했으며, checkstyle 도입 여부는 Master 판단 사항으로 남겨 둠

---

## 2026-09-21 — SF-3 완료 (청약 목록/상세 조회 API — GET /subscriptions, GET /subscriptions/{id})

> 워크스페이스: backend-3, 브랜치: `winter0123/subscription-query`

### 사전 확인 (중요 — 브랜치 구성 이슈)
- 분배 시점에 **SF-2(등록 API) 코드가 `subscription-query` 브랜치에 없었다.** SF-2는 `winter0123/subscription-register` 브랜치(커밋 c29ec76)에만 존재하고 별도 PR로 아직 main에 머지되지 않은 상태다.
- SF-3는 SF-2의 `SubscriptionService`/`SubscriptionController`에 메서드를 추가하는 구조이므로, **register 브랜치에서 SF-2 소스 파일 10개(서비스/컨트롤러/DTO 2종/예외 5종/컨트롤러 테스트)를 `git show`로 바이트 단위 동일하게 작업 트리에 복제**한 뒤 그 위에 SF-3를 구현했다. git commit/push는 하지 않았다.
- **Master 참고(머지 시)**: 이 브랜치에는 SF-2 파일들이(테스트 포함) 다시 포함되어 있으므로, register 브랜치 머지와 충돌(add/add)할 수 있다. SF-2가 먼저 main에 머지된 뒤 이 브랜치의 변경분을 복제분(동일 내용) 제외 SF-3 추가분만 반영하도록 정리하는 것을 권장한다.

### 구현 내용 (Test-First: RED → GREEN)
- **RED**: `SubscriptionControllerTest`에 SF-3 테스트 5개를 먼저 추가해 실행 — GET 엔드포인트가 아직 없어 404/파싱 실패로 5개 전부 RED(12개 중 7개는 SF-2 등록 테스트로 GREEN).
- **`SubscriptionService`** (SF-2 register 이어서 동일 클래스에 메서드 추가):
  - `list(Long investorId, Long securitiesAccountId)` — investorId만 있으면 투자자 필터, securitiesAccountId만 있으면 계좌 필터, 둘 다 null이면 전체 조회. 둘 다 주어진 경우 investorId 우선 조회 후 그 결과 안에서 securitiesAccountId로 스트림 재필터링(신규 쿼리 메서드 추가 없음 — SF-1이 만들어 둔 `findAllByInvestorIdAndDeletedAtIsNullOrderByCreatedAtDesc`/`findAllBySecuritiesAccountIdAndDeletedAtIsNullOrderByCreatedAtDesc`/`findAllByDeletedAtIsNullOrderByCreatedAtDesc` 재사용). `@Transactional(readOnly = true)` + `List<SubscriptionResponse>` 변환 (`IpoService.list` 패턴).
  - `getById(Long id)` — `findByIdAndDeletedAtIsNull` → 없으면 `SubscriptionNotFoundException`(404, `IpoService.getById` 패턴).
  - **조회 시 계좌 소유권 검증은 수행하지 않음**: spec(`docs/spec/subscription-management.md` 2/3절, AC-007~011)과 architecture(목록/상세 흐름, 42~52행) 어디에도 조회 시 소유권 필터가 명시되어 있지 않고, 등록(SF-2) 시에만 소유권을 이미 강제하므로 중복 검증하지 않는다. 아키텍처 29행 "SecuritiesAccount를 통해 간접 적용"도 등록 흐름 전제다.
- **`SubscriptionController`**:
  - `@GetMapping list(...)` — `@RequestParam(required = false) Long investorId, securitiesAccountId`, 200 OK + 목록.
  - `@GetMapping("/{id}") getById(...)` — 200 OK, 없으면 404.
  - 두 엔드포인트 모두 AGENTS 15장 규칙대로 **메서드별 한글 주석** 작성(클래스 주석과 별개로 각 메서드 위에).

### 테스트 케이스 (신규 5개, `SubscriptionControllerTest`에 추가)
1. `listSubscriptionsReturnsOnlyActiveExcludingCancelled` — 등록 후 취소 → 같은 조합 재등록 → 목록에 ACTIVE 1건만 포함, CANCELLED 제외 (AC-007, AC-018)
2. `listSubscriptionsFiltersByInvestorId` — 두 투자자 기록 중 investorId 필터로 해당 투자자 것만 (AC-008)
3. `listSubscriptionsFiltersBySecuritiesAccountId` — 같은 투자자의 두 계좌 기록 중 securitiesAccountId 필터로 해당 계좌 것만 (AC-009)
4. `getSubscriptionByIdReturnsDetail` — 등록 후 GET /subscriptions/{id} → 200 + 전체 필드 확인 (AC-010)
5. `getSubscriptionByIdWithUnknownIdReturns404` — 존재하지 않는 ID → 404 NOT_FOUND (AC-011)
- 테스트 헬퍼: 두 개의 서로 다른 IPO가 필요해 `createIpo()`를 `createIpo(companyName, ipoName, stockCode)` 오버로드로 분리(기존 호출부는 그대로 위임), 응답 배열 파싱용 `readList()` 추가.

### 테스트 실행 결과
```bash
DB_PASSWORD=vibe_local_password ./gradlew test --rerun
```
- **전체 테스트 스위트 204개 통과** (기존 199개 + 신규 5개, failures 0 / errors 0 / skipped 0)
- `SubscriptionControllerTest`: 12개 통과 (SF-2 등록 7 + SF-3 조회 5)

### 남긴 것 / 다음 SF 예고 — SF-4: 청약 수정 API (PATCH /subscriptions/{id})
- spec 4절 (AC-012~015): 제공된 필드만 수정, 유효성 오류 400, ACTIVE 중복 409(자기 자신 제외), 존재하지 않는 ID/이미 CANCELLED → 404
- `SubscriptionService.update(id, request)` + `SubscriptionController`에 `@PatchMapping("/{id}")` 추가 예정
- 엣지케이스 참고: "이미 CANCELLED된 기록을 다시 수정하는 요청은 404로 처리" — `findByIdAndDeletedAtIsNull`로 CANCELLED(삭제) 기록은 자연 배제됨
- 이후 SF-5: 청약 취소(DELETE, 논리 삭제)
