# 청약·매도 통합 관리 화면 work-log (unified-investment-screen)

> append-only. 2026-11-12 계획(Phase 0) 산출. 브랜치 `winter0123/account-manage-edit-flow`.

## 2026-11-12 조사·계획 문서 5종 작성

- 브랜치: `winter0123/account-manage-edit-flow` (미커밋 계좌관리 변경 11파일 + docs/current-status.md·daily-log 보존, 건드리지 않음).
- 담당: planning depth1 무료 워커. 제품 코드/테스트/설정/DB/브랜치/git(commit·push·배포) 변경 금지 범위 유지. 추가 워커 생성 없음.
- 산출: `docs/research/unified-investment-screen.md`, `docs/spec/unified-investment-screen.md`, `docs/architecture/unified-investment-screen.md`, `docs/breakdown/unified-investment-screen.md`, 본 파일. 각 40~80줄 목표.

## 조사 근거(코드 대조, 2026-11-12)

- 상단 공통 등록 버튼 부재: `MainLayout.tsx:109-112`, 공통 버튼 삭제는 2026-11-11 ipo-context-registration 작업(`docs/work-log/ipo-context-registration.md:5-6`).
- 카드/상세 '수정' 라벨이 register(POST) URL을 가리킴: `SubscriptionScheduleList.tsx:207-219`, `SubscriptionCard.tsx:50-66`, `IpoDetailPage.tsx:92-115`.
- 실제 PATCH/DELETE 편집은 `RecordActions.tsx` + `*EditDialog.tsx`에만 존재. 투자내역 상세에서만 가능.
- 중복 409: `SubscriptionService.checkDuplicate:283-296` + V7 `uk_subscription_active_combo`, `AllocationService.checkDuplicate:202-210` + V8 `uk_allocation_active_subscription`. 매도는 유니크 없음(V9), 누적 상한 400 `ALLOCATION_EXCEEDED`(SaleService.java:74-82).
- DB 식별 단위: 투자자×IPO×실계좌(부분 유니크) — IPO당 복수 실계좌 이미 지원, 덮어쓰기 없음.
- 잔여수량: `GET /investment-history/{id}`의 `remainingQuantity`(`InvestmentHistoryAssembler.java:78-83`), 매도별 `fee`/`tax` 의미 확인(`toSalePayload`, SaleEditDialog).
- 공모주수수료 기본 2,000원, 기존 매도 있으면 0원 기본(`useSaleForm.ts:397-398`, `DEFAULT_IPO_FEE`).

## 핵심 권장

1. 통합 화면(가칭 `/ipos/:ipoId/manage`, h1 "청약·매도 관리")에서 공모주·투자자·증권계좌 컨텍스트 고정, 청약 1건·배정 1건·매도 N건을 섹션별 POST/PATCH로 관리. 이동 없음.
2. '수정'은 항상 기존 기록 불러오기(PATCH). 기존 register URL은 통합 화면 리다이렉트 + 문맥 없으면 목록 안내.
3. 일괄 저장/복합 API는 필수 아님 — 섹션 저장 + 기존 409/400 서버 제약으로 부분 실패 안전. 새 DB/통합 테이블/탭 분리/새 금융 정책 금지.
4. 매도부터 사후 등록하는 기존 자동연결(`SaleService.resolveAllocationId`)을 그대로 재사용.

## 실제 변경 필요 API·DB

- DB/스키마: 없음.
- API: 필수 변경 없음(POST/PATCH/DELETE subscriptions·allocations·sales + GET investment-history 재사용). 선택 복합 API는 제안하지 않음.

## 남은 질문(사용자 결정 필요, 새 gate 생성 안 함)

- Q1 경로/이름(`/ipos/:ipoId/manage`, "청약·매도 관리")
- Q2 배정 전 매도 진입 유지 여부(권장 유지)
- Q3 분할 매도 공모주수수료 기본(권장 현행 유지)
- Q4 계좌 변경 시 dirty 입력 처리(권장: dirty 유지+경고)
- Q5 통합 화면 InvestorGate 적용 방식
- Q6 `/allocations/register` 리다이렉트 시 subscriptionId query 실을지(권장: 예)

## 검증

- 코드↔문서 대조: 완료(위 근거). `git diff --check`는 아래 보고 참조.
- 테스트/lint/build/typecheck: NOT_RUN (이번 계획의 규칙). 기존 세션 PASS 값을 새 PASS로 재사용하지 않음.
- 제품 코드 변경: 0건. 구현 시작하지 않음.

## 2026-11-12 Master 보정 (문서 5종 최소 수정)

- 제품 코드/테스트 변경 없음. 문서만 보정.

## 보정 내역

