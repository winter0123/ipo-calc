# 프론트엔드 아키텍처 리팩토링 작업 분해 (frontend-architecture-refactor)

> **Epic**: Epic 20 (후보)
> **상태**: **초안 — Phase 0 승인 전 구현 착수 금지.** 이 문서는 워커 dispatch 지시가 아니다.
> **작성일**: 2026-10-01 (Master 직접 작성 — 사용자 지시)
> **근거**: 조사 `docs/research/frontend-architecture-refactor.md`, spec `docs/spec/...`, 설계 `docs/architecture/...`
> **공통 규칙**: 한 SF = 한 워커 = 한 PR. 작업공간은 `frontend` worktree. 새 의존성·백엔드·금융 계산·API 의미 변경 금지. 외부 dispatch·commit·push·PR·merge는 명시 승인 후에만.

## 공통 검증 (모든 SF, `ui/`에서 실행, `docs/guide-frontend.md` 기준)

```bash
pnpm run test
pnpm run lint
pnpm run typecheck
pnpm run format:check
pnpm run build
```

보고 형식은 명령별 PASS / FAIL / NOT_RUN / BLOCKED. axe·E2E는 별도 승인 전까지 NOT_RUN.
이동(rename) SF는 `git diff -M --stat`으로 rename 비율을 함께 첨부한다.

## Epic 17·18과의 파일 충돌 후보

| 경로 | 충돌 대상 | 관련 SF |
|---|---|---|
| `entities/ipo/**`, `features/ipo-calendar/**`, `widgets/IpoCalendar/**`, `pages/IpoCalendarPage/**` | E17 UI·E19 달력 후속 | SF-7(키 factory), SF-9(index.ts), SF-11(재배치) |
| `App.tsx`, `main.tsx`, `index.css`, `shared/components/MainLayout/**` | E18 UI가 신규 라우트 추가 시 `App.tsx` 충돌 | SF-12(app 이동) |
| `entities/subscription/**`(투자 내역 조회 모델) | E18 자동 채움 UI가 투자 내역 타입을 확장할 가능성 | SF-9, SF-11 |
| `features/*-register/**`(폼) | E18이 매도·비용 폼에 "자동 채움" UI를 추가할 가능성 | SF-8a~c |

→ **SF-8 이후 SF는 착수 전 E17/E18의 미병합 UI PR 유무를 `gh pr list`로 확인**하고 있으면 해당 PR 병합 후 착수한다.

## SF 목록 (위험 낮은 순)

### SF-1. Dashboard 기간 집계 타입 교정 — 선행: Phase 0 승인

- **범위**: `entities/dashboard/types.ts`의 `DashboardByPeriod`를 백엔드 계약(평면 배열 아이템 `PeriodAggregation`)으로 교정, `api-client.ts`·`useDashboardByPeriod`·`DashboardPage.tsx`의 `as unknown as` 제거, 관련 테스트 mock 모양 교정.
- **비범위**: 요청 파라미터(`year`/`month`) 변경, 화면·문구 변경, 키 변경.
- **AC**: spec B-1. 추가로 fixture를 `satisfies PeriodAggregation[]`로 고정하고 잘못된 모양이 컴파일 오류가 됨을 타입 테스트로 확인.
- **산출물**: `ui/src/entities/dashboard/*`, `ui/src/pages/DashboardPage/*`, `ui/src/features/dashboard/*`(mock), 테스트.
- **롤백**: revert.

### SF-2. 죽은 코드·잔여물 정리 — 선행: Phase 0 승인 (SF-1과 병렬 가능, 파일 겹침 없음)

- **범위**: `useInvestmentHistoryFilters.ts` 삭제(전체 저장소 재검색 0건 확인), `.gitkeep` 제거, `index.css`의 `.counter`·`#social .button-icon` 제거(사용처 재검색 0건), 오래된 "Epic 11 미구현" 주석 정정.
- **비범위**: `#root`·`h1`·토큰·`EmptyState.module.css`(D-8), 동작 변경.
- **AC**: spec C-4, C-7, E-3, B-10.
- **롤백**: revert.

### SF-3. 접근성 lint 규칙 복원 — 선행: Phase 0 승인

- **범위**: `eslint.config.js`의 두 규칙(`no-static-element-interactions`, `click-events-have-key-events`)을 `error`로 복원.
- **AC**: spec SD-1. **착수 시 위반 0건 재확인.** 위반이 있으면 규칙을 켜지 말고 위반 목록을 보고(코드 수정은 별도).
- **롤백**: revert.

### SF-4. HTML `lang`·title — 선행: 승인 D-12(문구 확정)

- **범위**: `index.html` `lang="ko"`, `<title>`.
- **AC**: spec E-1/E-2. **새 브랜드명 금지.**

### SF-5. 재현 테스트 선행: invalidation 일람 고정 — 선행: SF-1

- **범위**: 코드 변경 없이 테스트만 추가. (a) 기간 탭 invalidation 접두사 불일치 재현(B-4), (b) 모든 mutation의 현재 `invalidateQueries` 대상을 고정, (c) 같은 `['investmentHistory', …]` 키 공유 동작 고정.
- **AC**: 결과를 PR 본문에 "재현됨 / 재현 안 됨"으로 기록. 재현 안 되면 B-4는 종결.
- **산출물**: 테스트만(`features/**/__tests__` 또는 기존 위치).

### SF-6. Query key factory + 중복 훅 통합 — 선행: SF-5

