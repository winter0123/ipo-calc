# frontend 작업 가이드

> **누가 읽는가**: ① Master가 frontend 워커의 task spec을 쓸 때(검증 명령을 **여기서 그대로 복사해** spec에 넣는다 — `typecheck`만이 아니라 `build`까지 포함해야 한다), ② frontend 워커가 작업 전에.
>
> 공통 규칙은 `AGENTS.md`(6.0절 구조) → `docs/project-agent-guide.md` → `docs/roadmap.md` → `docs/current-status.md` 순서로 먼저 읽는다. spec 형식은 `docs/worker-spec-template.md`를 따른다.

## 1. 기술 기준

| 영역 | 기준 |
|---|---|
| Frontend | React 19, TypeScript, Vite, TanStack Query, React Hook Form, FSD 유사 구조 |
| 패키지 관리 | pnpm |

`frontend`는 소스 폴더가 아니라 Orca 작업공간 이름이다. 프론트엔드 앱 소스는 반드시 `ui/`에 둔다.

## 2. 검증 명령

실제 설정 파일(`ui/package.json` 등)을 먼저 읽고 다음 명령을 쓴다.

```bash
cd ui
pnpm run test
pnpm run lint
pnpm run typecheck
pnpm run format:check
pnpm run build
```

`typecheck`만으로 끝내지 않는다. `pnpm run build`도 필수다.

> **정정 (2026-09-29)**: 위 문장의 기존 이유는 더 이상 성립하지 않는다. `typecheck`가 한때 `tsc --noEmit`이었을 때, `ui/tsconfig.json`이 `files: []` + `references`만 있는 solution-style 구성이라 `tsc --noEmit` 단독 실행은 **0개 파일을 검사하는 no-op**이었다. 그래서 타입 체크는 `pnpm run build`의 `tsc -b`가 대신 수행하고 있었다.
>
> `typecheck`는 이제 `tsc -b`로 실행되며, `references`를 따라가 `tsconfig.app.json`·`tsconfig.node.json` 양쪽을 실제로 검사한다(두 파일 모두 `noEmit: true`라 산출물 출력은 없다). **그래서 `build`를 생략해서는 안 된다** — 이유가 바뀌었을 뿐이다: `build`는 같은 `tsc -b`에 더해 `vite build`(번들링·에셋 처리)까지 수행한다. 타입 오류 확인은 `typecheck`로 충분하고, 번들까지 확인하려면 `build`가 필요하다.

## 3. 프론트엔드 구현 규칙

- `pnpm`만 사용한다. `pnpm-lock.yaml`을 커밋하며 `package-lock.json`을 만들지 않는다.
- ESLint, Prettier, TypeScript strict를 유지한다. 들여쓰기는 2칸, 탭 금지다.
- FSD 레이어는 반드시 `src/{app,pages,widgets,features,entities,shared}/`처럼 `src` 바로 아래 형제로 둔다. `entities`, `features`, `pages`, `shared`, `widgets`를 `src/app/` 아래에 중첩하지 않는다.
- 의존 방향은 `app → pages → widgets → features → entities → shared`다. 하위 레이어가 상위 레이어를 참조하거나 순환 참조하지 않는다.
- `app/`은 슬라이스 없이 세그먼트로만 구성한다(`App.tsx`, `AppProviders.tsx`, `queryClient.ts`, `ErrorBoundary.tsx`, `layouts/`, `styles/`). `shared/components`·`shared/components/ConfirmDialog/`·`shared/api/client.ts` 경로는 유지한다.
- 서버 상태는 TanStack Query, UI 상태는 지역 상태 또는 꼭 필요한 전역 상태로 구분한다. `useEffect`를 파생 상태·단순 계산·이벤트 처리 대체 수단으로 남용하지 않는다.
- 모든 화면은 처음부터 모바일 웹을 지원한다. 터치 대상은 최소 44×44px, `<meta name="viewport" content="width=device-width, initial-scale=1">` 유지, 가로 스크롤 금지, 640px/768px/1024px에서 레이아웃을 확인한다.
- 화면 작업은 로딩·오류·빈 상태, 키보드 접근성, 의미 있는 컴포넌트·통합·E2E 테스트를 함께 다룬다.

