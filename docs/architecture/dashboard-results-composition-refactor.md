# 대시보드 결과 조합 리팩토링 구조

`DashboardPage`가 query hook과 현재 탭을 소유하고 기존 `currentQuery` 기준으로 loading/error를 처리한다. 새 sibling `DashboardTabContent`에는 탭 종류별 데이터, 투자자 목록, reset handler를 전달한다. 이 컴포넌트는 현재 `renderTabData`의 projection과 표시 JSX를 소유한다.

해당 sibling은 같은 page slice의 내부 모듈이므로 `DashboardCardGroup`과 `EmptyState` widgets를 재사용할 수 있다. CSS는 페이지 레이아웃 규칙을 유지하되 결과 전용 클래스를 sibling 스타일 모듈로 옮긴다. React Query/API/상태 소유권은 변경하지 않는다.
