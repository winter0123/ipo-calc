# 프론트엔드 아키텍처 리팩토링 기획 문서 (frontend-architecture-refactor)

> **Epic**: Epic 20 (후보, 사용자 승인 2026-10-01)
> **분야**: 기획 (Planning) — Phase 0 spec. **제품 코드 변경 없음. 이 문서 승인 전 구현 착수 금지.**
> **작성일**: 2026-10-01 (Master 직접 작성 — 사용자 지시)
> **근거**: `docs/research/frontend-architecture-refactor.md` (이하 "조사")
> **설계**: `docs/architecture/frontend-architecture-refactor.md` / **분해**: `docs/breakdown/frontend-architecture-refactor.md`

## 1. 목적과 원칙

`ui/`의 FSD 경계·서버 상태·접근성·빌드 자산을 **작은 단계로, 근거를 갖고** 정리한다. 사용자가 보는 동작은 바뀌지 않는다.

**고정 제약 (변경 불가)**

- 유지: React 19, TypeScript strict, Vite, TanStack Query, React Hook Form, React Router, pnpm, ESLint, Prettier. **새 의존성 추가 없음.**
- FSD 레이어는 `ui/src/{app,pages,widgets,features,entities,shared}` 형제. 의존 방향 app → pages → widgets → features → entities → shared. `processes` 없음.
- `shared/components` 경로, `shared/components/ConfirmDialog/`, `shared/api/client.ts`는 그대로(이름 변경·중복 생성 금지).
- 서버 상태 = TanStack Query, 폼 = RHF, 일시 UI 상태 = 최소 지역 상태. `useEffect`로 파생 상태·이벤트 처리 금지.
- **변경 금지**: 금융 계산·세금/수수료 규칙, API 요청·응답 의미, 기존 라우트의 사용자 동작, 백엔드.
- 사용자 결정(2026-10-01): `/`는 현행 `Hello` 유지, redirect 만들지 않음. Not Found·라우트 오류 UI는 이 문서에서 **범위와 AC만 제안**하고 구현은 별도 승인.

## 2. 사용자 가치

| 이해관계자 | 가치 |
|---|---|
| 사용자(투자자 본인) | 화면 동작은 그대로이면서 (a) 스크린리더가 폼 오류를 정확히 읽고 (b) 잘못된 대시보드 탭 갱신이 줄고 (c) `lang`·제목이 정확하다 |
| 개발자·워커 | 경계가 지켜지는 구조, 타입이 계약과 일치, 중복 제거로 Epic 17·18 UI 추가 시 충돌·회귀 감소 |
| Master | slice public API·query key 규칙이 문서화되어 PR 리뷰 기준이 명확 |

## 3. 범위

- 포함: Stream A~E와 테스트·문서 갱신(4~9절).
- 비범위: 백엔드 전부, 신규 기능, 401 중앙 처리 구현, Zod 도입, FSD lint 플러그인, `@/` alias, React Compiler, 필터 URL 동기화, 기간 조회 API 파라미터 수정(조사 D-4, D-7, D-9, D-11, D-14, D-15).

## 4. Stream A — app/router

| 항목 | 내용 | 승인 |
|---|---|---|
| A-1 | `ui/src/app/` 신설: `main.tsx`의 Provider·`App.tsx` 라우터·전역 CSS(`index.css`)를 `app/`으로 이동. **라우트 14개 경로·요소는 그대로** | 본 spec 승인 시 |
| A-2 | `MainLayout`(skip link·헤더·`<main id="main-content"><Outlet/></main>`)을 `app/layouts`로 이동. 동작 불변 | 본 spec 승인 시 (조사 D-6) |
| A-3 | `/`는 `Hello` 유지 | 사용자 결정 완료 |
| A-4 | **(제안 — 구현 별도 승인)** Not Found(`*`) 라우트 | 별도 승인(D-3) |
| A-5 | **(제안 — 구현 별도 승인)** 라우트 오류 UI(`errorElement`) | 별도 승인(D-3) |

**AC**

