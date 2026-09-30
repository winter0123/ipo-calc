# 청약 비용 기록 — 세부 기능 분해 (cost-recording breakdown)

> **Epic**: Epic 9 (청약 비용 기록)
> **작성일**: 2026-09-21
> **API 계약 출처**: docs/spec/cost-recording.md, docs/architecture/cost-recording.md
> **의존 관계**: Epic 6 (subscription-management) 구현 완료 필요 — Cost는 Subscription을 FK로 참조 (N:1)

---

## 세부 기능(SF) 개요

총 **5개 SF**로 분해한다. 순차 의존 관계이며 각 SF는 PR 하나로 리뷰 가능한 크기다. Epic 7/8(allocation/sale)과 동일한 5단계 구조를 유지한다 — API 5개(POST/GET 목록/GET 상세/PATCH/DELETE), AC-001~AC-016, CSV·배치 같은 부가 진입점이 없어 그대로 적용 가능하다.

```
SF-1 (엔티티/Repository/마이그레이션) → SF-2 (등록 API, Subscription 존재+소유권 검증)
  → SF-3 (목록/상세 조회, JOIN 체인) → SF-4 (수정 API) → SF-5 (취소/논리 삭제)
```

---

## SF-1: Cost 엔티티, Repository, Flyway 마이그레이션

**범위**: `costrecording` 모듈의 DB 계층을 구축한다. Cost 엔티티, CostRepository 인터페이스, V9 Flyway 마이그레이션 SQL을 생성한다.

**커버하는 AC**: 이 SF 자체는 코드 레이어 설정이며, 이후 SF에서 AC-001~AC-016 전체가 커버한다.

**선행 SF**: 없음 (첫 번째 SF)

**산출물 파일 경로** (architecture.md "모듈 소유권", 루트 패키지 `com.vibeaiteam.costrecording` 참고 — ipo-catalog가 `com.vibeaiteam.ipocatalog`을 쓴 것과 동일한 명명 스타일):
- `domain/model/cost/Cost.java` — JPA 엔티티 (id BIGINT GENERATED ALWAYS AS IDENTITY, subscriptionId FK(N:1), costType 자유 텍스트, amount 0 이상, incurredAt, memo null 허용, status ACTIVE/CANCELLED, deletedAt, createdAt, updatedAt)
- `repository/cost/CostRepository.java` — Spring Data JPA 인터페이스 (deletedAt IS NULL 필터 기본 쿼리 — `findByIdAndDeletedAtIsNull`, `findAllByDeletedAtIsNullOrderByIncurredAtDescCreatedAtDesc`; 물리 삭제 메서드 노출 금지)
- `flyway/V9__create_costs_table.sql` — 테이블 생성 + FK 제약(`fk_cost_subscription`, ON DELETE RESTRICT) + 인덱스(idx_cost_subscription_id, idx_cost_status)

**비고**:
- **유니크 제약 없음** (D-C-002): 하나의 Subscription에 Cost가 여러 건 존재 가능(AC-005)하므로 partial unique index를 두지 않는다 — Epic 7 Allocation의 1:1 제약과 다른 점.
- **PostgreSQL 문법 준수** (AGENTS.md 15장): PK는 `GENERATED ALWAYS AS IDENTITY`, MySQL 문법 금지.

---

## SF-2: 비용 등록 API (POST /costs)

**범위**: 비용 등록 엔드포인트를 구현한다. Controller, Service, DTO를 포함한다. 핵심은 subscriptionId의 존재 검증(404)과 **계정 소유권 검증(403)** 이다.

**커버하는 AC**: AC-001 (등록 성공), AC-002 (필수값 누락 400), AC-003 (amount 0 미만 400), AC-004 (Subscription 미존재 404), AC-005 (동일 청약 다건 등록 성공)

**추가로 커버하는 엣지케이스**: memo null 허용, incurredAt 미래/과거 무관(사후 기록), status 필드에 허용되지 않은 값 400, memo 255자 초과 400 (architecture 리스크 4), 취소된 Subscription 참조 시 404 (아래 결정표 참고)

**선행 SF**: SF-1 (엔티티/Repository/Migration 필수)

**산출물 파일 경로**:
- `controller/cost/CostController.java` — `POST /costs`, 클래스/메서드 한글 주석 신설 (AGENTS.md 15장)
- `service/cost/CostService.java` — `register()` 메서드 (Subscription 존재+소유권 검증, amount 유효성 검증, Cost 생성 status=ACTIVE)
- `controller/cost/dto/CostRegisterRequest.java`, `CostResponse.java`
- `controller/cost/exception/CostValidationException.java`, `CostNotFoundException.java`, `CostForbiddenException.java`, `CostExceptionHandler.java`