## 4. 공용 인프라 재사용 (Epic 13/14에서 확보)

- `ui/src/shared/api/client.ts`의 `apiFetch` — 4xx/5xx 응답의 JSON body에서 message 필드를 읽어 그대로 전달한다(Issue #134 수정 완료). Register 폼의 `getFormErrorMessage()` 조건문이 이미 "서버 메시지가 있으면 그대로 표시" 구조라 대부분 추가 수정이 필요 없다 — 새 폼 작성 시 이 패턴을 그대로 따른다.
- `ui/src/shared/components/ConfirmDialog/` — 기존 재사용, 중복 생성 금지.
- Epic 14의 청약 목록 화면 패턴 — 백엔드 API가 아직 없을 때 계약(spec/architecture 문서)만으로 프론트를 먼저 구현하고 mock으로 검증한 뒤, 실제 백엔드가 나오면 통합하는 방식. 프론트/백엔드가 서로 다른 Epic 순서로 진행될 때 병목을 줄이는 데 재사용 가능.

## 5. FSD 경계·공개 API (Epic 20)

- `entities`·`features`·`widgets`의 슬라이스는 `index.ts`(공개 API)로만 외부에 노출한다. 다른 슬라이스·상위 레이어는 `index.ts`를 거쳐 import하고 내부 파일 경로를 직접 쓰지 않는다. 같은 레이어의 다른 슬라이스 import는 금지다(필요하면 상위 레이어에서 조합하거나 공유 코드를 하위 레이어로 내린다).
- `app → pages`는 라우트 진입점이라 페이지 컴포넌트를 직접 import한다(페이지 `index.ts` 불필요). 슬라이스를 가로지르는 통합 테스트는 `app/`에 둔다.
- `src/fsd-boundaries.test.ts`가 위 규칙(레이어 방향·슬라이스 격리·공개 API)을 검사한다. 테스트 파일은 mock 핸들 확보를 위해 내부 `api-client.ts`를 직접 import할 수 있어 공개 API 규칙에서만 제외된다(레이어 규칙은 적용). 새 의존성 없이 `import.meta.glob(?raw)`로 구현했다.
- 대시보드 조회 모델(`DashboardSummary` 등)과 훅은 `features/dashboard`, 쿼리 키(`dashboardKeys`)만 `entities/dashboard`에 둔다. 투자 내역 읽기 모델은 `entities/subscription`에 두고(여러 feature·widget이 공유), 로그인 전용 타입은 `features/login/types.ts`에 둔다.

## 6. TanStack Query 규칙 (Epic 20)

- 쿼리 키는 엔티티별 `queryKeys.ts` factory(`investorKeys`, `investmentHistoryKeys`, `profitKeys`, `dashboardKeys` …)만 쓴다. 네임스페이스는 kebab-case이고 하위 키는 상위 키의 prefix다. 문자열 키를 컴포넌트·훅에 직접 쓰지 않는다.
- 조회 훅은 엔티티에 하나만 둔다(`useInvestors`, `useInvestmentHistory`, `useIpos` …). feature에 복제 훅을 만들지 않는다. 모든 GET `api-client` 함수는 선택 인자 `signal?: AbortSignal`을 받고 `queryFn: ({ signal }) => …`로 넘긴다.
- 등록 mutation 성공 시 `investmentHistoryKeys.all`, `profitKeys.all`, `dashboardKeys.all`을 prefix invalidate한다(`app/mutation-invalidation.test.tsx`).
- 재시도 정책은 `app/queryClient.ts`: 4xx(`ApiError.status`)는 즉시 실패, 그 외는 최대 3회 미만 재시도. mutation 자동 재시도는 중복 제출 방지를 위해 끈 채(기본 0) 유지한다. `staleTime`은 기본값(0)을 유지한다.
- 401은 `apiFetch`가 `ApiError(401)`로 던지고, `app/AppProviders`가 쿼리·mutation 캐시 `onError`에서 한 번에 처리한다: 캐시를 비우고 `/login`으로 `replace` 이동하며 `state.from`에 복귀 경로를 남긴다(`/login`·`/signup`에서는 이동하지 않음). 로그인 요청 자체의 401(잘못된 자격 증명)은 `useLogin`의 `meta.skipUnauthorizedRedirect`로 제외한다. `LoginForm`은 성공 후 `state.from`이 앱 내부 경로(`/`로 시작, `//` 제외)일 때만 그곳으로 복귀한다.

## 7. 폼·접근성 규칙 (Epic 20)

- `aria-invalid`는 필드 유효성 오류(`errors.X`)에만 건다. 서버(mutation) 오류에는 걸지 않는다.
- `aria-describedby`는 가리키는 요소가 실제로 렌더될 때만 지정한다.
- 폼 단위 오류 메시지는 `role="alert"`로 렌더한다.
- 상태 코드 분기는 문자열 매칭이 아니라 `toErrorMessage(error, { byStatus, fallback })`를 쓴다.
- `jsx-a11y/no-static-element-interactions`, `click-events-have-key-events`는 error다. 새 테스트는 `fireEvent`보다 `@testing-library/user-event`를 쓴다(기존 테스트는 점진 이전).
- 존재하지 않는 경로는 `NotFoundPage`(MainLayout 안), 렌더 중 예외는 `app/ErrorBoundary`가 처리한다. `/`는 `Hello`를 유지하고 redirect하지 않는다.
- 목록 필터는 URL 쿼리가 단일 출처다(`InvestmentHistoryPage`: `investorId`·`securitiesAccountId`·`ipoId`, `SubscriptionListPage`: `status`). `useSearchParams`로 읽고 쓰며 변경은 `replace`로 history를 쌓지 않는다. 한 번의 사용자 동작에서 여러 키를 바꿀 때는 `setSearchParams`를 연속 호출하지 말고 한 번에(패치 객체로) 바꾼다 — React Router는 함수형 갱신을 큐잉하지 않는다.
- 서버 응답 런타임 검증은 Zod로 한다(파일럿: `features/dashboard`). 스키마를 단일 출처로 두고 타입은 `z.infer`로 파생하며, `api-client`에서 `schema.parse`로 경계에서 검증한다. 계약과 다르면 `ZodError`로 실패해 TanStack Query의 error 상태로 흐른다. 다른 feature로 넓힐 때도 같은 모양(`types.ts`에 스키마, `api-client.ts`에서 parse)을 따른다. 백엔드가 null을 줄 수 있는 필드는 `.nullable()`로 명시한다.
- React Compiler가 켜져 있다(`vite.config.ts`, Vitest도 상속). react-hook-form의 `formState`는 제자리 변경 객체라 메모이제이션과 충돌하므로 `useForm`을 쓰는 컴포넌트는 함수 첫 줄에 `'use no memo'`를 둔다. 수동 `useMemo`/`useCallback`은 새로 추가하지 않는다.
- 페이지는 `app/App.tsx`에서 `React.lazy`로 불러오고, `Suspense`는 `MainLayout`의 `<Outlet />` 주변에 둔다(페이지 전환 중에도 레이아웃 유지). 새 페이지도 같은 방식으로 추가하고, 라우트 테스트는 `findBy*`로 기다린다.
- 폼 오류 영역은 `role="alert"`로 표시한다. E2E·접근성 스캔 선택자(`tools/e2e/e2e.mjs`의 `errorTexts`)도 이에 맞춰져 있으므로 마크업을 바꾸면 `pnpm run e2e`를 함께 돌린다.
