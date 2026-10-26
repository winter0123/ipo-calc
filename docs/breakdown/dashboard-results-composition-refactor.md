# 대시보드 결과 조합 리팩토링 Breakdown

## SF-1: 탭 결과 표시 분리

- 범위: `DashboardTabContent` sibling 및 전용 CSS 추가, 페이지의 탭별 변환/렌더 제거, 현재 페이지 통합 테스트 유지 및 결과 컴포넌트 단위 테스트 추가.
- 비범위: 훅·탭 URL·기간 선택·query key·invalidation 변경, 기능/문구 변경, feature/widget 레이어 이동.
- 선행: 없음.
- 수용 기준: 명세의 탭별 표시와 상태 동일성.
- 검증: DashboardPage 및 DashboardTabContent 테스트, 이후 사용자 요청에 따라 최종 UI 종합 검증.
