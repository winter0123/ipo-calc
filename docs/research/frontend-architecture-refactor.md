# 프론트엔드 아키텍처 리팩토링 조사 문서 (frontend-architecture-refactor)

> **Epic**: Epic 20 (frontend-architecture-refactor, 후보 — 사용자 승인 2026-10-01)
> **분야**: 기획 (Planning) — Phase 0 조사 단계. **제품 코드(`ui/`, `backend/`)는 수정하지 않았다.**
> **작성일**: 2026-10-01
> **작성 경위**: 통상은 planning 워커가 쓰지만, 사용자가 "이번 건은 Master가 직접 수정"하도록 지시해(2026-10-01) Master가 직접 작성했다. 워커 dispatch는 하지 않았다.
> **조사 기준**: `origin/main` b291e58 (`ui/src` 비테스트 소스 약 4,700줄, 테스트 30파일 266건)
> **참고 문서**: `docs/guide-frontend.md`, `docs/roadmap.md`, `docs/current-status.md`, `docs/spec/dashboard-aggregation-api.md`, `docs/spec/posthoc-ux-redesign.md`, `docs/breakdown/posthoc-ux-redesign.md`, `backend/.../dashboard/controller/DashboardController.java`

## 0. 읽는 법

- **[확인]** = 이번 조사에서 코드·명령으로 직접 확인한 사실. **[추정]** = 코드 읽기에서 나온 추론으로 재현 전. **[미정]** = 근거 문서가 없어 사용자 결정이 필요한 항목.
- 이 문서는 "무엇이 문제인가"만 다룬다. 해법은 `docs/architecture/frontend-architecture-refactor.md`, 범위·AC는 `docs/spec/...`, 작업 분해는 `docs/breakdown/...`.

## 1. 기준선 (수정 전)

`ui/`에서 실제 실행한 결과(2026-10-01, main b291e58).

| 명령 | 상태 | 요약 |
|---|---|---|
| `pnpm run test` | PASS | 30 파일, 266 테스트 통과 |
| `pnpm run lint` | PASS | 출력 없음 |
| `pnpm run typecheck` | PASS | `tsc -b` 오류 없음 |
| `pnpm run format:check` | PASS | "All matched files use Prettier code style!" |
| `pnpm run build` | PASS | 190 모듈 변환, dist 생성 |

axe·E2E는 이번 조사에서 실행하지 않았다(NOT_RUN). 종료 코드는 셸 제약으로 캡처하지 못해 출력 내용만 근거로 삼았다.

## 2. 현재 구조 요약

- FSD 레이어는 `src/{pages,widgets,features,entities,shared}` 형제로 존재하고 **`app` 레이어는 없다**. 진입점(`main.tsx`)·라우터(`App.tsx`)·글로벌 CSS(`index.css`)가 `src` 루트에 흩어져 있다.
- `.gitkeep`이 `src/{features,pages,widgets,entities}`와 `src/shared/components`에 남아 있다(실제 파일이 있는 폴더라 불필요).
- `entities/*`는 전부 `api-client.ts` + `types.ts`뿐이고 **public API(`index.ts`)가 없다**. 모든 import가 `../../entities/x/api-client.ts`처럼 세그먼트 파일을 직접 가리킨다(상대 경로 + `.ts/.tsx` 확장자. `tsconfig.app.json`의 `allowImportingTsExtensions` 때문).
- 서버 상태는 TanStack Query, 폼은 React Hook Form, `useEffect`는 4개 파일(`AllocationRegisterPage`, `PeriodSelector`, `ConfirmDialog`, `SaleRegisterForm`)에서만 쓴다(`rg`, 테스트 제외).

## 3. 확인된 결함·리스크 (증거 포함)

### 3.1 계약 위반 타입 — `DashboardByPeriod` **[확인]**

