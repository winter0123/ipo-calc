# 작업 로그: 웹 IPO·청약·배정 입력 (web-subscription-workflow, Epic 14)

## 2026-09-21 — Master 결정 사항 (Phase 0 spec 작성 전 최신화)

`docs/research/web-subscription-workflow.md`는 이전 세션에서 이미 실시간
거래 처리 스코프 오류를 정정한 상태(사후 기록 서비스 전제 명시)라 큰
문제는 없으나, "불확실한 점"으로 남긴 항목들을 확정된 백엔드 spec(Epic 6/7)
기준으로 결정한다.

### D-W14-001: 청약 등록과 배정 등록은 별도 화면으로 분리한다
Subscription(Epic 6)과 Allocation(Epic 7)은 별도의 백엔드 리소스이며
(`POST /subscriptions`, `POST /allocations`), 실제로 사용자는 청약
시점과 배정 발표 시점에 시간차를 두고 각각 기록한다(배정은 청약 마감
후 며칠 뒤 발표됨). 두 화면을 하나로 합치지 않는다. 배정 등록 화면은
"아직 배정 미입력인 청약 목록"(Epic 11 `GET /investment-history`에서
`allocation: null`인 항목)에서 특정 청약을 선택해 진입하는 방식으로
연결한다.

### D-W14-002: "최소 청약 단위" 등 증권사별 규칙은 프론트에서 강제하지 않는다
[[subscription-management]] spec에는 `quantity`가 "0 초과 정수"로만
검증되며, 최소 청약 단위 배수 같은 규칙은 존재하지 않는다. 조사 문서가
언급한 "최소 청약 단위 배수 적용"은 실제 백엔드 spec에 없는 항목이므로
프론트는 이를 강제 검증하지 않는다 — 단순히 0 초과 정수만 검증한다.

### D-W14-003: 확정된 API 계약 (추측 금지)
- **청약 등록**: `POST /subscriptions` — `{investorId, securitiesAccountId, ipoId, quantity, depositAmount, subscribedAt, memo?}` → 201 / 400 / 404 / 409(중복)
- **청약 목록(미배정 필터용)**: `GET /investment-history?investorId=...` — Epic 11, `allocation` 필드로 미배정 여부 판단
- **배정 등록**: `POST /allocations` — `{subscriptionId, allocatedQuantity, allocatedAt, allocationMethod?, allocationPrice?, note?}` → 201 / 400(배정수량>청약수량 등) / 404 / 409(중복)
- **청약/배정 수정**: `PATCH /subscriptions/{id}`, `PATCH /allocations/{id}`
- **청약/배정 취소**: `DELETE /subscriptions/{id}`, `DELETE /allocations/{id}` (논리 삭제)
- **투자자/증권계좌/공모주 목록**(드롭다운용): `GET /investors`, `GET /securities-accounts`, `GET /ipos`

### D-W14-004: 배정수량 초과 검증은 서버가 최종 판단, 프론트는 UX 보조만
Σ(ACTIVE 매도수량) ≤ 배정수량, 배정수량 ≤ 청약수량 검증은 이미 Epic 7
백엔드에서 수행한다(AC-003). 프론트는 사용자 경험 개선을 위해 폼에
청약수량을 표시해 참고하게 하는 정도의 보조 검증만 추가하고, 최종
검증 실패 시 서버 400 응답 메시지를 그대로 인라인 에러로 표시한다 —
클라이언트에서 서버 검증 로직을 재구현하지 않는다.

### D-W14-005: 수정 이력 UI는 만들지 않는다
[[sale-management]] D-SL-005 / [[cost-recording]] D-C-003과 동일 원칙 —
백엔드에 별도 변경 이력 테이블이 없으므로 프론트에도 "수정 이력 보기"
같은 화면을 만들지 않는다. 단순 수정 폼(현재 값을 불러와 덮어쓰기)만
제공한다. 수정/삭제 전 확인 다이얼로그(조사 결론)는 유지한다.

### D-W14-006: 관리자 화면은 이 프로젝트 범위 밖
[[account-authentication]] 확정 사항대로 MVP는 단일 계정 타입(개인
사용자)만 존재하며 관리자 기능은 없다. "관리자를 통한 예외적 기록
수정"은 이 Epic에서 설계하지 않는다.

이 결정들은 `docs/spec/web-subscription-workflow.md` /
`docs/architecture/web-subscription-workflow.md` 작성 태스크 지시에
반영되었다.

## 2026-09-21 — SF 분해 (breakdown) (티켓: task_ca43430dea7a)

`docs/breakdown/web-subscription-workflow.md` 작성 완료. 총 **3개 SF**로 분해한다.

