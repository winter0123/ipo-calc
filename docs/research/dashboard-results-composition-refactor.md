# 대시보드 결과 렌더링 조사

## 질문

`DashboardPage`에서 조회 상태와 탭별 결과 UI를 분리할 구체적 이득이 있는가?

## 확인 결과

- 페이지는 종합·증권사별·월별·연도별 조회 훅과 탭 UI를 조합하면서 각 탭의 빈 상태·집계 변환·목록 렌더링까지 담당한다.
- `DashboardPage.tsx`는 231줄이고 `renderTabData`가 종합 탭의 투자자별 fallback 및 세 가지 집계 렌더링을 함께 소유한다.
- `features/dashboard`에서 `DashboardCardGroup`·`EmptyState` widgets를 직접 가져오면 계층 방향이 뒤집힌다. 따라서 결과 렌더링은 `DashboardPage` slice 내부의 sibling component로 둔다.
- 페이지 통합 테스트가 탭 전환, investor fallback, loading/error/empty, period selector 흐름을 이미 확인한다.

## 범위 판단

탭 결과의 데이터 변환과 렌더링만 `DashboardTabContent`로 이동한다. 조회 훅, 캐시 키, 탭 상태, 검색 기간, reset/invalidation, UI 텍스트·상태는 페이지가 계속 소유한다. 화면 구조나 API 계약은 바꾸지 않는다.
