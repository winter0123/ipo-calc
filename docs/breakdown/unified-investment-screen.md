# 청약·매도 통합 관리 화면 breakdown (unified-investment-screen)

> 단일 worktree(`winter0123/account-manage-edit-flow`)라 구현 시 병렬 편집 금지. 기존 미커밋 계좌관리 변경(11파일)과 같은 트리 — 구현은 계좌관리 파일을 수정하지 않는다. 테스트는 지금 실행하지 않는다(NOT_RUN).

## SF-1 라우트·페이지 준비 + 최종 진입점 전환(SF-2/3 완료 후)

- 범위(준비): `App.tsx` 라우트 신설(`/ipos/:ipoId/manage`), `pages/TradeManagePage` 스켈레톤(공모주/투자자/계좌 select + 섹션 자리, 계좌 1개 자동·여러 개 구분·번호 없는 계좌 표시·미저장 경고). **이 단계에서 기존 진입점은 그대로 둔다 — 스켈레톤만 먼저 병합/배포하면 기존 등록 진입점이 먹통이 되므로 금지.** 진입점 전환(SubscriptionScheduleList·SubscriptionCard·IpoDetailPage CTA·DashboardUpcoming/DashboardTodo·InvestmentHistoryDetail·SaleConnectionFields 링크 → 통합 화면)은 SF-2/SF-3이 완성되고 호환경로가 검증된 뒤, 전환과 배포를 한 묶음으로 한다. 기존 `/subscriptions/register`·`/sales/register`·`/allocations/register`는 같은 화면으로 리다이렉트(`replace` + `state.from` 보존), 문맥이 없으면 목록 안내(기존 동작). `subscriptionId` 리다이렉트는 권한 확인된 상세(investment-history 소유권 검증 — 타인 기록은 403/404)에서 IPO/투자자/계좌를 정확히 매핑해 manage 문맥으로 보낸다. 현재 선택된 투자자가 그 기록의 투자자와 다르면 글로벌 투자자를 임의 전환하지 않고 안내만 표시하며, 타인의 기록은 노출하지 않는다.
- 비범위: 섹션 내부 로직(SF-2/3), 백엔드, DB.
- AC: AC-1, AC-9, AC-10, AC-11. 계좌/투자자/IPO 변경 시 쿼리 키 `(investorId, ipoId, accountId)`로 재조회. 게이트는 관리 경로만 선별 scope 등록(공개 상세 보호 불변).
- 수정 파일: `ui/src/app/App.tsx`, `ui/src/pages/TradeManagePage/*`(신규), `ui/src/features/subscription-list/*`, `ui/src/pages/IpoDetailPage/IpoDetailPage.tsx`, `ui/src/features/dashboard/Dashboard*.tsx`, `ui/src/widgets/InvestmentHistoryTable/InvestmentHistoryDetail.tsx` — **`securities-manage/` 제외**.
- 검증(구현 시): `pnpm exec vitest run` 관련 파일 + `pnpm typecheck && pnpm lint && pnpm build`, `git diff --check`. NOT_RUN.

## SF-2 청약·배정 섹션 (원본 조회 → PATCH/POST)

- 범위: 청약 섹션 — 기록 없으면 첫 입력 폼(수량·증거금·청약기간 날짜 버튼·메모, `subscriptionPeriodDates` 재사용), 있으면 **개별 GET 원본 조회**(`/subscriptions/{id}`: quantity·depositAmount·subscribedAt·memo) → 변경 필드만 포함한 PATCH(null=무변경). 배정 섹션도 동일 — `GET /allocations/{id}` 원본(allocatedQuantity·allocatedAmount·note; note 편집만) → PATCH, 없으면 POST. 사후 등록 자동 생성 청약은 기존 memo 마커('매도/배정 등록에서 자동 생성된 청약 연결 기록')로 가능성 안내 + 사용자 수정 가능, 기간 외 기존 subscribedAt 보존(강제 변경 금지), 새 청약은 실제 기간 버튼, 인라인 dirty 입력 미덮어쓰기. 연결 id 탐색은 (`investorId`+`ipoId`+`securitiesAccountId`) 정확 필터 size:1 조회 + 응답 `subscriptionStatus === 'ACTIVE'` 검증(이력 조회는 deletedAt IS NULL만 반환). 저장 후 `investmentHistoryKeys` 무효화. 부분 실패는 섹션별 오류 + 재시도.
- 비범위: 매도 섹션, 금융 계산 변경, 복합 API.
- AC: AC-2, AC-3, AC-4, AC-5, AC-7, AC-13, AC-17. 첫 저장에 매도 필드 요구 없음.
- 수정 파일: `pages/TradeManagePage/*`, `features/subscription-register/SubscriptionRegisterForm/*` 또는 `features/investment-edit/*EditDialog.tsx`의 검증 로직 추출(새 중복 폼 대량 복사 금지), `useSubscriptionRegister`/`useAllocationRegister` 재사용.
- 검증(구현 시): vitest(원본 로드→PATCH/409 표시/부분 실패 재시도/페이지 순회 ACTIVE 탐색) + typecheck/lint/build. NOT_RUN.