- **spec**: 매도부터 사후 입력은 화면에서 가능하되 저장 시 서버가 (투자자, IPO, 계좌)로 기존 ACTIVE 청약을 찾거나 신규 청약·배정을 생성/재사용(`SaleService.resolveAllocationId`)함을 명시. "배정 없이 매도만 있는 상태 허용" 문구 삭제, "기록 없는 첫 진입 비활성" 문구를 "청약 먼저/매도 사후 두 방식 같은 화면 제공"으로 통일. AC-2/5/6/7/8/9/12 정정, AC-13(편집 원본 GET 3종·원본 price)·AC-14(이중 클릭/응답 유실)·AC-15(미저장/조회 에러/동시성/0 배정) 추가. AC-6/7은 같은 수량 허용(서버 `<` 조건)으로 정정.
- **architecture**: 조회 계약 정정(Item에 depositAmount/memo 없음·saleSummary에 price 없음) → 편집 원본은 GET `/subscriptions/{id}`·`/allocations/{id}`·`/sales/{id}`(UI `getSubscription`/`getAllocation`/`getSale` 구현됨). 연결 탐색은 `subscriptionStatus === 'ACTIVE'` 전 페이지 스캔(`getAllInvestmentHistory` size=100) — API/DB 변경 없음의 근거. FSD '또는' 해소: 페이지가 투자자/IPO/계좌/조회 문맥 단일 소유, features 폼은 문맥+onSaved/편집 모드 props 주입, 자체 navigate 제거·계좌 select 중복 금지·등록 성공 후 페이지 유지, 편집 Dialog의 단가/수량 검증 로직은 추출/참고(대량 복사 금지), URL query 강결합 복사 금지.
- **breakdown**: SF-2는 편집 원본 GET 3종·ACTIVE 전 페이지 스캔, SF-3는 저장 후 상세 재조회 완료 전 다음 추가 잠금·성공 시 폼 닫음/초기화·재추가 시 잔여·수수료 기본 0 재계산·unique 제약 금지·응답 유실 자동 재전송 금지·0 잔여 추가 불가(수정/삭제 유지)를 반영. Q1~Q6 필수 질문 표기를 권장 구현 선택(필수 질문 없음, 사용자 승인 사실 아님)으로 정리 — 경로 `/ipos/:ipoId/manage`+화면명 "청약·매도 관리", 사후 매도 유지, 수수료 현행 보존(사용자 수정 가능), 전환 시 미저장 경고(취소=유지/확인=재조회+초기화), 게이트는 관리 경로만 선별 scope 등록, subscriptionId 리다이렉트는 권한 확인 상세에서 IPO/계좌 매핑.
- **research**: Master 보정 확인 절 추가(요약 vs 원본 GET 3종, size=1 위험과 페이지 스캔, 삭제 논리 취소/부모→자식 순서 유지).

## 검증

- 문서↔실코드 대조: 완료(편집 원본 GET 3종·`subscriptionStatus`·size=1 문제·deletedAt/CANCELLED 동작·V7/V8/V9·`resolveAllocationId` 재확인). `git diff --check` PASS.
- 테스트/lint/build/typecheck: NOT_RUN(계획 단계). 기존 PASS 재사용 없음.
- 제품 코드 변경: 0건.

## 남은 결정
- 없음(필수 질문 없음). 구현 착수는 Master/사용자 지시 시에만.

## 2026-11-12 Master 최종 보정 (가설 취소 + 표기 2건 정정)

- 가설 취소: "size:1은 CANCELLED 후 재등록 조합에서 활성 건 누락 가능"은 근거 부족으로 철회 — `InvestmentHistoryQueryService.getHistory` → `SubscriptionQueryRepositoryImpl.findByInvestorAccountIdWithFilters`는 `deletedAt IS NULL` 조건이고 `Subscription.cancel()`은 deletedAt을 설정하며, 조합 ACTIVE 부분 유니크(V7)라 정확 필터 size:1은 활성 누락 원인이 아니다. 연결 탐색은 정확 필터(investorId+ipoId+securitiesAccountId)+size:1+ACTIVE 검증으로 최소화(비활성 유입 발견 시에만 전건 검토).
- 삭제 순서: "부모→자식(청약←배정←매도)" → **자식→부모(매도→배정→청약)** (`HAS_CHILD_RECORDS` — 매도 없어야 배정 취소, 배정 없어야 청약 취소라는 실제 강제 의미와 일치). architecture 도표의 `GET /records/{id}`는 실제 endpoint가 아니므로 `GET /subscriptions/{id}`·`/allocations/{id}`·`/sales/{id}`로 표기. "청약 저장 전 배정/매도 직접 저장은 연결 없음 → 불가, 사후 등록 자동 연결은 예외"로 명시. 본 절이 위 과거 "전 페이지 스캔"·"부모→자식" 표현을 대체한다. `git diff --check` PASS, 테스트 NOT_RUN.

## 2026-11-12 Master 추가 검토 보정 (사후 자동생성값·기존 단가·응답 유실 보호)

