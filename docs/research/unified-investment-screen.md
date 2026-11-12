# 청약·매도 단일 관리 화면 조사 (unified-investment-screen)

> 목적: "청약 버튼 아예 없앴냐... 청약 화면이랑 매도 화면 다 합쳐 무조건 한 화면에서 다 하게, 매도만 여러 번 다른 금액으로 입력"이라는 사용자 지시에 대한 구현 전 조사. 2026-11-12, 브랜치 `winter0123/account-manage-edit-flow` 기준 코드 대조. 제품 코드/테스트 수정 금지(문서만).

## 1. 버튼 실태 (파일·행 근거)

- 상단 공통 등록 버튼은 없다: 상단 메뉴는 `MainLayout.tsx:109-112` "공모주 청약"(`/subscriptions`) 내비뿐. 공통 등록 버튼은 2026-11-11 `ipo-context-registration`에서 삭제됨(`docs/work-log/ipo-context-registration.md:5-6`, current-status 2026-11-11 "공통 등록 2버튼 제거/IPO 고정·문맥 없음 안내").
- 카드 기록유무 label 바뀜: `SubscriptionScheduleList.tsx:194-206`(청약 없으면 "청약 등록"+"매도 등록"), `207-219`(있으면 "청약 수정"+"매도 수정"). 단, 두 경우 모두 같은 register URL로 이동한다. `SubscriptionCard.tsx:50-66`도 동일.
- "청약 수정"은 신규 POST 페이지로 가는 링크: `/subscriptions/register?ipoId=N` → `SubscriptionRegisterPage.tsx`(h1 "청약 등록") → `SubscriptionRegisterForm.tsx:287-328` = 항상 POST(기존 기록 미조회). 저장 시 `uk_subscription_active_combo` + `SubscriptionService.checkDuplicate`로 409 "이미 등록된 청약입니다."(`SubscriptionService.java:64,283-296`; `V7__create_subscriptions_table.sql:33` `WHERE status='ACTIVE' AND deleted_at IS NULL`).
- 실제 PATCH/DELETE 편집은 투자내역 상세의 `RecordActions.tsx:37-111`(SubscriptionEditDialog/AllocationEditDialog/SaleEditDialog)에만 있다. 즉 "수정" 라벨 진입점이 두 계열로 갈라져 있고 카드/상세의 '수정'은 뒤에 신규 생성 폼이다.
- 그 외 등록 진입: `IpoDetailPage.tsx:92-115,157-173`(청약 CTA는 청약 예정·진행 중일 때만 "청약 등록하기", 매도 CTA는 항상), `DashboardUpcoming.tsx:37-39`, `DashboardTodo.tsx:61-65`, `InvestmentHistoryDetail.tsx:19-24`(배정 입력/매도 등록). 모두 신규 등록 폼 링크.
- 배정: `/allocations/register?subscriptionId=`(`AllocationRegisterPage`) — 배정만 따로 등록.
- 운영(production) 화면에서의 실제 표기는 로컬 코드/테스트 기준이며, 운영 화면 실측 단정은 하지 않는다(이번 조사에서 운영 조회는 하지 않음).

## 2. API 중복 409의 이유

- POST /subscriptions: `(investor_id, securities_account_id, ipo_id)` ACTIVE 중복 → 서비스 사전 검사(SubscriptionService.checkDuplicate) + 부분 유니크 인덱스(V7) → 409 `DUPLICATE_SUBSCRIPTION`. 즉 같은 계좌로 같은 공모주에 두 번 청약할 수 없다(정상 제약).
- POST /allocations: 동일 `subscription_id`의 ACTIVE 배정 이미 존재 → 409 `DUPLICATE_ALLOCATION`(AllocationService.checkDuplicate `:202-210`, V8 L37 `uk_allocation_active_subscription`). 청약 1건당 배정 1건(1:1).
- POST /sales: 유니크 제약 없음(V9: `allocation_id` N:1, 유니크 아닌 인덱스만). 누적 초과는 400 `ALLOCATION_EXCEEDED`(SaleService.java:74-82)일 뿐 중복 차단 아님.
- 결론: '수정'이 POST 폼으로 연결되면 저장마다 409(또는 배정 재생성 409)이 난다. 사용자 입장에선 "수정 누르고 저장 눌렀더니 이미 등록된 청약" 오류가 된다.

## 3. DB 1→1→N 제약(식별 단위)

- subscriptions: `(investor_id, securities_account_id, ipo_id)` partial unique → 식별 단위는 **투자자×IPO×실계좌**. IPO당 복수 실계좌 청약을 이미 허용하며 덮어쓰지 않는다.
- allocations: `subscription_id` ACTIVE partial unique → 청약 1건당 배정 1건. sales: `allocation_id` N:1 → 매도 여러 건 각각 다른 수량/단가/일자 허용.
- 매도 자동 연결: `SaleService.resolveAllocationId:179-241` — (investor, account, ipo)로 기존 ACTIVE 청약을 찾고 배정이 있으면 재사용, 없으면 `allocatedQuantity` 받아 생성. 매도부터 사후 등록하는 기존 지원이 코드로 들어 있다.
- 잔여 수량: `GET /investment-history/{id}`가 `remainingQuantity = allocatedQuantity − Σ ACTIVE sale.quantity`를 반환(`InvestmentHistoryAssembler.java:78-83`, `ui/src/entities/subscription/types.ts:51-79`).