- **A-1/A-2**: Given 기존 14개 경로, When `app/`으로 이동 후 각 경로에 접근, Then 이동 전과 같은 화면이 렌더되고 기존 라우팅 테스트(`App.test.tsx` 등)가 수정 없이(import 경로 외) 통과한다.
- **A-1**: Given 이동 완료, When `pnpm run build`, Then 빌드 성공이며 진입점 `index.html`의 스크립트 경로가 새 위치를 가리킨다.
- **A-3**: Given 사용자가 `/` 접근, When 렌더, Then 이전과 같이 `Hello`가 보이고 redirect가 일어나지 않는다.
- **A-4 (제안)**: Given 정의되지 않은 경로 `/unknown`, When 접근, Then "페이지를 찾을 수 없습니다" 안내와 대시보드로 가는 링크를 보여 주고 `MainLayout` 안에서 렌더된다. 기존 14개 경로는 영향 없다. *이 동작은 현재 없는 것이므로 승인 전 구현하지 않는다.*
- **A-5 (제안)**: Given 라우트 렌더 중 예외, When 발생, Then 앱 전체 빈 화면 대신 오류 안내와 새로고침/대시보드 링크를 보여 준다. *승인 전 구현하지 않는다.*

## 5. Stream B — Query/API

| 항목 | 내용 |
|---|---|
| B-1 | `DashboardByPeriod` 타입을 백엔드 계약(평면 배열 아이템)으로 교정하고 `DashboardPage`의 `as unknown as`를 제거 |
| B-2 | 쿼리 키 규약 단일화: 엔티티별 key factory(`features`/`entities` 내부)로 `['investors']`·`['securitiesAccounts']`·`['investmentHistory', …]`·`['dashboard…']`의 철자·모양 통일 |
| B-3 | 중복 쿼리 훅 통합: `useInvestmentHistory` 2종, `fetchInvestors` 재선언 4곳, 증권계좌 목록 2철자 |
| B-4 | `useDashboardTabs`의 invalidation 키를 실제 쿼리 키와 일치시킨다(먼저 **재현 테스트**) |
| B-5 | 죽은 invalidation(`['costs']`) 정리: 실제로 갱신이 필요한 키(투자 내역·수익)로 교체할지는 현행 동작(재마운트 refetch)을 테스트로 고정한 뒤 결정 |
| B-6 | TanStack Query의 `AbortSignal`을 api-client → `apiFetch`까지 전달(조회 함수 한정) |
| B-7 | `QueryClient` 기본 옵션: 쿼리 `retry`는 `ApiError.status` 4xx면 재시도 안 함. **mutation은 기본 retry 0 유지** |
| B-8 | 잡음 제거: `enabled: true` 재명시, 항등 `select`, 기본값과 같은 `staleTime: 0` |
| B-9 | `staleTime`은 데이터 종류별 제안만 하고 **근거 없이 공통값을 넣지 않는다**(architecture 6절) |
| B-10 | 오래된 주석("Epic 11 백엔드 미구현") 정정 |
| B-11 | `entities/*`에 `index.ts` public API. 이동 대상(`entities/dashboard`, `entities/profit`, `entities/auth`)은 조사 4.1절의 소유 레이어로 재배치(승인 D-17) |

**AC**

- **B-1**: Given by-period 응답 `[{period, totalInvestmentAmount, …}]`, When 기간 탭 렌더, Then `as unknown as` 없이 타입 검사를 통과하고 화면 표시는 이전과 동일하다. `pnpm run typecheck`가 계약 불일치 mock을 **컴파일 오류로** 잡는다(타입 테스트 1건).
- **B-2/B-3**: Given 투자 내역을 본 뒤 매도 등록, When 성공, Then 투자 내역 목록이 이전과 동일한 조건에서 갱신된다(기존 테스트 통과 + 키 factory 단위 테스트).
- **B-4**: Given 기간 탭(월/연)에서 필터 초기화를 눌렀을 때, When `invalidateCurrentTab`, Then 현재 기간 쿼리가 무효화된다(현재 동작과 다르면 재현 결과를 PR에 기록).
- **B-6**: Given 탭 전환 중 이전 요청 진행 중, When 전환, Then 이전 요청이 abort되고 오류 UI는 나타나지 않는다(AbortError는 오류로 취급하지 않음).
- **B-7**: Given 404/403 응답, When 쿼리 실패, Then 재시도 없이 즉시 오류 상태. Given 네트워크 오류·5xx, Then 기본 재시도 유지. Given mutation 실패, Then 자동 재시도 없음(이중 제출 방지).
- **B-8/B-10**: Given 잡음 제거, When 전체 테스트, Then 모두 통과하고 동작 차이 없음.
- **B-11**: Given `entities/x/index.ts`, When 다른 레이어가 import, Then public API 경유이며 세그먼트 파일 직접 import가 0건이다(`rg`로 확인).