- 백엔드 `GET /dashboard/by-period`는 `@RequestParam String unit`만 받고 `List<PeriodAggregationDto>`(`[{period, totalInvestmentAmount, ...}]` 평면 배열)를 반환한다(`DashboardController#getByPeriod`, `docs/spec/dashboard-aggregation-api.md` 2.4절).
- 프론트 `entities/dashboard/types.ts`의 `DashboardByPeriod`는 `{unit, year?, month?, data[]}` **래퍼 객체**로 정의돼 있고, `api-client.ts`·`useDashboardByPeriod`는 `DashboardByPeriod[]`(= 래퍼의 배열)로 타입을 붙인다. 실제 응답 모양과 **타입이 전혀 다르다**.
- 이 불일치를 `DashboardPage.tsx:138`의 `currentQuery.data as unknown as { period: string; totalInvestmentAmount: number }[]`가 덮고 있다. 같은 파일에서 `securities-firm`·`investor` 탭도 `as` 단언으로 읽는다(`currentQuery`가 탭별 유니온이라 좁혀지지 않음).
- 테스트도 같은 형태의 mock을 쓰므로 회귀를 못 잡는다(DashboardPage.test.tsx는 훅을 통째로 mock).

### 3.2 `year`/`month` 파라미터를 백엔드가 무시 **[확인 → 제품 판단 필요]**

- `getDashboardByPeriod`는 `?unit=&year=&month=`를 보내지만 백엔드 컨트롤러는 `unit`만 읽는다. 즉 월·연도 선택(`PeriodSelector`)은 **쿼리 키만 바꿔 같은 전체 목록을 다시 받는다**.
- Epic 19 breakdown은 "기존 `GET /dashboard/by-period`를 그대로 쓴다(SF-3)"고 했으므로 의도된 제약일 수 있으나, 월 이동 버튼이 "해당 월 조회"처럼 동작하는지는 사용자에게 **오해를 줄 수 있다**. 이 Epic에서는 고치지 않고 **D-9로 승인 요청**(백엔드 변경이 필요할 수 있어 범위 밖).

### 3.3 쿼리 키·invalidation 불일치 **[확인(코드) / 재현 미실시]**

| 항목 | 증거 | 위험 |
|---|---|---|
| 기간 탭 invalidation | `useDashboardTabs`의 `getTabKey('month')`=`['dashboardByPeriod', {unit:'month'}]`, 실제 쿼리 키=`['dashboardByPeriod', 'month', year, month]`. TanStack 부분 매칭은 요소별 비교라 `{unit}` 객체 ≠ `'month'` 문자열 → **일치하지 않음** | 필터 초기화(`invalidateCurrentTab`)가 기간 탭에는 효과 없음 **[추정: 동작 재현은 SF에서 테스트로 확정]** |
| `['investmentHistory', X]` 혼용 | `useInvestmentHistory` ×2(investment-history, subscription-list)가 키 `['investmentHistory', filters]`를 공유, `SaleRegisterForm`은 `['investmentHistory', selectedInvestorId]`(숫자) 사용 | 같은 prefix에 모양이 다른 키가 섞임. prefix invalidation은 우연히 모두 닿지만 키 규약이 없음 |
| `['investors']` 중복 선언 | `useInvestorList`, `ProfitViewPage`, `SaleRegisterForm`, `SubscriptionRegisterForm`이 각자 동일 키·동일 `fetchInvestors`를 재선언 | 훅 중복. 한 곳만 옵션(`staleTime` 등)을 바꾸면 불일치 |
| 죽은 invalidation | `useCostRegister`가 `['costs']`를 invalidate하지만 `['costs']`를 쓰는 쿼리가 **없다** | 비용 등록 후 갱신이 필요한 화면(투자 내역·수익)이 invalidate되지 않음. 현재는 `staleTime 0`+ 재마운트 refetch에 의존 |
| 네이밍 혼재 | `dashboardSummary`(camel) vs `securities-firms`(kebab) vs `securities-accounts`/`securitiesAccounts` 두 철자 | 같은 데이터가 철자가 다른 두 키로 존재(`useSecuritiesAccountList`=`securitiesAccounts`, `SubscriptionRegisterForm`=`securities-accounts`) → 캐시 분리 |

### 3.4 죽은 코드·중복 **[확인]**