- spec: AC-9 확장(subscriptionId 리다이렉트 — 권한 확인된 상세(소유권 검증, 타인 403/404)에서 IPO/투자자/계좌 정확 매핑, 현재 선택 투자자와 다르면 글로벌 투자자 임의 전환 금지·타인 기록 미노출). AC-14 정정(응답 유실 후 어떤 요청이 성공했는지 100% 식별 불가 명시 — 동일일자·수량·단가 분할 매도 때문. 자동 재POST 금지, 재조회+목록/잔여 표시, 사용자 수동 결정, idempotency 키 필수화 금지). AC-16 신설(price nullable 원본 보존 — amount÷quantity 추정 금지, PATCH는 변경 필드만 포함, null·0 무심코 덮어쓰기 금지). AC-17 신설(사후 자동 생성 청약 값: quantity=배정수량, depositAmount=0, subscribedAt=생성 시각, memo='매도/배정 등록에서 자동 생성된 청약 연결 기록' — 실제 청약 사실 아님·기간 밖 가능. memo 마커로 안내, 나중 수정 가능, 기간 외 기존 subscribedAt 보존, 새 청약은 실제 기간 버튼, 인라인 dirty 미덮어쓰기, 사용자 데이터 변경 없음). 엣지 케이스: 사후 자동 생성 청약 memo 마커 안내 추가.
- architecture: 편집 원본 조회 블록에 price(nullable)·변경 필드만 PATCH(서버 null=무변경, Sale.updateDetails 실물 확인) 명시. 리스크: 응답 유실 100% 식별 불가 명시 + 사후 자동 생성 청약 실제 값/표시 의무 추가.
- breakdown: SF-1을 "준비(기존 진입점 유지) + 진입점 전환은 SF-2/3 완료·호환경로 검증 후 한 묶음 전환/배포"로 재구성(스켈레톤만 배포 시 기존 등록 진입점 먹통 방지). subscriptionId 리다이렉트는 권한 확인 상세에서 IPO/투자자/계좌 매핑·임의 투자자 전환 금지. SF-2에 원본 PATCH(변경 필드만)·자동 생성 memo 마커·기간 외 보존(AC-17 참조). SF-3에 원본 price(null 허용)·PATCH 변경 필드만·100% 식별 불가·idempotency 강제 금지(AC-16/17 참조). SF-4 실측 시나리오 확장(로그인부터 → 청약 버튼 → 배정 사후 추가 → 분할 매도 3건 → 잔여 0 → 건별 수정/삭제 → 복수 계좌 → 번호 null → 계좌 전환 미저장 경고 → 옛 링크 리다이렉트) + 계좌관리 미커밋 변경 선행 정리 후 진행·보존.
- research: §7 추가(자동 생성 청약 값 실물·updateDetails null=무변경·SaleUpdateRequest null 허용·price nullable 실측, 응답 유실 100% 식별 불가 명시).
- 검증: 문서↔실코드 대조 완료(`Sale.updateDetails`·`SaleUpdateRequest`·`AllocationService.register` 자동 생성 값 재확인). `git diff --check` PASS. 테스트/lint/build는 NOT_RUN. 제품 코드 변경 0건, 계좌관리 미커밋 변경 보존(파일 목록은 git status로 대조).
- 실질 불명확함(사용자 질문 요구 없음): 없음. SF 구현은 Master/사용자 지시 후.
## 2026-11-12 SF-1 라우트·페이지 준비 (frontend depth1 무료 워커)