**특이사항 — Subscription 참조 검증 (가장 중요, 결정표 참고)**:
- POST 요청의 `subscriptionId`가 실제 존재하는 **계정 소유의** Subscription을 참조하는지 Service 계층에서 반드시 확인한다.
- **계정 소유권 검증 여부**: **필요함 (403)**. Cost는 사용자가 직접 입력하는 개인 비용 기록이므로 계정 소유 데이터이며, architecture.md가 "사용자(Account) 소유 검증은 Subscription → SecuritiesAccount → Account 체인을 통해 간접적으로 적용된다"고 명시한다. 이는 Epic 7 Allocation("글로벌 기준정보")과 다른 판단이다 — 아래 "Subscription 참조 검증 관련 결정" 표에 근거를 상세히 기록했다.
- **검증 순서** (SubscriptionService.register 패턴 그대로):
  1. `findByIdAndDeletedAtIsNull(subscriptionId)`로 조회 → 없거나 취소(CANCELLED)되면 404
  2. Subscription → Investor 경유로 accountId 조회 → `securityUtils.getCurrentAccountId()`와 불일치하면 403 (SubscriptionForbiddenException과 동일 패턴의 CostForbiddenException)
- **(보안 점검 후 정정: 조회·수정·취소에도 403 소유권 검증 적용, 목록은 현재 계정 한정)** 당시 결정: 조회 시 소유권 검증은 하지 않는다 — SubscriptionService가 "조회 시 계정 소유권 검증은 수행하지 않는다(spec/architecture에 명시 없음)"고 한 것과 동일한 결정. 소유권 검증은 생성(레코드 소유권이 확정되는) 시점에만 강제한다.
- `subscriptionId`는 수정(PATCH) 요청 본문에 포함되지 않으므로 소유권 검증은 등록에서만 수행하고, 수정/취소/조회 시에는 재검증하지 않는다.
---

## SF-3: 비용 목록/상세 조회 API (GET /costs, GET /costs/{id})

**범위**: 비용 목록 조회와 상세 조회 엔드포인트를 구현한다. `deletedAt IS NULL` 필터, `subscriptionId`/`investorId`/`investorName` 쿼리 파라미터 필터링, Cost → Subscription → Investor JOIN 쿼리를 포함한다.

**커버하는 AC**: AC-006 (목록 조회), AC-007 (청약별 필터), AC-008 (투자자별 필터), AC-009 (상세 조회 성공), AC-010 (상세 조회 실패 404), AC-016 (취소된 비용 목록에서 제외)

**선행 SF**: SF-1 (필수)

**산출물 파일 경로**:
- `controller/cost/CostController.java` — `GET /costs`, `GET /costs/{id}` 추가
- `service/cost/CostService.java` — `list()`, `getById()` 메서드 추가
- `repository/cost/CostRepository.java` — `findBySubscriptionIdAndDeletedAtIsNull()`, 투자자별 필터 JPQL JOIN 쿼리 (`Cost JOIN Subscription s JOIN Investor i WHERE i.id = :investorId` / `i.name LIKE :investorName%`) 추가
- `controller/cost/exception/CostNotFoundException.java` (SF-2에서 생성하면 재사용)

**특이사항 — 투자자별 조회 JOIN 경로**:
- `investorId`/`investorName` 필터는 2단계 JOIN 체인(Cost → Subscription → Investor)을 사용한다 (architecture.md "투자자별/투자자명 조회" 설계). `subscriptionId` 필터는 Cost 직접 조회로 충분하다.
- INNER JOIN이므로 Subscription이 없는 Cost는 투자자 필터 적용 시 결과에서 자연스럽게 제외된다 (architecture.md 주의사항).
- 정렬은 `incurredAt DESC, createdAt DESC` (spec).
- CANCELLED(`deletedAt IS NOT NULL`) Cost는 목록/상세에서 항상 제외 (AC-016). Subscription이 CANCELLED여도 연결된 Cost 기록은 보존되어 조회 대상에 남는다 (spec엣지케이스 — 비용은 이미 발생한 사실).
- 3단계 JOIN(Epic 8 Sale: Sale→Allocation→Subscription→Investor)이 아니라 2단계 JOIN이므로 EXPLAIN 검증 정도만 수행하면 된다 (architecture 리스크로 명시된 3단계 성능 이슈에 해당하지 않음).