- `features/investment-history/FilterBar/useInvestmentHistoryFilters.ts`: `rg`로 참조처 0건. `InvestmentHistoryPage`가 같은 필터 상태·reset 로직을 직접 보유.
- `useInvestmentHistory`(investment-history)와 `useInvestmentHistory`(subscription-list): 이름·키·queryFn이 같고 옵션만 다른 **동명 중복 훅**.
- `useDashboardSummary/ByInvestor/BySecuritiesFirm/ByPeriod`: `enabled: true` 기본값 재명시, `select: (data) => data`(항등), `staleTime: 0`(TanStack 기본값과 동일) 잡음. `useInvestmentHistoryDetail`·`useSecuritiesAccountList`·`useInvestorList`도 `staleTime: 0`.
- `DashboardTabs`·`useDashboardTabs`·`DashboardPage`에 탭 목록·라벨이 세 군데 중복, `TAB_QUERY_KEYS_MAP`은 한 항목짜리 맵을 별도로 둠.
- `getTabQueryConfig`/`invalidateCurrentTab`은 `QueryClientLike` 인터페이스를 따로 정의(이미 `QueryClient` 타입이 있음).

### 3.5 FSD 경계 위반 **[확인]**

| 위반 | 위치 |
|---|---|
| 같은 레이어 slice 간 import | `widgets/DashboardCardGroup` → `../DashboardCard/DashboardCard.tsx` |
| 페이지가 entity API를 직접 호출 | `pages/ProfitViewPage` → `entities/investor/api-client.ts`(`fetchInvestors`) |
| 위젯이 feature 훅을 호출 | `widgets/InvestmentHistoryTable` → `features/investment-history/useInvestmentHistoryDetail` (widgets → features 방향은 허용이지만, 위젯이 서버 상태를 소유함) |
| 페이지가 slice 내부 파일 직접 import | 대부분의 `pages/*`가 `features/x/Y/Y.tsx`를 경로로 직접 가리킴(public API 부재) |
| 훅 파일이 `DashboardTab` 타입을 소유 | `features/dashboard/DashboardTabs`가 `useDashboardTabs.ts`에서 타입을 import |
| `app` 레이어 없음 | 진입점·라우터·Provider·전역 CSS가 `src` 루트 |
| 테스트 헬퍼 위치 | `src/test/setup.ts`는 FSD 밖(허용 — 테스트 인프라) |

### 3.6 접근성 **[확인]**

- `eslint.config.js`의 `jsx-a11y/no-static-element-interactions`, `click-events-have-key-events`는 Epic 13 스캐폴딩(`5ca7a83`)부터 `off`. 끈 이유는 커밋·문서에 기록이 없다. CLI 오버라이드(`--rule`)로 두 규칙을 켜고 전체를 린트하면 **위반 0건**이고, 잘못된 샘플(`<div onClick>`)에서는 규칙이 실제로 에러를 낸다 → **규칙을 `error`로 복원해도 현재 코드는 깨지지 않는다**(예방 효과만 회복). `InvestmentHistoryRow`의 `<tr role="row" tabIndex={0} onClick onKeyDown>`은 role·키 핸들러가 있어 통과하지만 의미상 `<tr>`에 role="row"를 덮는 패턴이라 별도 검토 대상.
- **aria-invalid 오염** **[확인]**: `aria-invalid={… || …}`로 mutation 오류를 섞는 패턴이 **8개 파일 27곳**(`rg "aria-invalid=\{[^}]*\|\|"`, 비테스트; Login, Signup, SecuritiesAccount, Cost, SubscriptionRegister 등)에 있다. 27곳 중 오류 결합이 아닌 `||`가 섞였는지는 구현 시 개별 확인. `error`는 **mutation 일반 오류**라 서버 오류가 모든 필드를 동시에 "잘못된 입력"으로 표시한다. 필드 검증 오류만 반영해야 한다.
- **깨진 `aria-describedby`** **[확인]**: `aria-describedby="…"` 정적 문자열이 **11개 파일 37곳**(`rg`, 비테스트)에 있고 대부분 항상 붙이지만(항상 렌더되는 요소를 가리키는 경우는 구현 시 개별 제외) 에러 요소(`id="x-error"`)는 오류가 있을 때만 렌더된다 → 평상시 존재하지 않는 id 참조. `PeriodSelector`만 `error ? 'id' : undefined`로 올바르게 조건부 처리.
- 일반(mutation) 오류 표시: `LoginForm` 등은 `<div aria-live="polite">`이고 `role="alert"`가 아니다(폼 수준 오류 전달 방식이 폼마다 다름).
- 로그인 오류 분기가 `error.message?.includes('Unauthorized')` **문자열 매칭**이다. `ApiError.status === 401`을 써야 한다(`shared/api/apiError.ts`가 이미 있음).
- `DashboardCard`가 폼 컨트롤이 없는 곳에서 `<label>`을 사용, 대시보드의 `<a href="/investments">`는 SPA 전체 새로고침(`react-router`의 `Link`가 아님) **[확인]**.