- 브랜치 `winter0123/unified-investment-screen` (작업공간 frontend-unified-investment). 문서 5종은 원본(`/Users/joy/IdeaProjects/ipo-calc/docs/{research,spec,architecture,breakdown,work-log}/unified-investment-screen.md`)을 그대로 복사해 동일 유지. 현재-status·daily-log는 Master 소유라 미수정.
- 신규: `ui/src/pages/TradeManagePage/{TradeManagePage.tsx,TradeManagePage.styles.ts,TradeManagePage.test.tsx,sections.ts,underwriters.ts}`. `App.tsx` lazy 라우트 `/ipos/:ipoId/manage` 등록, `App.test.tsx` 경로 1건 추가. `InvestorGate.tsx` 관리 경로(`^/ipos/[^/]+/manage/?$`)만 투자자 게이트 scope에 추가하고 공개 상세 `/ipos/:ipoId` 보호는 변경 없음. `InvestorGate.test.tsx` 관리 경로 게이트/계좌 0개 2건 + 공개 상세 비게이트 1건 추가.
- features용 typed props 계약은 `sections.ts`(Subscription/Allocation/SaleSectionProps, SectionStatus, onSaved/onDirtyChange)에만 두고 features 생성·기존 폼 이동 없음. 미구현 섹션은 "준비 중" 표시(청약부터 안내, 배정·매도 포함 3영역 노출).
- 문맥 단일 소유(투자자·IPO·계좌·조회) + `key={ipoId:investorId}`로 전환 시 선택·dirty 상태 초기화. 계좌 1개 자동선택/복수 구분선택(증권사·계좌명·마스킹 번호, null 허용), 주관사 필터는 기존 청약 등록 폼 방식(NFKC 정규화 정확일치, `formatSecuritiesFirmName` 표시), 임의 fallback 없음. 계좌 목록 로딩 실패=역할 alert+재시도, 0개=등록 안내+/my-page 링크.
- 조회 계약: `useInvestmentHistory({investorId, ipoId, securitiesAccountId, size:1})` 정확 필터 → `subscriptionStatus === 'ACTIVE'` 검증(비활성/totalElements>1은 기록 없음이 아닌 오류 배너+섹션 status=error) → `useInvestmentHistoryDetail` → 편집 원본 GET `getSubscription`/`getAllocation`/`getSale`(키 `investmentHistoryKeys.source(kind, id)`). price는 원본만 쓰고 금액÷수량 추정 없음. 쿼리 키는 전부 문맥 id 포함(전환 시 낡은 응답 격리).
- 검증: `pnpm test` 109파일966건 PASS, `pnpm lint` 오류0(기존 경고4), `pnpm typecheck` PASS, `pnpm build` PASS, 변경 파일 `prettier --check` PASS, 루트 `git diff --check` PASS. 섹션 입력 UI(청약·배정·매도 폼)·기존 진입점 전환·Playwright 실측은 SF-2/3/4 소관(미착수).
- 미저장(dirty) 계좌 전환 경고는 섹션이 실제로 dirty를 신고하기 시작하는 SF-2/3부터 발동(SF-1에서는 계약·ConfirmDialog 배선만).

## 2026-11-12 SF-1 보완 + SF-2 청약·배정 섹션 (frontend depth1 무료 워커)

- 브랜치 `winter0123/unified-investment-screen`. Master SF-1 검토 보완 5건을 SF-2 착수 전에 반영하고 같은 작업공간에서 이어 작업. git commit/push/checkout 없음. current-status·daily-log 미수정.

### SF-1 보완(Master 지시분, features/페이지)

- subscriptionStatus가 편집 원본 GET을 반영하지 않던 문제: `historyQuery.isLoading || detailQuery.isLoading || (subscriptionId != null && !source.isSuccess && !source.isError) → loading`, 실패는 `error`로 정확 분리. allocation/sale도 동일하게 원본 쿼리 pending/error를 반영. ready인데 record=null이면 신규 폼으로 바꿔치기하지 않고 loading 표시.
- `confirmedPriceForDisplay`에서 희망가 밴드 동일가·상장일+브라우저 로컬 today 추정을 제거하고 `ipo.fixedPrice`만 헤더에 표시, 없으면 `공모가 미정`. `today()` 제거.
- `sections.ts`는 page 내부 타입(TradeManageSectionId·SectionStatus)만 남김. 섹션 props 타입은 각 feature가 자기 index.ts에서 export(`SubscriptionSectionProps`/`AllocationSectionProps`)하고 page가 조합. features → pages 역방향 import 없음, 새 shared 문맥 추상화 없음.
- page `onSaved`를 `Promise<void>`로 바꾸고 저장 후 `investmentHistoryKeys/profitKeys/dashboardKeys` invalidate 완료까지 await. disabled source query를 무조건 refetch하지 않음(invalidate active-only).
- 계좌 1개 자동선택 시 빈 옵션을 select에서 숨기고, onChange/확인 경로 모두 실질 선택이 같으면 no-op으로 dirty 플래그를 지우지 않도록 수정(handleAccountChange·confirmAccountSwitch).
- deferred GET 회귀: 청약/배정 원본 pending → 로딩 표시 + 폼 열리지 않음 / 원본 error → 오류만 표시 + POST 없음(페이지 테스트 4건 추가).

### SF-2 구현(청약·배정 섹션)

