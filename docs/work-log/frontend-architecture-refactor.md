# 작업 로그: 프론트엔드 아키텍처 리팩토링 (frontend-architecture-refactor, Epic 20)

## 2026-10-01 — Master 직접 구현 (사용자 지시)

사용자가 "너가 직접 수정", "전부 승인"으로 지시해 워커 dispatch 없이 Master가 `planning-e20` 작업공간에서 `ui/`를 직접 수정했다. commit·push·PR은 아직 승인되지 않았다.

### D-E20-001: 결정과 범위
- `/`는 `Hello` 유지(redirect 없음). 새 의존성 0건, 백엔드 변경 0건.
- 승인에 따라 포함: A-4 NotFound, A-5 ErrorBoundary, D-8(b) EmptyState CSS 모듈 스타일, D-19 invalidation 확장, D-17 재배치, D-12 `lang="ko"`·title "투자 관리 시스템".
- 보류: D-4 401 중앙 redirect, D-7 Zod, D-9 by-period 백엔드 수정, D-11 필터 URL 동기화, D-14 `@/` alias, D-15 React Compiler.

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