## 4. 투자내역(이력) 액션과 캡처 경로

- 이력 상세의 '수정'은 실제로 PATCH다: `InvestmentHistoryDetail.tsx:77-153`의 `RecordActions`(수정/삭제 확인) → `SubscriptionEditDialog`(PATCH /subscriptions/{id}), `AllocationEditDialog`(PATCH /allocations/{id}), `SaleEditDialog`(PATCH /sales/{id}).
- 캡처 자동입력은 매도 등록 전용: `useCaptureFill`/`CaptureImport` — 공모주 문맥이 확정되지 않으면 적용 0회, 1건 매칭이어도 다른 계좌면 연결을 바꾸지 않고 값만 채운다(2026-11-11 보완 동작).
- 일정→상세→등록의 기존 해시/필터: 목록 `SubscriptionListPage`의 `from`/`to`/`page` URL 파라미터, 일정 `IpoCalendar`의 `/ipos/:ipoId` 링크 — 통합 화면에서도 이 원 경로를 보존해야 한다.

## 5. 제약·미확인

- 운영(production) 미커밋 계좌관리 변경(`securitiesaccountmanagement` 등 11파일)은 이 작업 범위가 아니며 읽기만 했다. 단일 worktree라 구현 시 병렬 편집 금지 — 문서만 산출한다.
- 금액 표시/계산 정책(realizedProfit vs finalProfit, 공모주수수료 DEFAULT_IPO_FEE=2000, 매도 있는 경우 0원 기본 `useSaleForm.ts:397-398`)은 기존 UI 정책을 사실로 기록했을 뿐 새 금융 정책을 만들지 않는다.
- 테스트/lint/build는 이번 계획에서 실행하지 않는다(NOT_RUN). 기존 세션의 PASS 값은 새 PASS로 재사용하지 않는다.
- `docs/daily-log/2026_11_12.md`는 이 작업 범위 밖 — 읽지도 수정하지도 않았다.

## 6. Master 보정 확인 (2026-11-12)

- 이력 요약(`InvestmentHistoryItem`)에는 `depositAmount`/`memo`가 없고(ui `entities/subscription/types.ts:51-66`), `saleSummary`에는 `price`가 없다(`:40-49`). 편집 원본은 `GET /subscriptions/{id}`(quantity·depositAmount·subscribedAt·memo)·`GET /allocations/{id}`·`GET /sales/{id}`(price 등)에서 읽고, 매도 단가는 amount÷quantity 추정이 아니라 원본 `price`를 쓴다.
- 연결 id 탐색(최종): 이력 조회는 서버가 `deletedAt IS NULL`만 반환(구독 `cancel()`은 deletedAt 설정)하고 조합 (investorId+ipoId+securitiesAccountId)는 ACTIVE 부분 유니크(V7)라, **정확 필터 size:1 + `subscriptionStatus === 'ACTIVE'` 검증으로 충분하다.** 앞서 기록한 "CANCELLED 후 재등록 조합에서 size:1이 활성 건을 놓칠 수 있다"는 가정은 근거 부족으로 취소 — 비활성 유입이 발견될 때만 전건 검토로 전환(API/DB 변경 없음).
- 삭제 규칙: 현 논리 취소(status=CANCELLED+deletedAt)와 자식→부모 순서(매도→배정→청약, `HAS_CHILD_RECORDS`) 유지.

## 7. Master 최종 추가 확인 (2026-11-12, 사용자 요청 반영)

- 사후 등록 자동 생성 청약의 실제 값(`AllocationService.register`, AllocationService.java:79-91): quantity=배정수량, depositAmount=0, subscribedAt=생성 시각(`LocalDateTime.now(clock)`), memo='매도/배정 등록에서 자동 생성된 청약 연결 기록'. 이 값들은 사용자의 실제 청약 사실이 아니며 청약기간 밖일 수 있다 — 화면은 memo 마커로 안내, 사용자는 나중에 실제 값으로 수정 가능(새 청약은 실제 기간 버튼). 기간 외 기존 subscribedAt은 보존, 인라인 dirty 입력은 미덮어쓰기, 사용자 데이터 변경·일괄 보정 없음.
- `Sale.updateDetails`는 null=무변경(각 필드 null이면 skip, Sale.java). `SaleUpdateRequest`는 price/amount/fee/tax/otherCost를 null 허용. `price`는 컬럼 nullable(Sale.java). 편집 원본 조회 시 price가 null이면 단가 미입력으로 원본 금액 보존, amount÷quantity 추정·저장 금지. PATCH는 변경 필드만 포함(fee/tax/otherCost를 무심코 0으로 덮어쓰지 않음).
- 응답 유실 후 어떤 요청이 성공했는지 100% 식별 불가(동일 일자·수량·단가의 정상 분할 매도 때문) — 재조회+목록/잔여 표시로 사용자가 수동 판단. 영구 중복 방지(idempotency 키)는 현 API로 미보장.