### 3.7 CSS·HTML **[확인]**

- `index.html`: `<html lang="en">`(UI는 전부 한국어), `<title>ui</title>`, viewport 메타는 `width=device-width, initial-scale=1.0`.
- `index.css`:
  - `#root { width: 1126px; max-width: 100%; margin: 0 auto; text-align: center; border-inline: 1px solid var(--border); min-height: 100svh; display: flex; flex-direction: column }` — Vite 템플릿 잔여물이지만 **모든 화면에 실제 적용 중**이라 제거 시 전 화면 레이아웃(폭 제한·양옆 테두리·중앙 정렬)이 바뀐다. 단순 삭제 불가.
  - `.counter`(사용처 없음 **[확인]**: `rg`로 `src` 내 0건), `#social .button-icon`(사용처 없음 **[확인]**) — 미사용 선택자.
  - `h1`(56px) 등 전역 타이포가 템플릿 값 그대로. `h1` 크기는 각 페이지 모듈 CSS가 덮는지 미조사 **[미정]**.
- `widgets/EmptyState`: `EmptyState.module.css`가 존재하고 클래스 `.empty-state-*`를 정의하지만, **TSX는 모듈을 import하지 않고 문자열 클래스(`className="empty-state-container"`)를 쓴다**. 전역 정의가 없으므로(`rg`) **스타일이 적용되지 않는다 [확인]**. 의도된 스타일(파란 버튼, 패딩 등)이 죽어 있는 시각 결함. 수정 시 화면이 달라지므로 승인 대상(D-8).

### 3.8 라우팅 **[확인]**

- `App.tsx`: 14개 경로. `/`는 `<div>Hello</div>`. `*`(Not Found) 없음, 라우트 오류 UI(error boundary) 없음.
- 인증: 보호 라우트·로그인 redirect 없음. 미인증 API 호출은 `apiFetch`가 `ApiError(401)`을 throw할 뿐 **전역 처리가 없다**. 각 화면이 오류 문구만 표시. `LoginForm`의 성공 후 `/dashboard` 이동, 가입 후 `/investors/register` 이동만 존재.
- `/`에 대한 제품 요구사항 문서는 `docs/`에 **없다**(`rg`로 spec/architecture/breakdown/roadmap 확인). 사용자 결정(2026-10-01): **현행 `Hello` 유지**.

### 3.9 API 계층 **[확인]**

- `apiFetch`(`shared/api/client.ts`)는 `RequestInit`을 받아 `signal` 전달이 가능하지만, **entity의 모든 API 함수가 `signal`을 노출하지 않는다**. TanStack Query가 주는 `AbortSignal`을 쓰지 못해 탭 전환·필터 변경 중 이전 요청이 취소되지 않는다.
- 성공 본문 파싱이 `(text ? JSON.parse(text) : undefined) as T`라 **런타임 검증이 없다**. 3.1의 타입 불일치가 컴파일·테스트를 통과한 근본 원인이다.
- `QueryClient`는 `new QueryClient()` 기본값 그대로(`retry` 3회 기본, `staleTime` 0). 재시도 정책이 `ApiError.status`를 고려하지 않아 **4xx(입력 오류·권한)도 3회 재시도**한다(쿼리 한정). mutation은 기본 `retry: 0`이라 금융 데이터 이중 제출 위험은 현재 없음 — 이 속성은 **유지되어야 한다**.
- 일부 api-client 주석이 "Epic 11 백엔드 미구현"이라 적혀 있으나 Epic 11은 완료(오래된 주석).

