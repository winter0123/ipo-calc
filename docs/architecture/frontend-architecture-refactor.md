# 프론트엔드 아키텍처 리팩토링 설계 문서 (frontend-architecture-refactor)

> **Epic**: Epic 20 (후보)
> **분야**: 기획 (Planning) — Phase 0 architecture. **제품 코드 변경 없음. spec 승인 전 구현 착수 금지.**
> **작성일**: 2026-10-01 (Master 직접 작성 — 사용자 지시)
> **근거**: `docs/research/frontend-architecture-refactor.md`(조사), `docs/spec/frontend-architecture-refactor.md`(spec)
> **표기**: "[제안]" = 승인 후 구현, "[보류]" = 이번 Epic에서 구현하지 않는 대안 기록.

## 1. 목표 디렉터리 구조

```
ui/src/
  app/                      # 조합 전용: Provider, 라우터, 레이아웃, 전역 CSS
    main.tsx (또는 src/main.tsx 유지 + app/index 호출 — 아래 주석)
    App.tsx                 # 라우트 14개 (경로·요소 그대로)
    providers/QueryProvider.tsx   # QueryClient 생성·기본 옵션
    layouts/MainLayout/     # shared/components/MainLayout 에서 이동
    styles/index.css
  pages/   <Name>Page/      # 라우트 1개 = page 1개. 조합·지역 상태만
  widgets/ <Name>/          # 독립 대형 UI 블록(표·달력). 같은 레이어 import 금지
  features/<name>/          # 사용자 행위 단위(폼·필터·조회 훅)
  entities/<name>/          # 업무 개념(투자자·계좌·공모주·청약·배정·매도·비용)
  shared/
    api/ (client.ts, apiError.ts)
    components/ (Button, ErrorMessage, Spinner, ConfirmDialog …)   # 경로 유지
    lib/
```

- `src/main.tsx`는 Vite 진입점이라 위치를 그대로 두고 **`app/`만 import**하게 만드는 방법(A안)과 `app/main.tsx`로 옮기고 `index.html`의 `src`를 바꾸는 방법(B안)이 있다. **A안 권장**(`index.html` 변경 없음, 롤백 단순). spec A-1의 "진입점 경로" AC는 A안이면 "`index.html` 변경 없음"으로 대체된다.
- `app`은 다른 레이어를 모두 import할 수 있고 **어떤 레이어도 `app`을 import하지 않는다.**
- `.gitkeep`은 전부 제거(실파일이 있는 폴더). 빈 폴더만 필요하면 그때 추가.

## 2. 레이어 규칙

| 규칙 | 내용 | 검사 |
|---|---|---|
| 방향 | app → pages → widgets → features → entities → shared 만 허용, 역방향 금지 | `rg` 스크립트(7절) |
| 같은 레이어 | slice는 같은 레이어 다른 slice를 import하지 않는다(`widgets/DashboardCardGroup`→`DashboardCard` 해소 포함) | 〃 |
| public API | 각 slice는 `index.ts`로 외부에 노출할 것만 export. 바깥은 `../entities/investor`처럼 slice 루트만 import, 세그먼트 파일(`…/api-client.ts`) 직접 import 금지 | 〃 |
| 테스트 | 테스트 파일은 같은 slice 안에서 내부 파일 직접 import 허용(public API 검사 제외) | |
| `shared` | 업무 지식 없음. `shared/components`는 이름 유지 | |
| 이동 금지 | `shared/components/ConfirmDialog/`, `shared/api/client.ts` | |

**예외 기록**: `widgets/InvestmentHistoryTable`이 feature 훅(`useInvestmentHistoryDetail`)으로 서버 상태를 읽는 것은 방향상 허용(widgets → features)이므로 유지하고, 이 사실을 `guide-frontend.md`에 명시한다.

## 3. slice 이동 판단 (조사 4절 결론)