- **SF-1 청약 등록 화면** — 즉시 착수 가능. 필요한 백엔드 4개 API(GET /investors, GET /securities-accounts, GET /ipos, POST /subscriptions)가 전부 main 머지 확인됨(POST /subscriptions은 PR #104, commit 0c4dd04). 이 SF에서 `entities/ipo`, `entities/subscription`, `entities/securities-account`(프론트에 아직 없음)와 `shared ConfirmDialog`를 신규 생성.
- **SF-2 청약 목록 화면** — 화면 구현은 가능하나 **Epic 11(`GET /investment-history`)이 미구현**이어서 실연동·E2E 검증은 Epic 11 백엔드 머지 후. (아키텍처 문서가 "Epic 11에서 이미 구현된 API를 재사용"이라 전제했으나 실제로는 Phase 0만 완료됨 — 발견사항으로 문서에 기록)
- **SF-3 배정 등록 화면** — **선행 조건: Epic 7(`POST /allocations`) 백엔드 구현 완료 + Epic 11 상세(`GET /investment-history/{id}`) 구현**. 둘 다 미구현.

검증: 백엔드 워크트리(backend-3)에서 컨트롤러 존재 여부(GET /investors, /securities-accounts, /ipos, POST /subscriptions + main log)와 Epic 11/7 미구현을 실제 파일로 확인. 프론트 FSD(entities/features/pages) 및 Epic 13 SF-4 패턴(react-hook-form + TanStack Query + 셀렉트 드롭다운)도 읽고 반영함.

커밋/PR은 Master 담당(워커는 git 작업 안 함).

---

## 2026-09-21 — SF-1: 청약 등록 화면 구현 (task_8838c31ae3f9)

`docs/breakdown/web-subscription-workflow.md` SF-1을 산출물 파일 경로/API/특이사항 그대로 구현 완료.

### 완료된 작업
- `entities/ipo/{types,api-client}.ts` — 신규. `Ipo` 타입, `fetchIpos()` (`GET /ipos`)
- `entities/securities-account/{types,api-client}.ts` — 신규. `SecuritiesAccount` 타입, `fetchSecuritiesAccounts()` (`GET /securities-accounts`)
- `entities/subscription/{types,api-client}.ts` — 신규. `SubscriptionRegisterRequest`/`Subscription` 타입, `createSubscription()` (`POST /subscriptions`)
- `entities/investor/api-client.ts`의 `fetchInvestors()`는 Epic 13에서 이미 추가되어 있어 변경 없음
- `features/subscription-register/SubscriptionRegisterForm/SubscriptionRegisterForm.{tsx,module.css,test.tsx}` — 투자자/증권계좌/공모주 드롭다운(각각 `useQuery`) + 수량/증거금/청약일시/메모, react-hook-form + `noValidate`
- `features/subscription-register/useSubscriptionRegister.ts` — `POST /subscriptions` `useMutation`, `onSuccess`에서 `invalidateQueries(['investmentHistory'])` 후 `/subscriptions`로 이동
- `pages/SubscriptionRegisterPage/SubscriptionRegisterPage.{tsx,module.css}`
- `App.tsx`에 `/subscriptions/register` 라우트 추가
- `shared/components/ConfirmDialog/ConfirmDialog.{tsx,module.css,test.tsx}` — 신규 공통 컴포넌트만 생성(사용처 없음). `aria-modal`, Escape 닫기, 포커스 트랩, 열릴 때 확인 버튼 자동 포커스 구현

### 특이사항 반영
- quantity는 0 초과 정수, depositAmount는 0 이상 정수(0원 허용)만 검증 — 최소 청약 단위 등 신규 규칙 없음
- 404 에러는 응답 메시지에 포함된 엔티티 키워드(투자자/증권계좌/공모주)로 필드 매핑 — `securities-account-register`(Epic 13) 참고 패턴과 동일하게 폼 하단 공통 에러 영역에 표시(필드별 개별 배치 대신 기존 코드베이스 관례를 그대로 따름)
- 409는 "이미 동일한 조건의 청약이 존재합니다.", 네트워크 오류는 "네트워크 연결을 확인해 주세요."로 매핑

### 실행한 검증
- `pnpm run lint` — 통과(기존 파일의 사전 존재 warning 1건 외 무관)
- `pnpm run typecheck` — 통과
- `pnpm run format:check` — 통과
- `pnpm run test` — 44개 테스트 전체 통과 (기존 28개 + 신규 16개: SubscriptionRegisterForm 10개, ConfirmDialog 6개)
- `pnpm run build` — 통과

### 남은 작업
SF-2(청약 목록 화면)는 Epic 11(`GET /investment-history`) 백엔드 완료 후 착수. 커밋/PR/머지는 Master가 진행.

---

## 2026-09-22 — SF-2: 청약 목록 화면 구현 (task_e959f6b04bd5)

`docs/breakdown/web-subscription-workflow.md` SF-2를 산출물 파일 경로/필터 3종/카드 UI 그대로 구현했다.
**`GET /investment-history`는 이 시점 기준 백엔드(Epic 11)에 아직 구현되어 있지 않다** — `docs/breakdown/investment-query-api.md`가 SF 분해만 된 상태이며, 구현 착수 게이트(Epic 7·8 backend 머지)가 아직 열리지 않았다. 따라서 이 SF는 화면·타입만 먼저 구현하고, 실제 API 연동 검증은 기존 SF들과 동일하게 `apiFetch`를 모킹한 단위 테스트로 대체했다(실제 백엔드 호출 불가 — 정상).

### 완료된 작업
- `entities/subscription/types.ts` — `InvestmentHistoryItem`/`AllocationSummary`/`SaleSummary`/`InvestmentHistoryFilters`/`InvestmentHistoryResponse` 타입 추가. 필드는 `docs/breakdown/investment-query-api.md` SF-1이 정의한 `InvestmentHistoryDto`(id, subscribedAt, ipoId, ipoName, ipoFixedPrice, subscriptionQuantity, subscriptionStatus, allocation, sales, remainingQuantity) 그대로 반영
- `entities/subscription/api-client.ts` — `getInvestmentHistory(filters)` 추가. investorId/securitiesAccountId/ipoId/page/size 중 지정된 값만 쿼리스트링에 포함
- `features/subscription-list/useInvestmentHistory.ts` — `useQuery`, `queryKey: ['investmentHistory', filters]`
- `features/subscription-list/SubscriptionFilter.tsx`(+`.module.css`) — `useState<'all'|'unallocated'|'allocated'>` 로컬 필터 버튼 그룹(URL 파라미터 미사용), 선택값을 `onChange`로 상위에 전달
- `features/subscription-list/SubscriptionList/SubscriptionList.tsx`, `SubscriptionCard.tsx`(+`.module.css`) — 필터값 기준 클라이언트 사이드 필터링(백엔드 API에 배정여부 필터 파라미터가 없으므로, AC-009/010의 Given "전체 목록이 표시됨"과 일치하게 이미 로드된 목록을 필터링) + 카드(공모명·청약수량·청약일시·배정 여부 배지). 미배정(`allocation: null`) 카드에만 "배정 입력" 링크(`/allocations/register?subscriptionId={id}`) 표시
- `pages/SubscriptionListPage/SubscriptionListPage.tsx`(+`.module.css`) — 필터+목록 조합, 로딩/에러(404·네트워크·기타) 상태 처리
- `App.tsx` — `/subscriptions` 라우트 추가
- `pages/SubscriptionListPage/SubscriptionListPage.test.tsx` — `apiFetch` 모킹 + `QueryClientProvider`+`MemoryRouter`+`fireEvent` 패턴(Epic 13/SF-1과 동일)으로 11개 테스트 작성: 로딩(AC-008), 필터 3종(AC-009/010/전체), 필터 전환 시나리오(E2E 3), 배정 입력 버튼 표시/href(AC-011/012), 빈 목록, 404/네트워크/기타 에러

### 발견사항 — spec-DTO 불일치
`docs/spec/web-subscription-workflow.md` 2절(화면 구성)은 청약 카드에 "증권계좌"를 표시하라고 명시하지만, `docs/breakdown/investment-query-api.md`가 정의한 `InvestmentHistoryDto`에는 증권계좌 관련 필드(securitiesAccountId 등)가 없다. DTO를 추측으로 확장하지 않고 확정 문서(investment-query-api.md) 기준으로 타입을 만들었으므로, 이번 구현의 카드에서는 증권계좌를 표시하지 않는다. Epic 11 SF-1 구현 시 DTO에 필드 추가가 필요한지, 아니면 spec을 정정할지 Master/Planning 확인이 필요하다.

### 특이사항 반영
- 필터 상태는 로컬 state(뒤로/앞으로가기로 안 바뀜), `SubscriptionFilter`가 내부 `useState`로 관리하고 `onChange`로 부모에 전달
- 배정여부 필터는 백엔드 쿼리 파라미터가 아니라 프론트에서 이미 조회된 `content` 배열을 클라이언트 사이드로 필터링(investment-query-api.md SF-2가 정의한 필터는 investorId/securitiesAccountId/ipoId뿐이며 배정여부 필터가 없음을 확인)
- "배정 입력"은 Dashboard의 기존 관례(`Link`를 버튼처럼 스타일링)를 따라 `<Link>`로 구현 — `subscriptionId`를 URL 파라미터로 전달

### 실행한 검증
- `pnpm run lint` — 통과(기존 파일의 사전 존재 warning 1건 외 무관)
- `pnpm run typecheck` — 통과
- `pnpm run format:check` — 통과
- `pnpm run test` — 55개 테스트 전체 통과 (기존 44개 + 신규 11개)
- `pnpm run build` — 통과

### 남은 작업
- 실데이터 연동·E2E 시나리오 1 검증은 Epic 11(`GET /investment-history`) 백엔드 머지 후 진행
- 카드의 "증권계좌" 표시 여부는 위 발견사항 확인 후 반영
- SF-3(배정 등록 화면)는 Epic 7(`POST /allocations`) + Epic 11 상세 조회 백엔드 완료 후 착수
- 커밋/PR/머지는 Master가 진행