---

## SF-4: 비용 수정 API (PATCH /costs/{id})

**범위**: 비용 수정 엔드포인트를 구현한다. 제공된 필드만 부분 수정, 시점/필드 제약 없이 전체 허용(D-C-003), updatedAt 갱신을 포함한다.

**커버하는 AC**: AC-011 (수정 성공), AC-012 (유효성 오류 400), AC-013 (존재하지 않는 ID 404), 수정 대상이 CANCELLED 상태면 404 (spec API 상세 — AC 번호 없음)

**선행 SF**: SF-1, SF-2, SF-3 (필수)

**산출물 파일 경로**:
- `controller/cost/CostController.java` — `PATCH /costs/{id}` 추가
- `service/cost/CostService.java` — `update()` 메서드 추가 (부분 수정, CANCELLED 확인, updatedAt 갱신)
- `controller/cost/dto/CostUpdateRequest.java`

**특이사항**:
- PATCH 요청 본문에는 `subscriptionId`가 없다 — 비용의 잘못된 청약 연결을 바꾸는 것이 아니라 기록 정정만 허용하므로 소유권/존재 재검증이 필요 없다 (spec D-C-003 "전체 필드 수정 허용"은 costType/amount/incurredAt/memo 한정).
- 취소된 비용(CANCELLED)은 조회에서 노출되지 않으므로 수정 요청 시 `findByIdAndDeletedAtIsNull`로 조회해 404로 처리한다 (spec엣지케이스 — "이미 CANCELLED된 기록을 다시 수정하는 요청은 404").

---

## SF-5: 비용 취소 API (DELETE /costs/{id})

**범위**: 비용 논리 삭제(취소) 엔드포인트를 구현한다. status를 CANCELLED로 변경하고 deletedAt을 설정한다.

**커버하는 AC**: AC-014 (취소 성공 204), AC-015 (존재하지 않는 ID 404), AC-016 (취소된 비용은 목록에서 제외 — 조회 로직은 SF-3이 담당)

**선행 SF**: SF-1 (필수)

**산출물 파일 경로**:
- `controller/cost/CostController.java` — `DELETE /costs/{id}` 추가
- `service/cost/CostService.java` — `cancel()` 메서드 추가 (조회, CANCELLED 확인, status 변경, deletedAt 설정)

**비고**:
- 논리 삭제이므로 물리 삭제가 아니며, FK ON DELETE RESTRICT 제약과 무관하게 항상 성공한다 (D-C-004, architecture.md "논리 삭제와 FK 관계").
- 취소된 비용(CANCELLED)은 다시 ACTIVE로 되돌릴 수 없다 — 정정이 필요하면 새 비용을 등록한다 (spec엣지케이스).
- Subscription이 CANCELLED되어도 연결된 Cost 기록은 유지된다 — 비용은 이미 발생한 사실이므로 보존된다.

---

## Subscription 참조 검증 관련 결정 (Epic 7과 독립적으로 판단)

> spec의 AC/에러코드 목록에는 소유권 케이스가 없지만, architecture.md의 계정 소유 체인 명시와 실제 코드베이스 패턴(SubscriptionService.register의 403 검증)이 결정적 근거다. **Epic 7 Allocation("글로벌 기준정보")의 결정을 그대로 가정하지 않고 Cost의 spec/architecture를 직접 확인해 판단했다.**