- **범위**: slice별 `queryKeys.ts`, `useInvestmentHistory` 2종 통합, `fetchInvestors`·증권계좌 목록 훅 중복 제거, 키 철자 통일, B-4 수정(SF-5에서 재현된 경우), B-5 교체(동작 차이는 PR에 명시), B-8 잡음 제거.
- **비범위**: staleTime 값 변경, retry/signal.
- **AC**: spec B-2~B-5, B-8. **사용자 동작 차이가 있으면 PR 본문에 명시**(없으면 "없음").
- **충돌**: E17/E18 UI PR 확인.

### SF-7. retry·signal — 선행: SF-6

- **범위**: `QueryClient` 기본 retry(4xx 즉시 실패), 조회 api-client 함수에 `signal` 선택 인자, 테스트.
- **비범위**: mutation retry(기본 0 유지 — AC로 테스트 고정).
- **AC**: spec B-6, B-7.

### SF-8a/8b/8c. 폼 접근성 — 선행: Phase 0 승인 (SF-3 이후 권장, 서로 병렬 불가 — 폼 파일 겹침 없으면 병렬 가능)

- **범위(분할)**: 8a = 로그인·가입(+`ApiError.status` 401 판정 SD-5), 8b = 투자자·증권계좌·IPO·청약 폼, 8c = 배정·매도·비용 폼.
- **공통**: `aria-invalid`에서 mutation 오류 제거, `aria-describedby` 조건부, 폼 수준 오류 `role="alert"`. 표시 문구 불변. 테스트는 **수정·추가하는 테스트부터** `user-event` 사용(SD-6).
- **AC**: spec SD-2~SD-5.
- **충돌**: E18 매도·비용 폼 UI PR 확인(8c).

### SF-9. entities public API + 경계 검사 테스트 — 선행: SF-6

- **범위**: 각 slice `index.ts`, 외부 import를 public API로 교체, `fsd-boundaries.test.ts`(역방향·같은 레이어·세그먼트 직접 import 검사) 추가. 위반이 남는 곳은 테스트에 **허용 목록 없이** 해소하거나 남은 위반을 명시 예외로 문서화.
- **AC**: spec B-11, T-4.
- **충돌**: 경로 다수 변경 → E17/E18 UI PR 확인, 이동 PR이므로 내용 변경 금지.

### SF-10. widgets 경계 해소 — 선행: SF-9

- **범위**: `DashboardCardGroup`의 `DashboardCard` 직접 import 해소(한 slice로 통합).
- **AC**: spec C-5.

### SF-11. read model 재배치 — 선행: SF-9, 승인 D-17 (slice당 1 PR)

- **범위**: `entities/auth`→`features/login`, `entities/dashboard`→`features/dashboard`, (승인 시) `entities/profit`→`features/profit-view`, (검토) 투자 내역 조회 모델. 이동만, 내용 변경 없음.
- **AC**: 이동 전후 테스트 동일 통과, 각 PR에서 `git diff -M` rename 비율 첨부.

### SF-12. app 레이어 — 선행: SF-9, 승인 A-1·A-2

- **범위**: `app/`에 `App.tsx`·Provider·전역 CSS 이동, `MainLayout`→`app/layouts`. `main.tsx` 위치는 설계 1절 A안(유지) 권장.
- **AC**: spec A-1~A-3. 14개 경로 렌더 테스트 추가(T-3).
- **충돌**: `App.tsx` — E18 신규 라우트 PR 확인.

### SF-13. 페이지 책임 정리 — 선행: SF-1, SF-6, SF-11

- **범위**: `DashboardPage` 탭별 렌더 분리, `DashboardTab` 타입·탭 목록 단일화, `ProfitViewPage` 투자자 조회 feature 경유, 대시보드 링크 `Link` 교체.
- **AC**: spec C-1, C-2, C-3, C-6.

### SF-14. 문서 마감 — 선행: 위 SF 병합 후

- **범위**: `docs/guide-frontend.md` 일괄 점검(각 SF가 해당 절을 이미 갱신한 전제), `docs/work-log/frontend-architecture-refactor.md`.
- **AC**: spec DOC-1, DOC-2, Epic DoD.

### (별도 승인 전 제외) SF-X1. Not Found / 라우트 오류 UI

- spec A-4, A-5. 설계 5절의 ErrorBoundary 자체 구현 비용을 재평가한 뒤 승인 시 SF로 확정.

### (별도 승인 전 제외) SF-X2. `EmptyState` 스타일 적용(D-8 (b))

### (Epic 밖) 이슈 제안: by-period `year`/`month` 파라미터(D-9)

## 의존 그래프

```
SF-1 ──► SF-5 ──► SF-6 ──► SF-7
  │                 │
SF-2, SF-3, SF-4    ├──► SF-9 ──► SF-10, SF-11, SF-12 ──► SF-13 ──► SF-14
SF-8a/b/c (SF-3 후)  
```

SF-1·2·3·4는 서로 파일이 겹치지 않아 병렬 가능(각각 별도 워커·PR). SF-8은 SF-6과 폼 파일이 겹치지 않으면 병렬.

## 승인 요청 (spec 11절과 동일 — 여기서는 breakdown 착수 조건만)

| 조건 | 해당 SF |
|---|---|
| spec 승인(S-1) | SF-1, 2, 3, 5, 6, 7, 8, 9, 10, 13, 14 |
| D-12(제목 문구) | SF-4 |
| D-17 | SF-11 |
| A-1·A-2(= S-1에 포함) + 충돌 확인 | SF-12 |
| D-3, D-8 | SF-X1, SF-X2 |
| D-18(워커 dispatch 여부) | 전부 — 미승인 시 Master 직접 수행 |
