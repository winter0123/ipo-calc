# ipo-context-registration — 공모주별 등록 진입 단일 SF

> 작업 브랜치: winter0123/remove-allocation-method · 담당: frontend 워커(depth 1) · 2026-11-11

## 목적

등록(청약·매도)을 공모주 문맥에서만 시작하게 하고, 공모주 선택 select를 제거한다.

## 실제 읽은 계약

- `SubscriptionRegisterPage`(h1만) → `SubscriptionRegisterForm`. 현재 `?ipoId=`를 읽어 select 기본값으로만 쓰고, select로 공모주를 고른다. `subscribedAt`은 `<input type="date">`, payload는 `${subscribedAt}T09:00`.
- `useIpos()` 목록으로 이름/공모가/기간 표시. `useIpo(id)`(apiGetParsed `/ipos/{id}`, ipoSchema) 단건 조회 존재. 매도 폼은 이미 `useIpo`+`?ipoId` 고정 표시 패턴.
- 매도 폼: 문맥 없으면 guide(`/subscriptions` 링크)만 표시, 청약 프리셋은 `useInvestmentHistoryDetail(subscriptionId)`→ipoId 확정(소유권은 서버), `entryProblem`(mismatch/not-found) 블록, `submitBlockReason`으로 POST 차단. `saleIpoEntry`는 InvestorGate가 `/sales/register?ipoId>0`에만 예외 처리.
- 서버(SubscriptionService.register): subscribedAt 필수·LocalDateTime만 검증, **기간 검증 없음** — 클라이언트가 유효기간 밖 날짜를 보내면 안 된다. payload 형식(`T09:00`) 유지.
- 청약/매도 등록 공통 링크 생성처: SubscriptionListPage 상단 버튼, EmptyState(→`/subscriptions/register`), ProfitTable(→`/sales/register`), SubscriptionCard·SubscriptionScheduleList·DashboardUpcoming·DashboardTodo·InvestmentHistoryDetail·IpoDetailPage(모두 `?ipoId`/`?subscriptionId` 문맥 유지).
- `allocationId=`로 진입하는 URL은 저장소에 존재하지 않음(grep 0건). 매도 폼도 allocationId를 받지 않음. → 호환 대상은 `subscriptionId`뿐이며 기존 동작을 보존하고, allocationId 신규 진입은 만들지 않는다(범위 밖).

## 단일 SF 범위

1. SubscriptionListPage 상단 공통 청약/매도 등록 버튼 제거(카드·상세·기록별 링크 유지).
2. 청약 등록: select 제거 → `?ipoId` 필수. 유효 id면 단건 조회 후 이름 고정 텍스트 + 기간/공모가 표시, 변경 불가. 없음/잘못된 id/조회 실패/일정 누락·역전은 안내 + 등록 차단(임의 fallback 금지).
3. 문맥 없는 `/subscriptions/register`,`/sales/register`는 "공모주를 선택해 들어와 주세요" 계열 안내 + `/subscriptions` 링크만. EmptyState/ProfitTable 문맥 없는 링크도 `/subscriptions`로 변경.
4. 청약일 input[type=date] 제거 → `subscriptionStartDate~subscriptionEndDate`의 실제 날짜 버튼(aria-pressed, type=button). 같은 날 1개, 기간 전체를 선택 가능(2일 초과 포함). 기본값: 한국 오늘이 기간 내면 오늘, 아니면 첫날(문자열 비교, Asia/Seoul).
5. 매도일 date input·목록 기간 필터는 유지. 배정/계좌/캡처/자동 연결 로직 보존.

## 검증

vitest(관련 페이지·feature·EmptyState·ProfitTable), `pnpm typecheck && pnpm lint && pnpm build`, prettier, `git diff --check`.