### 3.10 테스트 **[확인]**

- 30 테스트 파일, 266건. `fireEvent.` 호출 18개 파일 210회, **`@testing-library/user-event`는 설치돼 있지만 0회 사용**.
- 커버리지 공백: 쿼리 훅 단위 테스트(키·enabled·signal·invalidation) 없음, 라우팅·`App` 전체 경로 테스트는 `App.test.tsx` 1개, Not Found 없음(기능이 없으므로), 탭별 데이터 타입 테스트는 훅 mock 기반.
- `shared/api/client.test.ts`·`apiError.test.ts`는 존재(전송 계층 테스트 위치는 이미 적절).

## 4. 분류표 (모듈 | 현재 책임 | 실제 사용자/업무 의미 | 사용 위치 | 제안 레이어/slice | 근거)

### 4.1 entities

| 모듈 | 현재 책임 | 업무 의미 | 사용 위치 | 제안 | 근거 |
|---|---|---|---|---|---|
| `entities/investor` | 투자자 API·타입 | **업무 개념**(투자자) | features(investment-history, sale, subscription-register), `pages/ProfitViewPage` | 유지 + `index.ts`. 페이지 직접 호출은 feature 훅 경유로 | 여러 feature가 공유 |
| `entities/securities-account` | 증권계좌 API·타입 | 업무 개념 | features(investment-history, subscription-register) | 유지 + `index.ts` | 〃 |
| `entities/securities-firm` | 증권사 카탈로그 API·타입 | 기준정보(업무 개념) | features(securities-account-register) 1곳 | 유지 | 사용처가 1곳이어도 기준정보 개념. 과분해 금지라 통합 여부는 [미정] |
| `entities/ipo` | 공모주 API·타입 | 업무 개념 | features(ipo-calendar, subscription-register), widgets/IpoCalendar | 유지 + `index.ts` | 다수 사용 |
| `entities/subscription` | 청약 + 투자 내역 조회 API·타입 | 업무 개념(청약). 단 `getInvestmentHistory`/`InvestmentHistoryItem`은 청약·배정·매도를 합친 **조회 모델** | features 6곳, pages, widgets | 청약 CRUD는 entity 유지, `InvestmentHistory*`는 **read model**로 분리 검토 | 이름은 subscription이나 응답이 조회 전용 집계(Epic 11 `investment-query-api`) |
| `entities/allocation` | 배정 API·타입 | 업무 개념 | features(allocation-register) 1곳 | 유지 | 〃 |
| `entities/sale` | 매도 API·타입 | 업무 개념 | features(sale-register) 1곳 | 유지 | 〃 |
| `entities/cost` | 비용 API·타입 | 업무 개념 | features(cost-register) 1곳 | 유지 | 〃 |
| `entities/profit` | 수익 조회 API·타입 | **업무 개념이 아니라 계산된 조회 결과**(매도·투자자별 수익) | features(profit-view) 1곳 | **read model**: `features/profit-view` 소유 후보 | 사용처 1곳, 자체 생애주기·등록 행위 없음 |
| `entities/dashboard` | 대시보드 집계 API·타입 | **화면용 집계 read model**(사후 합산. 저장·변경되는 개념 아님. `dashboard-aggregation-api` spec D-D-002 "저장 없음") | features/dashboard, widgets/DashboardCardGroup | **entity 아님** → `features/dashboard`(또는 `pages/DashboardPage`) 소유 후보 | 업무 개념이 아니라 조회 집계. 사용처가 기능 1개+위젯 1개 |
| `entities/auth` | `LoginRequest` 타입만 | 업무 개념 아님(인증 폼 입력 DTO) | features/login 1곳 | **`features/login` 안으로 이동** 후보 | API 함수도 없고 타입만 있어 entity 요건 미충족 |

### 4.2 features

