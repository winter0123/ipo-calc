# 작업 로그: 프론트엔드 아키텍처 리팩토링 (frontend-architecture-refactor, Epic 20)

## 2026-10-01 — Master 직접 구현 (사용자 지시)

사용자가 "너가 직접 수정", "전부 승인"으로 지시해 워커 dispatch 없이 Master가 `planning-e20` 작업공간에서 `ui/`를 직접 수정했다. commit·push·PR은 아직 승인되지 않았다.

### D-E20-001: 결정과 범위
- `/`는 `Hello` 유지(redirect 없음). 새 의존성 0건, 백엔드 변경 0건.
- 승인에 따라 포함: A-4 NotFound, A-5 ErrorBoundary, D-8(b) EmptyState CSS 모듈 스타일, D-19 invalidation 확장, D-17 재배치, D-12 `lang="ko"`·title "투자 관리 시스템".
- 1차 보류였던 D-4·D-7·D-9·D-11·D-14·D-15는 "다 진행" 지시로 후속 구현했다(아래 D-E20-006). D-10 staleTime은 현행 유지.

### D-E20-002: FSD 판단 변경
- 대시보드: 조회 모델·API·훅은 `entities/dashboard` → `features/dashboard`로 이동(소비자가 feature뿐). 쿼리 키(`dashboardKeys`)만 `entities/dashboard`에 남겨 mutation feature들이 invalidate에 쓴다.
- `DashboardCard`는 `DashboardCardGroup` 위젯 안으로 흡수(위젯 간 import 제거).
- `entities/auth`(타입뿐, 소비자는 `features/login`)는 `features/login/types.ts`로 이동.
- `entities/profit`은 유지(mutation의 `profitKeys` 의존), 투자 내역 읽기 모델은 `entities/subscription`에 유지(여러 feature·widget 공유).
- `MainLayout`·`App`·`index.css`를 `app/`으로 이동. 슬라이스를 가로지르는 테스트(`mutation-invalidation`, `queryKeys`)는 `app/`으로 이동.
- 모든 `entities/features/widgets` 슬라이스에 `index.ts` 공개 API 추가, 외부 import를 공개 API로 전환. `src/fsd-boundaries.test.ts`가 레이어 방향·슬라이스 격리·공개 API를 검사.

### D-E20-003: 버그 수정과 동작 변화(의도된 것)
- 대시보드 기간 탭 새로고침이 월/연 쿼리 키 prefix 불일치로 무효화되지 않던 문제 수정(`useDashboardTabs.test.tsx`).
- 등록 mutation 성공 시 투자 내역·수익·대시보드 캐시를 함께 무효화.
- 4xx는 재시도하지 않음(그 외 3회 미만). mutation 재시도는 0 유지.
- 폼 `aria-invalid`는 필드 오류에만, 폼 오류 영역은 `role="alert"`. 보이는 문구는 동일.
- `DashboardCard` 고아 `<label>` → `<span>`, 대시보드 내부 `<a>` → `<Link>`, EmptyState `:focus-visible` outline.
- 존재하지 않는 경로 → NotFoundPage, 렌더 예외 → ErrorBoundary.
- `LoginForm`의 `message.includes('Unauthorized')` 분기를 `ApiError.status === 401` 분기로 교체(표시 문구 동일).
- 죽은 코드 삭제: 미사용 필터 훅, `.gitkeep`, 기본 vite/react 에셋, 미사용 CSS.

### D-E20-004: 의도적으로 남긴 것
- `SaleRegisterForm`의 `useEffect`(배정 조회 결과를 RHF 값에 동기화)와 `AllocationRegisterPage`의 redirect effect는 외부 데이터/내비게이션 부수효과라 유지.
- `fireEvent`→`user-event` 이전은 LoginForm만 수행, 나머지는 점진 이전.

### 검증(`ui/`, 2026-10-01)
- test 313 passed(기준선 266), lint·typecheck·format:check·build PASS. axe·E2E는 NOT_RUN(별도 승인 전, 운영/공유 데이터 대상 실행 금지).