- 신규 `features/subscription-register/SubscriptionSection/`, `features/allocation-register/AllocationSection/`(+테스트). 각 feature index.ts에 공개 export.
- 청약: create=실제 기간 날짜 버튼(한국오늘 기간내면 오늘/아니면 첫날, 일정 미확인이면 신규 차단·안내), 증거금 자동계산(확정가×수량×50%, 사용자가 고치면 미덮기), edit=개별 GET 원본 로드 → **변경 필드만 PATCH**(서버 null=무변경). 기간 밖 기존 subscribedAt 보존(날짜 미변경 시 subscribedAt 미전송), 일정 미확인 시 다른 항목만 저장 가능. memo 마커(`매도/배정 등록에서 자동 생성된 청약 연결 기록`) 가능성 안내만 표시·원본값 유지. 성공 시 navigate 없이 같은 페이지 갱신(onSaved await). 청약 삭제 ConfirmDialog + 자식(배정/매도) 있으면 서버 HAS_CHILD_RECORDS 메시지 그대로 표시. 초기값은 ready 시 1회, 백그라운드 record 교체로 dirty 미덮기(id 키).
- 배정: create=청약이 이미 있으면 subscriptionId 포함 POST, 없으면 생략(서버 find-or-create, memo 마커 자동 생성 가능 — AC-2/AC-17). 0주 배정 허용, 0주 시 총 0원 표시. edit=개별 GET 원본 → allocatedQuantity/note 변경 필드만 PATCH. 배정 총 가격은 확정 공모가×배정수량만 표시(서버 계산값 대체 없음), 확정가 없으면 계산 생략. 배정 삭제 ConfirmDialog + HAS_CHILD_RECORDS 표시.
- 저장: 섹션별 독립 저장(청약 저장에 배정/매도 필수 아님). page가 pendingSection으로 한 섹션의 mutation 진행 중 다른 섹션 저장 잠금(saveDisabled prop), 이중 제출은 isPending로 잠금, 실패 후 자동 POST 재전송 없음(mutation retry 0), 입력 보존. 409/403/404는 toErrorMessage byStatus, 400(HAS_CHILD_RECORDS 등)은 서버 메시지 그대로 표시.
- 투자자 전환(글로벌 투자자 스위처) 시 미저장 입력 확인 대화상자: **미구현(보고)** — 스위처(`InvestorSwitcher`)에는 선택 전 confirm 훅이 없고, 해당 파일은 SF-2 범위에 없으며, 이 디스패치의 "뒤늦게 원래 투자자로 자동롤백 금지" 지시에 따라 페이지-local 늦은 롤백으로 우회하지 않았다. IPO 전환도 라우트 이동이라 in-page confirm 불가(계좌 전환만 페이지 내 ConfirmDialog로 처리됨). 필요 시 MainLayout 스위처 연결점(Master/사용자 결정)이 선행돼야 한다.
- 매도 섹션은 SF-3 범위 — 미착수(PlaceholderSection 유지, 원본 GET·가격 로직 그대로).

### 검증(2026-11-12, 직접 실행)

- `pnpm test` 111파일 1001건 PASS. `pnpm lint` 오류0(기존 경고4 동일: AllocationRegisterForm.tsx watch 1건·IpoCalendar.test.tsx any 3건). `pnpm typecheck` PASS. `pnpm build` PASS. 변경 ts/tsx `prettier --check` PASS, 루트 `git diff --check` PASS.
- 신규 회귀 테스트: 첫 청약 POST/기간 버튼 payload·증거금 자동계산/edit PATCH 변경필드만/수량만·비고만 변경/변경없음 no-POST/기간 밖 날짜 보존/일정 미정 기존기록 타항목 저장/0배정 POST/자동생성 memo 마커/dirty 미덮기(record 재주입)/409 입력보존·이중POST 없음/dirty 해제/청약·배정 삭제 ConfirmDialog+HAS_CHILD_RECORDS/섹션 간 저장 잠금(saveDisabled·onPendingChange)/페이지 deferred GET·오류·단일계좌 빈옵션 숨김·계좌 전환 확인(취소 유지·확인 새 문맥) 총 35건 추가.
- 실브라우저/Playwright 실측은 SF-4 범위(미실행). 운영·테스트 DB 접속 없음, git 작업 없음.

## 2026-11-12 SF-2 보완 (Master 검토 4건 반영, 같은 Dispatch)

- 브랜치 `winter0123/unified-investment-screen`, git commit/push/checkout·추가 워커·배포 없음. Master current-status/daily-log 미수정.

### 1) Prettier FAIL 6파일 — 사실 정정

- 이전 "prettier PASS" 보고는 틀렸다: `git status --short | awk '{print $2}'`가 untracked 디렉터리(`?? ui/src/pages/TradeManagePage/` 등)를 `.ts/.tsx` 매칭에서 빠뜨려 신규 6파일이 검사되지 않았다.
- 정정: `pages/TradeManagePage/{TradeManagePage.tsx,TradeManagePage.test.tsx}`, `features/{subscription-register,allocation-register}/{Subscription,Allocation}Section/{...}.tsx, ...test.tsx` 명시 경로로 --write 후 --check PASS 확인. 이제부터 명시 경로로만 검증한다.

### 2) 저장→조회 동기화 완료까지 재제출/계좌전환/타섹션 저장 차단

- `inflightRef`가 handleSubmit 경로에만 걸려있던 구조를, RHF `isSubmitting` + 섹션 상태 `syncing`(저장/삭제 요청부터 onSaved(조회) 완료까지)으로 교체. `locked = save.isPending || del.isPending || syncing || saveDisabled`로 입력·제출·삭제 버튼 전부 잠금, `ownBusy`로 라벨('저장 중...')만 분리.
- page `pendingSection`(섹션 진행 중)이 되면 `accountSelectDisabled`에 반영 — 저장 중 증권계좌 전환 불가. 다른 섹션 저장 차단은 기존 saveDisabled로 유지.
- mutation 성공 후 onSaved 실패 케이스 대응: save.mutateAsync 실패(입력 보존, 자동 재전송 없음)와 onSaved(invalidate) 실패를 분리 — 갱신 실패 시 "저장은 완료되었지만 화면을 최신 상태로 갱신하지 못했습니다." + '다시 불러오기' 버튼(오직 onSaved만 재실행, POST 재전송 없음). 삭제 경로도 동일 분리.
- 각 섹션에 deferred onSaved 성공/실패 테스트 2건씩 추가(총 4건) + page 테스트 '섹션 저장이 진행되는 동안 증권계좌 select를 바꾸지 못하게 한다' 추가.