## 6. Stream C — 페이지 책임

| 항목 | 내용 |
|---|---|
| C-1 | `DashboardPage`: 탭별 렌더 로직을 순수 컴포넌트로 분리해 탭마다 타입이 좁혀지게 한다. **view-model 훅을 기계적으로 만들지 않는다** |
| C-2 | `DashboardTab` 타입·탭 목록을 한 곳(`features/dashboard`)으로 모은다 |
| C-3 | `ProfitViewPage`의 투자자 목록 직접 호출을 feature 훅 경유로 바꾼다 |
| C-4 | 미사용 훅 `useInvestmentHistoryFilters.ts` 삭제(전체 참조 재검색 후) |
| C-5 | `widgets/DashboardCardGroup`의 `DashboardCard` 직접 import 해소 |
| C-6 | 대시보드 `<a href="/investments">`를 `Link`로 교체(SPA 이동, 전체 새로고침 제거) |
| C-7 | `.gitkeep` 잔존 제거 |

**AC**

- **C-1**: Given 각 탭, When 데이터 로드, Then 이전과 같은 카드·표가 표시되고 `DashboardPage.test.tsx`(mock 모양은 B-1로 교정)가 통과한다.
- **C-4**: Given 전체 저장소 `rg useInvestmentHistoryFilters` 결과가 정의 파일 외 0건, When 삭제, Then 테스트·빌드가 통과한다.
- **C-5**: Given 위젯 간 import, When 정리, Then `widgets/*`의 같은 레이어 slice 간 import가 0건이다.
- **C-6**: Given 대시보드의 "투자 내역 보기" 링크, When 클릭, Then 전체 새로고침 없이 `/investments`로 이동하고 세션이 유지된다. *(현재는 전체 새로고침이지만 세션 쿠키로 로그인 상태는 유지됨 — 사용자 체감 차이는 새로고침 깜빡임·캐시 재사용뿐.)*

## 7. Stream D — 폼·접근성

| 항목 | 내용 |
|---|---|
| SD-1 | `jsx-a11y/no-static-element-interactions`, `click-events-have-key-events`를 `error`로 복원(현재 위반 0건, 구현 시 재확인) |
| SD-2 | `aria-invalid`는 **필드 검증 오류만** 반영 (`!!errors.x`). mutation 오류(`!!error`) 제거 — 8파일 27곳(조사 3.6) |
| SD-3 | `aria-describedby`는 해당 에러 요소가 **렌더될 때만** 부여 — 11파일 37곳(조사 3.6, 항상 렌더되는 대상은 제외). `PeriodSelector` 패턴 재사용 |
| SD-4 | 폼 수준(mutation) 오류는 `role="alert"`로 통일, 필드 오류 문구와 구분 |
| SD-5 | `LoginForm`의 문자열 매칭(`includes('Unauthorized')`)을 `ApiError.status === 401` 판정으로 교체. **표시 문구는 변경하지 않는다** |
| SD-6 | 테스트는 `fireEvent` → `@testing-library/user-event`로 **신규·수정 테스트부터 점진 이관**(이미 설치됨, 일괄 변환 금지) |

**AC**

- **SD-1**: Given `<div onClick>` 샘플, When `pnpm run lint`, Then 오류. Given 현행 코드, Then 위반 0.
- **SD-2**: Given 서버 오류(mutation 실패) 상태, When 폼 렌더, Then 어떤 입력도 `aria-invalid="true"`가 아니다. Given 필드 검증 실패, Then 해당 필드만 `aria-invalid="true"`.
- **SD-3**: Given 오류 없음, When DOM 조회, Then `aria-describedby`가 존재하지 않는 id를 가리키는 입력이 0개. Given 필드 오류, Then 해당 입력의 `aria-describedby`가 렌더된 에러 요소 id와 일치.
- **SD-4**: Given mutation 실패, When 오류 표시, Then `role="alert"` 요소 1개에 동일 문구가 표시된다.
- **SD-5**: Given 401 응답, When 로그인 실패, Then 이전과 같은 문구가 표시된다. Given 서버가 메시지 문자열을 바꿔도 401 처리는 동일하다.
- **한계**: axe(브라우저 기반)·E2E는 이 Epic의 자동 검증에 포함하지 않으며 **NOT_RUN으로 보고**한다. 실행하려면 로컬 서버와 테스트 계정이 필요하므로 별도 승인(테스트 계정 생성·로컬 DB 변경 포함, production·공유 데이터 대상 금지).