### D-E20-006: 보류 항목 후속 구현 (사용자 "다 진행", 새 의존성·백엔드 변경 승인으로 간주)
항목별로 별도 커밋했다.
- **D-14 `@/` alias**: tsconfig `paths` + Vite `resolve.alias`(Vitest는 `mergeConfig`로 상속). 상위 레이어 경계는 FSD 경계 테스트가 `@/`도 해석해 검사한다.
- **D-4 401 중앙 처리**: `QueryCache`·`MutationCache`의 `onError`가 `ApiError(401)`을 잡아 캐시를 비우고 `/login`으로 `replace` 이동(`state.from`에 복귀 경로). `/login`·`/signup`에서는 동작하지 않고, 로그인 실패 401은 `mutation.meta.skipUnauthorizedRedirect`로 제외한다. 복귀 경로는 `/` 시작·`//` 거부로 오픈 리다이렉트를 막는다. 뮤테이션 자동 재시도는 0 유지.
- **D-11 필터 URL 동기화**: 투자 내역(`investorId`·`securitiesAccountId`·`ipoId`)과 청약 목록(`status`) 필터를 URL 쿼리가 단일 출처. 변경은 `replace`. `setSearchParams` 함수형 갱신은 같은 틱에서 큐잉되지 않아 패치 객체 한 번 호출로 처리한다.
- **D-9 by-period 백엔드**: `GET /dashboard/by-period`가 선택 파라미터 `year`(1900~9999)·`month`(1~12)를 반영한다. `year`만 있으면 그 해 매도만, `unit=month`+`month`이면 그 달만, `unit=year`에서 `month`는 무시, 둘 다 없으면 기존과 동일(전체). 잘못된 값은 400. 필터는 순수 함수 `DashboardAggregator.byPeriod(sales, unit, year, month)`에 두고 단위 테스트를 추가했다. **동작 변화**: 대시보드 월별/연도별 탭이 선택한 기간만 보여준다(이전엔 선택과 무관하게 전체 기간).
- **D-7 Zod(파일럿)**: `features/dashboard`의 4개 응답을 `types.ts` 스키마(단일 출처)로 정의하고 `api-client.ts`에서 `parse`한다. 계약 위반은 `ZodError` → Query error 상태. 다른 feature는 이 모양으로 점진 확대(미적용).
- **D-15 React Compiler**: `babel-plugin-react-compiler` + `@rolldown/plugin-babel`(plugin-react v6의 `reactCompilerPreset`). Vitest도 같은 설정을 상속해 컴파일된 코드로 테스트한다. 전체 적용 시 `SaleRegisterForm` 테스트 1건이 실패했다 — react-hook-form의 `formState`가 제자리 변경 객체라 메모이제이션과 충돌. RHF를 쓰는 폼 8개에 `'use no memo'`를 붙여 제외했다. 번들은 약 +48kB(486→535kB)로 늘고 Vite의 500kB 청크 경고가 떴으나, 아래 D-E20-007의 라우트 코드 스플리팅으로 해소했다.
- **검증**: 프런트 `test` 329 PASS, `lint`·`typecheck`·`format:check`·`build` PASS. 백엔드 `./gradlew test checkstyleMain checkstyleTest --rerun-tasks` PASS(로컬 Postgres). axe/E2E는 NOT_RUN.

### D-E20-007: 후속 정리 (2026-10-01)
- **라우트 코드 스플리팅**: `app/App.tsx`의 페이지를 `React.lazy`로, `MainLayout`의 `<Outlet />`을 `Suspense`(폴백 `role="status"` "불러오는 중...")로 감쌌다. 메인 번들 535→284kB, 청크 경고 해소. 레이아웃은 페이지 전환 중에도 유지된다. `App.test.tsx`는 `findBy*`로 전환.
- **E2E-11·수익 합계 NPE(D-E20-009)**: D-9 기간 필터를 직접 겨냥한 E2E-11을 추가했다(11/11 PASS). 또 Zod 확대 중 매도 금액이 DB nullable임을 보고 `ProfitCalculationService.calculateForInvestor`의 `reduce(0L, Long::sum)`이 null에서 NPE를 내는 것을 재현 테스트로 확인(500)하고, null 필터로 고쳤다. 개별 `ProfitDto.saleAmount`는 null을 그대로 두며 UI는 `-`로 표시한다. 이 백엔드 수정은 동작 변화(500 → 200)다.
- **E2E·axe 재실측**: E2E 10/10, axe 12개 화면 위반 0건(상세 `docs/qa/e2e-report.md`). 첫 실행의 E2E-3·4·6 실패는 E2E 스크립트가 `[aria-live="polite"]`만 찾는데 E20이 폼 오류를 `role="alert"`로 바꿨기 때문이었다(제품은 정상). 스크립트 선택자를 보강했다.
- **Zod 확대(D-E20-008, 2026-10-01 "묻지말고 계속 진행")**: DB NOT NULL 대조(V2~V10, V14)와 백엔드 DTO 매핑을 확인한 뒤 GET 읽기 모델 6개(`ipo`, `investor`, `securities-account`, `securities-firm`, `subscription`(투자 내역 목록·상세), `profit`)에 확대했다. 대조 결과 UI 타입이 non-null이라 적었지만 실제로 null이 올 수 있는 곳: IPO의 `stockCode`·`roundLabel`·`lower/upperBandPrice`·`fixedPrice`·`offeringShareCount`, 투자 내역의 `ipoName`·`ipoFixedPrice`·매도 `amount/fee/tax/otherCost`, 수익의 `saleAmount`, 증권사의 `externalCode`·`businessRegistrationNumber`, 증권계좌의 `securitiesFirmName`·`accountName`. 타입 체크가 드러낸 null 취약 지점(`InvestmentHistoryDetail`의 `fee.toLocaleString()` 등, `ProfitRow`, 캘린더 가격 표기)은 `-`/생략 표시로 고쳤다(수수료 없는 매도 상세에서 크래시하던 잠재 버그). 테스트 fixture 4개 파일은 실제 DTO와 달라 고쳤다(`investorName`·`securitiesFirmName` 누락, 매도 폼 fixture의 `allocation`이 `AllocationSummaryDto`가 아니라 배정 엔티티 모양이었음). 이전 판단("확대 보류"): 백엔드 응답 DTO가 전부 박싱 타입(`Long` 등)이고, IPO 같은 데이터는 외부 동기화(OpenDART 등)로 채워져 UI 타입이 non-null로 적어 둔 필드(`fixedPrice`, `stockCode` 등)에 실제로 null이 올 수 있다. 엄격한 스키마를 실응답에 걸면 멀쩡한 화면이 ZodError로 깨질 수 있어, DB의 NOT NULL 제약(Flyway)을 먼저 대조해 스키마를 만들어야 한다. 대시보드 파일럿은 백엔드가 원시 `double`/비-null 합계를 만드는 것을 확인한 뒤 적용했다. 후속 후보는 청구 금액 계산이 있는 `entities/profit`, `entities/subscription`(투자 내역).
- **staleTime**: 현행 유지(0). 금융 수치 갱신 지연 위험이 이득보다 크다고 판단해 바꾸지 않았다.