## SF-3 매도 섹션 (N건 리스트 + 건별 수정삭제)

- 범위: 문맥은 TradeManagePage가 소유하고 props로 주입(자체 navigate·계좌 select 제거). "+매도 추가" → 인라인 1행(매도일·수량·**원본 price**(null 허용)·공모주수수료·세금 및 기타 — 캡처 자동입력 유지) → **저장 후 상세 재조회 완료 전 다음 추가 잠금**, 성공 시 폼 닫음/입력 초기화, 재추가 시 잔여·수수료 기본 0원 재계산. 건별 수정/삭제는 `RecordActions` 재사용(현재 페이지) — PATCH는 변경 필드만 포함(null·0 덮어쓰기 금지), amount÷quantity 재계산 금지. 요약 바: 배정/Σ매도/잔여/상태(잔여 0이면 추가 불가, 기존 수정/삭제 유지). 10주 분할 픽스처(3주12,000/4주13,500/3주11,000=잔여0). 응답 유실 시 자동 POST 재전송 금지 — 재조회해 목록/잔여 표시 + 100% 식별 불가 명시 + 사용자 수동 결정(idempotency 키 강제 금지). 이중 클릭은 isPending로만(unique 제약 금지).
- 비범위: 배정 수량 감소 제약 변경(서버 기존 규칙 유지 — 같은 수량 허용), `HAS_CHILD_RECORDS` 해제, 수수료 정책 변경.
- AC: AC-2, AC-5, AC-6, AC-12, AC-13, AC-14, AC-15, AC-16, AC-17. 잔여·상태 계산은 `calculateRemainingQuantity`와 일치.
- 수정 파일: `features/sale-register/*`(문맥 props 모드 추가), `pages/TradeManagePage/*`, `features/investment-edit/RecordActions.tsx` 호출부.
- 검증(구현 시): vitest(분할 누적/잔여/재조회 잠금/응답 유실 복구/이중 클릭/수량 초과 400) + 전체 vitest. NOT_RUN.

## SF-4 검증·증적·문서

- 범위: Playwright(375px/1280px) 실측 증적(로그인부터 시작 → 청약 버튼으로 첫 청약 → 배정 나중 추가 → 분할 매도 3건(10주) → 잔여 0 → 건별 수정/삭제 → 복수 계좌 → 번호 null 계좌 → 계좌 전환 미저장 경고 → 매도부터 사후 등록 → 예전 등록 링크 리다이렉트), `docs/user-guides/manual/{pc,mobile}.html` 갱신, work-log 갱신, `git diff --check`. 기존 계좌관리 미커밋 변경이 먼저 정리(커밋/푸시)된 뒤 진행하고, 그 파일을 덮어쓰지 않고 보존한다.
- 비범위: 운영 배포·실계좌 검증(사용자 승인 별도).
- 검증(구현 시): 관련 vitest + build + 캡처 검토. NOT_RUN.

## 의존관계

SF-1 → SF-2 → SF-3 → SF-4. SF-2/SF-3는 같은 페이지 파일을 만지므로 단일 트리에서 순차. 병렬 구현 금지.

## 충돌 위험

- 미커밋 계좌관리 변경과 `pages/`·`features/` 겹침 없음. 통합 화면의 게이트 등록은 SF-1 범위(`InvestorGate.tsx`)에서만 다룬다.
- `docs/current-status.md`/`daily-log`는 Master 소유.

## 권장 구현 선택 (필수 질문 없음 — 사용자 승인 사실 아님)

아래는 Master 검토로 확정된 구현 선택이다. 별도 사용자 질문 없이 진행 가능한 기본안이며, 사용자 승인을 이 문서가 만들지는 않는다. 착수는 Master/사용자 지시 시에만.

- 경로 `/ipos/:ipoId/manage`, 화면명 "청약·매도 관리".
- 사후 매도 등록 지원 유지(서버 자동 생성/재사용).
- 공모주수수료 현행 기본 보존(배정 1건당 1회, 기존 매도 있으면 0원). 실제 수수료는 사용자가 수정 가능, 서버 하드과금 금지.
- 계좌/투자자/IPO 전환: 미저장 경고 → 취소=현재 유지 / 확인=새 문맥 재조회+입력 초기화.
- 통합 화면 로그인/투자자 게이트: 현 보안 의미와 동일, 관리 경로만 선별 scope 등록. 공개 상세 보호 불변.
- `subscriptionId` 리다이렉트: 권한 확인된 상세에서 IPO/계좌 정확 매핑.
- 연결 조회: (investorId+ipoId+securitiesAccountId) 정확 필터 + size:1 + ACTIVE 검증, API/DB 변경 없음.