## 8. Stream E — CSS/HTML

| 항목 | 내용 | 승인 |
|---|---|---|
| E-1 | `index.html` `lang="ko"` | D-12 승인 |
| E-2 | `<title>`: 저장소 내 기존 UI명 "투자 관리 시스템"(MainLayout 헤더) 후보 — **새 브랜드 금지** | D-12 승인 |
| E-3 | `index.css`의 **확인된 미사용 선택자만** 제거: `.counter`, `#social .button-icon` | 본 spec 승인 시 |
| E-4 | `#root`(폭 1126px·중앙 정렬·`text-align:center`·양옆 테두리)·`h1`·색 토큰·다크 모드 **변경 없음** — 별도 시각 검토 Epic/승인 | D-13 |
| E-5 | `EmptyState.module.css` 미적용 결함: 스타일 적용은 **시각 변경**이므로 (a) 그대로 두고 죽은 CSS만 삭제 (b) 모듈 연결로 의도 스타일 적용 중 선택 | D-8 승인 |

**AC**

- **E-1/E-2**: Given 빌드 산출물, When `index.html` 확인, Then `lang="ko"`와 승인된 제목이 들어 있다. 화면 레이아웃 변화 없음.
- **E-3**: Given 제거 대상 선택자, When `rg`로 `src` 전체 사용처 재검색, Then 0건이다. 제거 후 모든 화면의 레이아웃(폭·정렬)이 이전과 동일하다.
- **E-4**: Given `#root` 규칙, When 이 Epic 완료, Then 규칙이 그대로 남아 있다.
- **E-5**: Given 선택지 (a), When 삭제, Then `EmptyState` 렌더 결과가 이전과 같다. 선택지 (b)는 스크린샷 비교를 첨부하고 승인 후에만.

## 9. 테스트·문서

| 항목 | 내용 |
|---|---|
| T-1 | B-4 재현 테스트(기간 탭 invalidation), B-7 retry 정책 테스트, B-6 abort 테스트, query key factory 테스트 |
| T-2 | 계약 타입 테스트(B-1): by-period 응답 타입이 평면 배열임을 컴파일 시 검증 |
| T-3 | 라우팅 테스트: 14개 경로 렌더, `/`=Hello, (승인 시) Not Found |
| T-4 | FSD 경계 검사: 새 의존성 없이 `rg` 기반 스크립트 또는 테스트로 "같은 레이어 slice import·세그먼트 직접 import 0건" 확인(architecture 7절) |
| DOC-1 | `docs/guide-frontend.md` 갱신: FSD 레이어·`app` 레이어, slice public API, query key 규칙, 폼 접근성 규칙, 테스트 기준 |
| DOC-2 | 구현 종료 시 `docs/work-log/frontend-architecture-refactor.md` 기록(AGENTS.md 기준) |

**AC**

- Given 이 Epic의 모든 SF 병합, When `docs/guide-frontend.md` 확인, Then 실제 디렉터리 구조·규칙과 일치한다(문서와 코드 불일치 0).
- Given 각 SF PR, When 검증, Then `docs/guide-frontend.md`의 5개 명령(`test`/`lint`/`typecheck`/`format:check`/`build`)이 모두 실행·PASS 보고된다.

## 10. 전체 완료 조건 (Epic DoD)

1. 승인된 Stream 항목이 각 SF에서 병합되고, 승인되지 않은 항목(A-4·A-5 등)은 구현되지 않았다.
2. 라우트 14개·API 호출·폼 검증·금융 표시 값이 이전과 동일하다(회귀 테스트 + 금융 수치 화면 비교).
3. 새 의존성 0건(`package.json`·`pnpm-lock.yaml` diff 없음).
4. 5개 검증 명령 PASS, axe/E2E는 실행하지 않았다면 NOT_RUN 명시.
5. 최종 보고에 9개 항목(레이어·기능별 변경, 주요 경로 변경과 이유, FSD 판단 변경, 라우트·API·폼 검증·금융 의미 보존 여부, 검증 명령별 상태, axe/E2E 한계, 신규 의존성, 미완료·승인 대기, git diff 요약) 포함.