| 항목 | 결정 | 근거 |
|------|------|------|
| Subscription 존재 검증 | **필수 (404)** | AC-004, architecture 등록 흐름 "Service가 Subscription 존재 여부 확인 (존재하지 않으면 404)" |
| 계정 소유권 검증 | **필수 (403)** | Cost는 **계정 소유 데이터**다 — architecture.md "사용자(Account) 소유 검증은 Subscription → SecuritiesAccount → Account 체인을 통해 간접적으로 적용된다". 글로벌 기준정보라고 명시한 Epic 7 Allocation과 다르고, spec 개요가 "사용자가 사후에 수동으로 입력·기록"하는 개인 비용 기록으로 정의한다. 실제 구현된 SubscriptionService.register도 계정 소유 데이터 등록 시 403(SubscriptionForbiddenException)으로 소유권을 강제한다 — Cost도 동일 패턴 적용. 만약 소유권 검증이 없으면 인증된 사용자가 순차 Long ID 추측으로 타 계정의 subscriptionId에 임의로 비용을 붙일 수 있고, 남의 청약 ID 존재 여부를 열거(enumeration)할 수 있다 |
| 참조 경로 단계 수 (등록 시) | **2단계** | Cost → Subscription → Investor(accountId 확인 위해 Investor 조회). subscriptionId는 Cost가 직접 FK로 참조하므로 검증 자체는 1단계 조회 + Investor 소유권 확인 1회 |
| `SecurityUtils.getCurrentAccountId()` 사용 여부 | **사용함 (등록 시에만)** | SubscriptionService/InvestorService/SecuritiesAccountService와 동일 패턴. 신규 클래스 CostForbiddenException(+ handler, 403) 추가 — spec 에러코드 목록에 FORBIDDEN이 없으므로 SF-2 작업지시에 추가를 명시한다 |
| 조회(목록/상세)/수정/취소 시 소유권 | **재검증 안 함** | SubscriptionService가 "등록 시에만 소유권을 강제하고 조회 시에는 검증하지 않는다"고 명시한 것과 동일 결정. PATCH는 subscriptionId가 없어 재검증 대상이 아님. (잔여 리스크: ID 추측으로 타 계정 Cost 열람/수정 가능 — 현 프로젝트가 조회 계층에서 이미 받아들인 relaxed 모델) |
| 취소된 Subscription 참조 시 | **404** | `findByIdAndDeletedAtIsNull` 조회 패턴 사용 (SubscriptionService가 IPO 참조 검증에 같은 패턴을 쓰는 것과 동일). 취소된 청약에 새 비용을 연결하는 것은 허용하지 않되, 기존 Cost 기록은 보존된다 (spec엣지케이스) |
| 조회 필터에서 Subscription 상태 | **무관하게 JOIN** | investorId 필터는 INNER JOIN으로 Cost.deletedAt IS NULL만 필터하고 Subscription 상태는 확인하지 않음 — CANCELLED 청약의 비용도 보존 원칙(사실 기록)에 따라 조회됨 |

---

## 의존성 요약

```
SF-1 (엔티티/Repository/마이그레이션)
  └→ SF-2 (등록 API) ── Subscription 존재 검증 (404) + 계정 소유권 검증 (403) ★ 가장 중요
  └→ SF-3 (목록/상세 조회) ── 2단계 JOIN (Cost→Subscription→Investor), investorName LIKE
  └→ SF-4 (수정 API) ── 부분 수정 (subscriptionId 불변), CANCELLED 404
  └→ SF-5 (취소/논리 삭제)
```

**가장 주의가 필요한 부분**: SF-2의 **Subscription 존재+계정 소유권 검증**이다. spec의 에러코드 목록에는 FORBIDDEN이 없어 architecture.md 승인 없이는 걸러질 위험이 있지만, 코드베이스에서 계정 소유 데이터(SubscriptionService.register)가 403으로 소유권을 강제해 온 패턴과 architecture.md의 계정 소유 체인 명시를 근거로 반드시 구현해야 한다. 다음으로는 SF-3의 `deletedAt IS NULL` 필터와 JOIN 체인이 누락되면 취소된 비용 노출/오필터링이 발생하므로, 모든 Repository 쿼리에 필터가 들어가는지 검증이 필요하다.

---

## 참고

- **문서 의존성**: `docs/spec/cost-recording.md`, `docs/architecture/cost-recording.md`
- **금지사항**: 코드/엔티티/DB 스키마 작성 금지 — 이 문서는 "무엇을 어떤 순서로 만들지"를 정하는 계획 문서
- **논리 삭제 필터 누락 방지**: 모든 Repository 쿼리에 `deletedAt IS NULL` 필터가 반드시 포함되는지 검증해야 함 (architecture 리스크 3)
- **물리 삭제 방지**: repository에 `delete()`/`deleteById()` 같은 물리 삭제 메서드를 노출하지 않음 (architecture 리스크 2)
- **memo 255자 상한**: 초과 시 Controller에서 400 처리 (architecture 리스크 4)
- **유니크 제약 없음**: 하나의 Subscription에 비용 다건 허용(AC-005) — DB 레벨 유니크 제약/인덱스를 두지 않음 (D-C-002)
- **Epic 10(Profit)과의 경계**: Cost 도메인에 수익 계산 로직을 두지 않음 (D-C-005) — 취득 비용 합계는 Epic 10 구현 시 CostRepository 읽기 전용 조회로 계산
- **매도 비용과의 경계**: 매도 시점 비용(fee/tax/otherCost)은 Epic 8 Sale 필드로 기록되므로 Cost에서 다시 설계하지 않음 (D-C-001)