| 모듈 | 결론 | 근거 |
|---|---|---|
| `entities/investor, securities-account, securities-firm, ipo, allocation, sale, cost` | 유지 + `index.ts` | 업무 개념, 재사용 |
| `entities/subscription` | 유지. `getInvestmentHistory`/`InvestmentHistoryItem`은 **read model**로서 `features/investment-history` 소유로 이동 여부를 구현 SF에서 사용처 재집계 후 결정 [제안, 승인 D-17] | 응답이 청약·배정·매도 합성 조회 |
| `entities/dashboard` | `features/dashboard`로 이동 [제안, D-17] | 저장·변경되지 않는 화면용 집계(spec D-D-002). 사용처: `features/dashboard` + `widgets/DashboardCardGroup` |
| `entities/profit` | `features/profit-view`로 이동 검토 [제안, D-17] | 사용처 1곳. 단 매도 등록 성공 후 invalidation 키가 공유되므로 키는 feature 공개 API로 노출 |
| `entities/auth` | `features/login`으로 이동 [제안, D-17] | 타입만 있음 |
| `widgets/DashboardCardGroup` | `DashboardCard`를 같은 slice 내부 파일로 흡수하거나 한 slice(`DashboardCards`)로 통합 | 같은 레이어 import 금지 |
| `widgets/EmptyState` | 현위치 유지(사용처 1곳). 사용처가 2곳 이상 생기면 `shared/components` 후보 | 섣불리 shared 승격 금지 |
| `MainLayout` | `app/layouts`로 이동 | 조사 4.4 |

이동은 **한 SF에 한 slice**만 한다(롤백 단위).

## 4. Query 설계

### 4.1 Query key factory

- 위치: 키를 **소유하는 slice**의 `queryKeys.ts`. 배열 리터럴을 호출부에서 직접 쓰지 않는다.
- 규칙: 첫 요소는 **kebab-case 고정 네임스페이스**(예: `'investors'`, `'securities-accounts'`, `'investment-history'`, `'dashboard'`), 이후 요소는 구조화된 값. 변수 모양은 prefix 매칭에 맞춰 **앞쪽이 넓은 범위**.

```ts
// 예시 (제안). 정의는 implementation SF에서.
export const investmentHistoryKeys = {
  all: ['investment-history'] as const,
  list: (filters: InvestmentHistoryFilters) => [...investmentHistoryKeys.all, 'list', filters] as const,
  detail: (id: number) => [...investmentHistoryKeys.all, 'detail', id] as const,
};
export const dashboardKeys = {
  all: ['dashboard'] as const,
  summary: () => [...dashboardKeys.all, 'summary'] as const,
  byInvestor: () => [...dashboardKeys.all, 'by-investor'] as const,
  byFirm: () => [...dashboardKeys.all, 'by-securities-firm'] as const,
  byPeriod: (unit: 'month'|'year', year?: number, month?: number) =>
    [...dashboardKeys.all, 'by-period', unit, year, month] as const,
};
```

- 키 문자열 변경은 캐시 key 자체의 변경이라 **사용자 동작 영향 없음**(프로세스 내 캐시이며 영속화 없음 — `persistQueryClient` 사용 없음은 `rg`로 확인, 12절).
- `dashboardKeys.all` 한 번의 `invalidateQueries`로 대시보드 전체 갱신이 가능해 B-4 불일치가 구조적으로 사라진다. 현재 `useDashboardTabs`의 "현재 탭만 갱신" 의미는 `dashboardKeys.byPeriod(unit)` prefix 사용으로 보존.
- **사용처 정합**: 투자 내역을 바꾸는 mutation(매도·비용·배정 등)이 invalidate하는 키는 factory를 통해 명시 매핑(4.4절).

### 4.2 staleTime 정책 [제안 — 근거 없이 전역 적용 금지]

현재 전부 기본값 0(= 마운트·포커스 시 항상 refetch). 변경은 **체감 동작 변화**이므로 데이터 종류별 제안만 남기고 기본은 **현행 유지**.