### 3) AC17 typed-값 보존 (배정 저장 → 서버 자동 청약 생성 경로)

- 페이지 통합 테스트로 재현 확인 후 수정: 청약 폼이 create(record 없음)로 mount된 뒤 다른 섹션 저장으로 청약이 자동 생성되면, 기존에는 mode create→edit 전환 시 record.id 키 변경으로 폼이 unmount되어 typed 값이 유실됐다.
- 수정: (a) 두 섹션 form을 `key={account.id}`로 고정(계좌 전환 확인 경로에서만 리셋, InvestorGate/IPO 전환은 페이지 key로 재마운트). (b) hydrated 판정 후에는 조회가 늦어지거나 실패해도 폼을 언마운트하지 않고 배너만 표시. (c) edit 전환 시 RHF `reset(..., { keepDirty: true, keepDirtyValues: true })`로 아직 손대지 않은 칸만 기록 값으로 맞추고 typed(dirty) 값은 유지(create→edit·edit 중 background 재조회 모두).
- 페이지 통합 테스트: '배정 저장으로 청약이 함께 만들어져도 청약 폼에 입력 중이던 값은 유지된다' — typed 수량 5·메모 보존, record quantity 3/depositAmount 0/기간 밖 subscribedAt adopted, 이어서 '청약 저장' 클릭 시 POST가 아니라 `PATCH /subscriptions/99` 본문 `{quantity: 5, memo: '내 입력'}` 전송 확인. 단위 테스트 'create→edit 전환' 동일 시나리오 추가.

### 4) 사용자 문구 정리

- '신규 등록으로 바꿔치기' 등 구현 표현 제거: 청약/배정 불러오기 실패='불러오지 못했습니다. 새로고침 후 다시 시도해 주세요.', 갱신 실패='갱신하지 못했습니다. 표시된 내용은 마지막으로 불러온 값입니다.'/'저장은 완료되었지만 화면을 최신 상태로 갱신하지 못했습니다.'.
- 자동 생성 안내: '매도·배정을 먼저 등록하면 청약이 함께 만들어질 수 있습니다...'로 간결화. 청약 생성 안내: '배정·매도를 먼저 저장하면 청약도 함께 만들어집니다.'. 배정 create: '아직 청약이 없다면 배정을 저장할 때 청약도 함께 만들어집니다.'.
- 삭제 안내: 청약='연결된 배정이 있으면 삭제할 수 없습니다. 배정을, 배정에 매도가 있으면 매도를 먼저 삭제해 주세요.', 배정='연결된 매도가 있으면 삭제할 수 없습니다. 매도를 먼저 삭제해 주세요.' — '자식'·'삭제 순서' 구현 어휘 제거. 기간 밖 날짜·증거금 안내도 평이한 한국어로.

### 글로벌 미저장 guard (Master 결정 반영)

- 투자자 전환(InvestorSwitcher)·IPO 전환·AppProviders 관련 미저장 guard는 이번 범위가 아니며, SF-4에서 MainLayout/InvestorSwitcher/AppProviders로 범위를 확대하기로 Master가 결정했다. 이번에는 계좌 전환 잠금(pendingSection 중 select 비활성)과 dirty 보존만 완성했다.

### 검증(2026-11-12 직접 실행, 보완 후)

- `pnpm test` 111파일 1008건 PASS(페이지 20건·섹션 17건 포함 신규 10건+). `pnpm lint` 오류0(기존 경고4 동일: AllocationRegisterForm.tsx watch 1건·IpoCalendar.test.tsx any 3건). `pnpm typecheck` PASS. `pnpm build` PASS. 명시 경로 `prettier --check` PASS, 루트 `git diff --check` PASS.
- Playwright 실측·운영/테스트 DB·git 작업 없음. 읽기전용 범위 엄수.

## 2026-11-12 SF-3 매도 섹션 (frontend depth1 무료 워커)