| 모듈 | 현재 책임 | 사용자 가치 | 제안 |
|---|---|---|---|
| `investment-history` | 필터바 + 계좌 선택 + 조회 훅 4종 + 죽은 필터 훅 | 투자 내역 조회 | 유지. 죽은 훅 삭제, 중복 훅 통합, 훅 세분화 재검토 |
| `subscription-list` | 청약 목록 필터·카드 + 중복 조회 훅 | 청약 목록 조회 | 유지. 조회 훅 중복 제거 |
| `dashboard` | 탭·기간 선택 + 집계 훅 4종 | 대시보드 조회 | 유지. 탭 정의 단일화, 타입 교정 |
| `profit-view` | 수익 조회 토글·테이블 + 훅 2종 | 수익 조회 | 유지. `ProfitViewPage`의 투자자 목록 조회를 feature로 |
| `*-register`(6종) | 등록 폼 + mutation 훅 | 이미 가치 단위 | 유지. 오류·접근성 패턴 정리 |
| `login`, `signup` | 인증 폼 | 로그인/가입 | 유지. 401 분기 `ApiError.status`화 |
| `ipo-calendar` | 일정 이벤트 빌드 + 훅 | 일정 조회 | 유지 |

### 4.3 widgets

| 모듈 | 현재 책임 | 제안 | 근거 |
|---|---|---|---|
| `InvestmentHistoryTable`(+Row/Detail) | 표·행 선택·상세 조회 | widget 유지. 상세 조회(서버 상태)를 위젯이 소유하는 것은 **허용하되 문서화** | 독립적 대형 UI 블록 |
| `IpoCalendar` | 달력 렌더 | widget 유지 | 대형 블록(187줄) |
| `DashboardCard`/`DashboardCardGroup` | 지표 카드 | **Group이 Card를 직접 import**하는 위반 → 한 slice로 합치거나 Card를 shared로 | 같은 레이어 slice 간 import 금지 |
| `EmptyState` | 빈 상태 + 필터 초기화 버튼 | 범용 부분은 `shared` 후보. 그러나 **스타일 미적용 결함** 해결 선행(D-8) | 업무 비종속. 현재 사용처 `InvestmentHistoryPage` 1곳만 → 섣불리 shared로 올리지 않음 [미정] |

### 4.4 pages / shared / app 후보

| 모듈 | 현재 책임 | 제안 |
|---|---|---|
| `pages/InvestmentHistoryPage` | 필터 상태·reset·빈 상태 분기·조합 | 조합 + 지역 상태는 page에 적절. 중복 필터 훅 정리 |
| `pages/DashboardPage` | 탭별 쿼리 4개 + 스위치 + 렌더 + `as` 단언 | **타입 교정 + 탭별 render 순수 함수 분리**. view-model 훅 기계적 생성 금지 |
| `pages/IpoCalendarPage` | 조회 + 로딩·오류·빈 분기 | 현 구조 적절. 변경 최소 |
| `pages/SubscriptionListPage` | 조회 + 로딩·오류 + 필터 | 현 구조 적절 |
| `pages/ProfitViewPage` | 모드 토글 + 투자자 조회(entity 직접 호출) + 2개 쿼리 | feature 훅 경유로 경계 정리 |
| `shared/api/*`, `shared/lib/*` | 전송·에러·검증 | 유지. `signal` 전달·검증 유틸 추가 여지 |
| `shared/components/{Button,ErrorMessage,Spinner,ConfirmDialog}` | 범용 UI | **경로·`ConfirmDialog` 유지**(사용자 고정 결정) |
| `shared/components/MainLayout` | skip link + 헤더 + `<main><Outlet/>` | **앱 shell** 후보 → `app/` 레이아웃 (D-6). 도메인 지식 없고 라우터 결합만 있음 |
| `App.tsx` / `main.tsx` / `index.css` | 라우터·Provider·전역 CSS | `app/` 레이어 신설로 이동 후보 (D-5) |

## 5. Epic 17·18과의 충돌 가능성