| 데이터 | 변경 주기 | 제안 | 기본 결정 |
|---|---|---|---|
| 증권사 카탈로그(`securities-firms`) | 거의 불변(시드) | 길게(예: 수 분) 가능 | 현행 유지, 측정 후 |
| 투자자·증권계좌 목록 | 사용자 등록 시에만 | 중간 | 현행 유지 |
| 공모주 일정(`ipos`) | E17 동기화 배치 주기 | 동기화 주기 이하 | 현행 유지 |
| 투자 내역·수익·대시보드 집계 | 등록·매도·비용 입력 직후 즉시 반영 필요 | **0 유지**(+ mutation 후 명시 invalidation) | 현행 유지 |

결론: **이번 Epic은 staleTime 값을 바꾸지 않는다**(B-8에서 "기본값과 같은 명시 0"만 제거). 값 변경은 별도 승인(D-10).

### 4.3 retry / signal

- `QueryClient` 기본: `queries.retry = (failureCount, error) => !(error instanceof ApiError && error.status >= 400 && error.status < 500) && failureCount < 3`. 401/403/404/400 즉시 실패. 네트워크·5xx는 기본 3회 유지.
- `mutations.retry`는 **설정하지 않는다(기본 0)** — 금융 등록 요청의 이중 제출 방지(spec D-16).
- `signal`: `queryFn: ({ signal }) => fetchX(args, signal)` 형태로 api-client 함수에 **선택 인자** 추가 → `apiFetch(path, { signal })`. mutation은 해당 없음. AbortError는 TanStack이 내부 처리하므로 별도 UI 처리 없음(테스트로 확인).
- `ApiError`(`shared/api/apiError.ts`) 재사용. 새 오류 클래스 만들지 않음.

### 4.4 invalidation 매핑 (mutation → 갱신 대상)

`rg invalidateQueries`로 확인한 **현재** 상태(2026-10-01, 테스트 제외):

| mutation | 현재 invalidate | 비고 |
|---|---|---|
| 청약 등록 | `['investmentHistory']` | |
| 배정 등록 | `['investmentHistory']` | |
| 매도 등록 | `['investmentHistory']` | |
| 비용 등록 | `['costs']` (쿼리가 없는 죽은 키) | 사실상 갱신 없음 — 화면 재마운트 시 staleTime 0 refetch에 의존 |
| 투자 내역 페이지 "새로고침/필터 초기화" | `['investmentHistory']` | `InvestmentHistoryPage` 직접 호출(죽은 훅과 중복) |
| 투자자·증권계좌·로그인·가입 | invalidation 없음 | 목록이 재마운트 시 refetch |

리팩토링(B-2~B-5)은 **위 "현재"를 factory 키로 그대로 옮기는 것까지**가 동작 불변 범위다. 아래는 **동작 변화**라서 별도 승인 없이 넣지 않는다.

| 제안(동작 변화, 승인 필요) | 이유 | 영향 |
|---|---|---|
| 매도·비용·배정 등록 후 `profit`·`dashboard` 키도 invalidate | 현재는 다른 화면으로 이동해 재마운트되어야 갱신 | 같은 마운트 상태에서 수치가 즉시 갱신됨(금융 수치 표시 시점 변화) |
| 비용 등록의 `['costs']`를 `investmentHistory`로 교체 | 죽은 invalidation | 비용 등록 직후 투자 내역 갱신 |

→ SF-6은 "현재 동작 그대로 옮기기"만 하고, 위 두 항목은 **D-19로 승인 요청**한다(spec 11절에 추가).

## 5. 라우트·app 설계

- `App.tsx`(라우터)는 경로·요소를 그대로 `app/App.tsx`로 이동. `MainLayout`은 `app/layouts`.
- `/` = `Hello` 유지(사용자 결정).
- **[제안] Not Found**: `{ path: '*', element: <NotFoundPage/> }`를 `MainLayout` 하위에 추가. `NotFoundPage`는 `pages/NotFoundPage`. 문구·링크 대상(`/dashboard`)은 승인 시 확정.
- **[제안] 라우트 오류 UI**: 현재 `main.tsx`의 `<BrowserRouter>` + `App.tsx`의 `<Routes>`(data router 아님)이므로 `errorElement`를 쓸 수 없다. 선택지는 (a) 루트에 **자체 구현 클래스 ErrorBoundary**(새 의존성 없음, 약 30줄 + 테스트) (b) `createBrowserRouter` 전환(라우팅 구조 전체 변경이라 위험 큼). **(a) 권장**, 별도 승인 때 비용 재평가.
- 인증 보호 라우트는 **추가하지 않는다**(사전 승인 사항).