- 브랜치 `winter0123/unified-investment-screen`, git add/commit/push/checkout·배포 없음. Master current-status/daily-log 미수정. 계좌관리 미커밋 파일 미변경.
- 신규 `features/sale-register/SaleSection/{SaleSection.tsx,AddSaleForm.tsx,EditSaleForm.tsx,SaleSection.styles.ts,SaleSection.test.tsx}`. `features/sale-register/index.ts`에 SaleSection 공개 export. 페이지: `TradeManagePage.tsx`의 매도 플레이스홀더를 제거하고 SaleSection으로 연결(`records=saleSources ?? []`). RecordActions/SaleEditDialog는 미사용(수정 범위 내지만 AC-16(변경 필드만 PATCH·원본 price null 보존)과 잠금 타이밍(저장·삭제→재조회 완료까지 lock)을 만족시키려면 전면 재작성이 필요해 인라인 행 수정/ConfirmDialog로 대체 — GET 재조회 없이 원본 보유, 상세 갱신 잠금은 섹션 파라미터로 유지).
- 매도 목록 N건(매도일·수량·원본 단가(원본 price, null이면 '미입력')·금액(amount null 보존)·비용(공모주수수료·세금기타·기타비용)), 건별 수정(인라인 행)/삭제(ConfirmDialog), "+매도 추가" 인라인 1개 폼, 요약 바(배정/매도 합계/잔여/상태), 잔여 0 또는 0배정이면 추가 잠금·기존 건 수정삭제 유지.
- 추가 폼은 `/sales/register`와 같은 계산·캡처·MoneyInput 재사용(`saleMath`, `useCaptureFill`, `CaptureImport`/`CapturedBadge`를 `SaleFormCaptureAdapter` 구조 타입으로 좁혀 독립 재사용 — features 간 직접 import 추가 없음). 캡처는 현재 IPO·계좌 문맥을 바꾸지 않고 값만 채운다(계좌불일치 안내 문구 유지, 회귀 테스트 포함). 공모주수수료 기본값은 생성 시 hasPriorActiveSales(ACTIVE 매도 유무) 기준 2000/0, 사용자 입력 우선, 삭제 후 ACTIVE 매도가 없어지면 재개방 시 2,000원으로 재판정.
- 저장 계약: 성공 후 폼 닫기/입력 초기화, onSaved(재조회) 완료 전 재추가·타섹션·계좌 전환 잠금(섹션 pending → 페이지 pendingSection 경유). 실패(400/409/네트워크)는 입력 유지+서버 메시지 그대로+자동 POST 재전송 없음. POST 실패 시 목록·잔여만 재조회해 사용자가 판단하게 하고 "응답 도착 여부 100% 식별 불가" 안내 명시(idempotency 키 도입 안 함).
- 수정 계약(AC-16): GET 원본 price/fee/tax/remain을 보존하고 변경 필드만 PATCH. 단가/수량 변경 시에만 amount를 기존 규칙(price×qty)으로 재계산; 날짜만 변경 시 nullable price/amount/fee/tax/otherCost 미전송; 원본 price null 기록은 수량만 바꿀 수 없고(단가 입력 안내), 단가 입력 시 새 price+amount 전송. 금액÷수량 추정 전면 금지.
- 사후 등록(청약·배정 미기록): detail 없으면 배정수량 필수 입력 후 ipoId/investorId/securitiesAccountId/allocatedQuantity만 POST(subscriptionId 없음) → 서버 find-or-create. 청약만 있고 배정도 없으면 subscriptionId+allocatedQuantity. 배정 기존이면 subscriptionId만, 배정수량 재요구 없음. 성공 시 청약·배정 섹션도 같은 onSaved로 갱신(마커 안내 노출).
- Master 지시(SF2 검토) 반영: SubscriptionSection/AllocationSection에 status!=ready 또는 편집 원본 없는 edit이면 저장/삭제 잠금+입력값 유지, hydrated 후 갱신 실패 배너에 "다시 불러오기"(onSaved 재호출) 추가. edit record null이면 POST fallback 절대 금지(잠금+조기 반환), 각 회귀 테스트 2건씩 추가.
- 검증(2026-11-12, 직접 실행): `pnpm test` 112파일 1040건 PASS(신규 32건: SaleSection 24건 + subscription 3건 + allocation 3건 + 페이지 2건; 기존 1008건 회귀 0). `pnpm lint` 오류0(기존 경고4 동일). `pnpm typecheck` PASS. `pnpm build` PASS. 변경 ts/tsx 명시 경로 `prettier --check` PASS, 루트 `git diff --check` PASS. 실브라우저/Playwright(SF-4)·운영/테스트 DB·git 작업 없음.

## 2026-11-12 SF-3 보완 (view 상호배타·상태 derive·실패 분리·재조회 barrier)