## 11. 승인 요청 목록

| # | 항목 | 선택지 | 권장 | 영향 |
|---|---|---|---|---|
| **S-1** | 이 spec 전체 범위(Stream A-1~3, B, C, D, E-3, T, DOC) | 승인 / 수정 / 보류 | 승인 | 구현 SF 착수 가능 |
| **D-3** | Not Found·라우트 오류 UI 구현 | 구현(A-4·A-5) / 이번 Epic에서 제외 | 구현은 **별도 승인**. 이번엔 제외 | 미인증 사용자·오타 URL에서 빈 화면 지속 |
| **D-4**(후속 구현됨, work-log D-E20-006) | 401 중앙 처리 | 보류 / 전역 redirect / 쿼리 캐시 `onError` 후 로그인 안내 | **보류**(세션 확인·복귀 경로 정책 결정 전) | 인증 요구 경로 추가·redirect 정책 변경은 사전 승인 사항 |
| **D-7**(후속 구현됨, work-log D-E20-006) | Zod / FSD lint 도입 | 미도입 / 도입 | **첫 단계 미도입**. 경계는 `rg` 검사, 계약은 타입+계약 테스트 | 새 의존성은 별도 승인 |
| **D-8** | `EmptyState` 스타일 | (a) 죽은 CSS 삭제 (b) 모듈 연결 | (a) — 동작 불변 | (b)는 시각 변경 |
| **D-9**(후속 구현됨, work-log D-E20-006) | by-period `year`/`month` 미지원 | 별도 이슈 / 백엔드 변경 | **별도 이슈**. 이 Epic 범위 밖 | 월 이동 UX 오해 가능성 잔존 |
| **D-10** | staleTime | 종류별 제안 / 전체 공통값 / 현행 유지 | **현행 유지 + 제안만**(근거 측정 전) | 공통값은 갱신 지연 위험(금융 수치) |
| **D-11**(후속 구현됨, work-log D-E20-006) | 필터 URL 동기화 | 현행 유지 / 도입 | 현행 유지 | 새로고침 시 필터 초기화 유지 |
| **D-12** | `lang`·`<title>` | `ko`+"투자 관리 시스템" / `ko`만 / 현행 | `lang=ko` 승인, **제목 문구는 사용자 확인** | 브라우저 탭·스크린리더 발음 |
| **D-13** | 템플릿 CSS | 확인된 미사용 선택자만 / `#root` 포함 전체 | 미사용 선택자만 | `#root` 변경은 전 화면 레이아웃 변경 |
| **D-14** | `@/` alias | 보류 / 도입 | 보류 | 도입 시 tsconfig·vite·eslint 설정 변경 |
| **D-16** | 금융 mutation 자동 재시도 금지 | 유지 | 유지(AC로 고정) | — |
| **D-17** | 재배치: `entities/dashboard`→`features/dashboard`, `entities/profit`→`features/profit-view`, `entities/auth`→`features/login` | 각각 승인 / 유지 | `auth`·`dashboard` 승인, `profit`은 사용처 재확인 후 | import 경로 다수 변경 → Epic 17/18 UI PR과 병합 순서 조정 필요 |
| **D-19** | 동작 변화를 동반하는 invalidation 확대(매도·비용·배정 후 `profit`·`dashboard`도 갱신, `['costs']` 교체) | 이번 Epic 제외 / 포함 | **제외**(동작 불변 원칙). 원하면 별도 SF로 | 포함 시 금융 수치의 갱신 시점이 바뀜 |
| **D-18** | 구현 실행 방식 | Master 직접 / 워커 dispatch(SF별) | 워커 dispatch는 **별도 승인** | 외부 dispatch는 명시 승인 없이는 하지 않음 |

> 본 목록은 구현 승인이 아니라 결정 요청이다. 승인 전에는 `ui/`·`backend/`를 수정하지 않는다.