## 6. 401 중앙 처리 — 대안 기록 [보류, 구현 제안 아님]

| 대안 | 동작 | 장점 | 위험 |
|---|---|---|---|
| Z0 현행 | 화면별 오류 문구 | 동작 불변 | 세션 만료 시 화면마다 일반 오류로 보임 |
| Z1 전역 redirect | 401 → `/login` 이동 | 일관 | 로그인 화면의 잘못된 비밀번호 401과 구분 필요, 복귀 경로·입력 중 폼 유실, 로그인 전 공개 경로(`/login`·`/signup`) 예외 |
| Z2 `QueryCache.onError` 안내 | 401이면 "다시 로그인" 안내 배너만 | 동작 변화 작음 | 안내 UI 위치·문구 신규 설계 필요 |

결정 전 필요한 정보: 세션 만료 시간, `/auth/me`류 세션 확인 API 유무(백엔드 계약에서 확인 필요 — **미확인**), 로그인 후 복귀 경로 요구 여부. **결정 전까지 Z0 유지.**

## 7. 런타임 검증·경계 검증 — 새 의존성 없는 대안

- **계약 오류(3.1)는 런타임 검증이 없어서 생겼다.** Zod는 첫 단계에서 도입하지 않고 아래로 대응한다.
  1. 타입을 백엔드 DTO와 일치시키고(B-1), `satisfies`로 테스트 fixture를 타입 검증.
  2. 계약 테스트: 백엔드 `docs/spec/*` 샘플 JSON을 fixture로 두고 api-client가 그 모양을 반환한다고 가정하는 타입 테스트(`expectTypeOf`는 vitest 기본 제공).
  3. 금융 수치 필드는 화면 표시 포맷 함수 앞에서 `Number.isFinite` 같은 최소 방어가 이미 있는지 구현 시 확인(없으면 추가하지 않고 별도 제안).
- Zod가 필요해지는 기준(재판단 트리거): 같은 계약 불일치가 다시 발생하거나, 런타임 오류 보고가 추적되는 경우.
- **FSD lint**: `eslint-plugin-boundaries` 등은 새 의존성이므로 미도입. 대안으로 저장소 스크립트(`ui/scripts/check-fsd.sh` 또는 vitest 테스트 `fsd-boundaries.test.ts`)가 `rg`/파일 읽기로 (a) 역방향 import, (b) 같은 레이어 slice 간 import, (c) 세그먼트 직접 import를 검사. **스크립트를 `pnpm run lint`에 묶을지는 구현 SF에서 결정**(package.json script 추가는 의존성이 아니므로 허용, 단 설정 파일 변경이라 PR에 명시). 테스트로 두는 방식이 기존 검증 명령(`pnpm run test`)에 자동 포함되어 가장 작다 — **권장**.
  - 한계: 정규식 기반이라 동적 import·alias(`@/`)는 못 잡는다(현재 alias 없음).

## 8. 접근성 설계

- 폼 공통 패턴(필드): `aria-invalid={errors.x ? 'true' : undefined}`, `aria-describedby={errors.x ? 'x-error' : undefined}`. 에러 요소는 `id="x-error"`로 조건부 렌더(`PeriodSelector` 기존 패턴).
- 폼 수준 오류: `<div role="alert">`(mutation 실패 문구). `aria-live="polite"`와 중복 지정하지 않음.
- 새 공통 컴포넌트(`FormField` 등)는 **만들지 않는다**. 27+37곳을 수정하되 호출부마다 작은 diff로 처리(3줄 중복이 추상화보다 낫다). 폼별 PR 분할(9개 파일 → 2~3 SF).
- `<tr role="row" tabIndex onClick onKeyDown>`(InvestmentHistoryRow)는 lint 규칙상 통과하지만 별도 검토 대상 — 이번 Epic에서 구조 변경하지 않고 기록만.