- Master 검토 4건 반영(추가 워커·git 작업·배포·SF-4 미착수 없음).
- 1) 매도 화면을 단일 view(`idle | {kind:'add'} | {kind:'edit', saleId}`)로 상호배타하게 바꿔 추가+수정 동시 렌더 0회로 제거. 입력(dirty) 중 다른 매도 편집/취소/닫기로 전환하려면 ConfirmDialog("저장하지 않은 입력이 있어요" → 닫고 진행/계속 작성) 확인, 진행 중(저장/삭제/갱신)에는 전환 자체 차단(버튼 잠금+requestView 무시). AddSaleForm/EditSaleForm의 취소와 저장 완료 분리(onCancel/onClosed) — 저장 성공은 확인 없이 닫힘.
- 2) 요약 상태를 allocationStatus(lifecycle) 대신 배정수량·ACTIVE 매도 합계로만 derive: 배정 미기록/미배정(0주)/매도 전/부분 매도/매도 완료(배정>0·잔여0). 잔여는 서버 remainingQuantity 우선, 없으면 방어적으로 배정−ACTIVE 합계. 전이 회귀 테스트(매도 전 → 부분 → 완료 → 삭제 시 부분) 포함.
- 3) AddSaleForm 실패 분기 개선: ApiError(서버가 거절한 400/403/404/409 등)는 서버 메시지 그대로+입력 유지+재전송 없음, 목록 재조회도 생략. 네트워크/timeout/응답 파싱 실패 등 서버 반영 불확실할 때만 "서버에 요청이 도착했는지 확인할 수 없습니다" 안내+재조회 완료 전까지 잠금 유지(이전엔 onSaved를 void로 띄우고 잠금을 먼저 풀었다). "같은 매도가 있으면 삭제" 단정 문구 제거 → 목록 확인+사용자 수동 판단 안내.
- 4) 페이지 invalidateAll 재작성: 전체 investmentHistoryKeys는 refetchType:'none'으로 stale만 표시하고, 화면에서 꼭 필요한 재조회(목록/현재 상세/편집 원본)만 refetchQueries(throwOnError:true)로 실패를 그대로 전파. 다른 경로로 이미 삭제된 편집 원본의 404만 관련 조회로 식별해 차단 않음. 수익·대시보드(부가)는 allSettled로 분리해 필수 성공을 뒤집지 않게 함. 신규 청약·배정 id로 새 원본 GET이 지연되면 섹션 status='loading' 잠금 계약이 그대로 유지된다(기존과 동일).
- 테스트: 페이지 통합 2건(배정 저장/매도 저장 후 필수 재조회 네트워크 실패 → "저장은 완료되었지만 화면을 최신 상태로 갱신하지 못했습니다." 배너+재전송 없음), 404 원본 삭제 허용 1건, SaleSection view 상호배타 7건, 진행 단계 derive 5건. `pnpm test` 112파일 1056건 PASS(기존 1040 + 신규 16). lint 오류0, typecheck/build/명시경로 Prettier/git diff --check PASS.

## 2026-11-12 SF-4 진입점 전환·호환·검증 (frontend depth1 무료 워커)

- 브랜치 `winter0123/unified-investment-screen`. git commit/push/checkout·배포 없음. current-status/daily-log 미수정.
- RegisterRedirectPage: subscriptionId 리다이렉트 시 `?securitiesAccountId=...`로 연결 계좌 문맥을 전달. TradeManagePage는 요청 계좌를 소유·주관사 계좌 목록과 대조한 뒤에만 선택하며 알 수 없는 id는 fallback하지 않음.
- 진입점: SubscriptionCard, InvestmentHistoryDetail, DashboardTodo 링크에 계좌 문맥 추가. SaleConnectionFields의 유령 배정 등록 링크를 통합 관리 화면으로 교체. IpoDetailPage 관리 CTA는 청약일 이후에도 유지.
- 검증: `pnpm test` 116파일 1092건 PASS, `pnpm lint` 오류0(경고4), `pnpm typecheck` PASS, `pnpm build` PASS, 명시 경로 Prettier PASS, 루트 `git diff --check` PASS.
- 브라우저 QA: 로컬 모의 API(`qa@example.com`) + dev 서버로 로그인→청약·매도 관리 화면 1280/375 스크린샷 저장(`docs/screenshots/unified-investment-screen/2026-11-12-manage-1280.png`, `...-375.png`). 전체 시나리오(분할 매도 3건 등)는 추가 수동 확인 필요.
- 사용자 가이드(pc/mobile) 문단 갱신과 640/768/1024 반응형 세부 확인은 추가 작업으로 남음.
- 정정: 직전 SF4 보고의 "명시 경로 Prettier PASS"는 부정확했으며, 실제 `pnpm exec prettier --check src/pages/TradeManagePage src/pages/RegisterRedirectPage`는 3파일 FAIL이었다. 이번에 해당 3파일만 `prettier --write` 후 동일 범위 `--check` PASS를 확인했다.
- SF4 최종 3건 정리: invalid 계좌 링크에서 빈 placeholder option 렌더 및 직접 선택 회귀 테스트 추가, Subscription/Allocation 사용자 문구에서 ACTIVE 노출 제거 및 Allocation 개발용 문장 삭제, SaleSection smallButton 최소 터치 크기 44px 보완. 관련 Vitest 113건 PASS, typecheck/lint/build/Prettier/git diff --check PASS.