- Epic 17(공모주 일정 동기화, 진행 중)·Epic 18(증권사 조회 전용 연동, SF-4b·5·6 미착수): **ui를 건드릴 미착수 SF가 생길 가능성**이 있다. 확인된 겹침 후보는 `entities/ipo`, `features/ipo-calendar`, `widgets/IpoCalendar`, `pages/IpoCalendarPage`(E17·E19 SF-2 산출물), 그리고 E18 UI가 추가할 신규 slice(미정).
- **영향 없음 [확인]**: 이 Epic의 조사는 읽기 전용이다. 구현 단계에서는 `App.tsx`·`main.tsx`·`entities/*/index.ts`·`features/dashboard/**`를 이동·수정하므로 **E17/E18 UI PR과 같은 시기에 병합되면 충돌**한다. 병합 순서를 breakdown에서 정한다.

## 6. 미결정·확인 필요 (사용자 결정 대기)

| # | 항목 | 근거 | 권장 |
|---|---|---|---|
| D-1 | Epic 번호/slug E20 `frontend-architecture-refactor` | roadmap 최대 E19, 충돌 없음 | 승인됨(2026-10-01). roadmap 반영 |
| D-2 | `/` 경로 | 요구 문서 없음 | **현행 `Hello` 유지**(사용자 결정). redirect 만들지 않음 |
| D-3 | Not Found·라우트 오류 UI | 없음. 사용자에게 없던 동작 | **제안만**: spec 4.A에 AC 초안. 별도 승인 후 구현 |
| D-4 | 401 중앙 처리 | 전역 처리 없음, 화면별 문구 | 세션 확인·로그인 redirect·복귀 경로 정책이 정해질 때까지 **도입 보류** |
| D-5 | `app` 레이어 신설 | FSD 요구, 현재 없음 | 신설 권장(진입점·라우터·Provider·전역 CSS만) |
| D-6 | `MainLayout` 위치 | 라우터 `Outlet` 결합 | **app shell 후보** → 실제 책임(skip link·헤더·main) 확인 완료, `app/layouts`로 이동 권장 |
| D-7 | Zod / FSD lint 플러그인 | 런타임 검증 없음(3.9) | **첫 단계 미추가**. 자체 타입 가드·`Promise<T>` 제한 + 경계 테스트로 시작, 부족 시 재판단 |
| D-8 | `EmptyState` 스타일 미적용 | 3.7 | 의도된 스타일을 살리면 **시각 변경**이므로 별도 승인 |
| D-9 | by-period `year`/`month` 무시 | 3.2 | 이 Epic에서 고치지 않음. 별도 Epic/이슈로 분리 제안 |
| D-10 | staleTime 정책 | 전부 기본 0 | 근거 없으면 **기존 동작 유지**. 데이터 종류별 제안은 architecture 6절 |
| D-11 | 필터 URL 동기화 | 요구 문서 없음 | **현행 유지**. 새로고침·공유 복원이 요구사항인지 사용자 확인 후 별도 |
| D-12 | HTML `lang`·title | `lang="en"`, title "ui" | `lang="ko"` 타당. title은 저장소 내 기존 UI명 "투자 관리 시스템"(MainLayout 헤더) 후보 — **새 브랜드 금지**, 사용자 확인 |
| D-13 | 템플릿 CSS 제거 | `#root` 규칙은 전 화면에 적용 중 | **확인된 미사용 선택자(`.counter`, `#social .button-icon`)만 제거**. `#root`·`h1`·토큰·다크 모드는 별도 검토 |
| D-14 | `@/` alias | 없음, 상대 경로+확장자 | 보류. public API 정리 후 경로 길이·변경량 비교 |
| D-15 | React Compiler | 미도입 | 별도 선택 과제. 이 Epic에서 하지 않음 |
| D-16 | 금융 mutation 재시도 | mutation 기본 retry 0 | **유지 원칙**(자동 재시도·이중 제출 금지)을 AC로 고정 |
| D-17 | read model 재배치(`entities/dashboard`·`profit`·`auth`) | 4.1절: 업무 개념이 아닌 조회 집계·폼 DTO | `auth`·`dashboard` 이동 권장, `profit`은 사용처 재확인 후 |
| D-18 | 구현 실행 방식 | 워커 dispatch vs Master 직접 | 별도 승인(외부 dispatch 금지 원칙) |