## 9. CSS/HTML 설계

- `index.html`: `lang`, `title`만 변경(승인 후).
- `index.css`: `.counter`, `#social .button-icon`만 삭제. `#root`, 토큰, `h1`, 다크 모드는 **그대로**.
- CSS 모듈 전환·클래스 재작성은 하지 않는다.

## 10. 이동 순서와 롤백 단위

원칙: **위험 낮은 순, 한 SF = 한 PR = 한 커밋 그룹 = `git revert` 1회로 롤백.** 이동(rename)과 내용 수정은 **다른 PR**로 분리(blame·리뷰 보존).

| 순서 | 묶음 | 위험 | 롤백 |
|---:|---|---|---|
| 1 | 타입 교정(B-1, C-1 일부) | 낮음(타입·캐스트 제거, 동작 불변) | revert |
| 2 | 죽은 훅·`.gitkeep`·미사용 CSS·오래된 주석(C-4, C-7, E-3, B-10) | 낮음 | revert |
| 3 | a11y lint 규칙 복원(SD-1) | 낮음(현재 0건) | revert |
| 4 | `lang`/title(E-1·E-2) | 낮음(문구 승인 필요) | revert |
| 5 | 쿼리 키 factory + 중복 훅 통합 + invalidation 정합(B-2~B-5, B-8) | 중간 | revert. 재현 테스트 선행 |
| 6 | retry·signal(B-6, B-7) | 중간 | revert |
| 7 | 폼 접근성(SD-2~SD-5) 폼 그룹별 | 중간(문구·role 변경) | 그룹별 revert |
| 8 | public API `index.ts` + 경계 검사 테스트(B-11, T-4) | 중간(import 경로 다수) | revert |
| 9 | slice 재배치(D-17), widgets 해소(C-5) | 중~높음(경로 이동) | slice당 revert. E17/E18 UI PR과 병합 순서 조정 |
| 10 | app 레이어 이동(A-1, A-2) | 중~높음 | revert |
| 11 | Dashboard/페이지 분리(C-1~C-3, C-6) | 중간 | revert |
| 12 | (별도 승인) Not Found·라우트 오류 UI | — | — |
| 13 | `docs/guide-frontend.md` 갱신(DOC-1) | 낮음 | 각 SF에서 해당 절 갱신 + 마지막 일괄 점검 |

## 11. 위험과 대응

| 위험 | 대응 |
|---|---|
| 이동 PR이 E17·E18 UI PR과 충돌 | 이동 SF(9·10)는 E17/E18 UI 미병합 구간을 확인하고 착수, 충돌 시 진행 중 PR 병합 후 rebase |
| 키 factory 도입 중 invalidation 누락 → 화면 갱신 안 됨 | SF 5 선행 조건: 현행 invalidation 일람을 테스트로 고정 |
| aria/role 변경이 기존 테스트 셀렉터를 깨뜨림 | 폼별 PR, `getByRole('alert')` 기반으로 테스트 갱신 |
| 회귀를 테스트가 못 잡음(훅 mock 중심) | 계약 타입 테스트·키 factory 테스트 추가(T-1·T-2) |
| 문서와 코드 불일치 | 각 SF가 해당 절의 `guide-frontend.md`를 함께 갱신 |

## 12. 미확인 (구현 전 확인)

- (확인 완료) `persistQueryClient` 사용 없음 — `rg persistQueryClient ui/src` 결과 0건(키 이름 변경은 프로세스 내 캐시에만 영향).
- 세션 확인 API 유무(401 정책 결정용).
- `h1` 전역 스타일이 각 페이지 모듈 CSS에서 덮이는 범위(E-4 판단용).
