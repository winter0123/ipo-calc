# 청약 목록 URL 상태 리팩토링 Breakdown

## SF-1: 날짜 범위 URL updater 추출

- 범위: 순수 updater 구현, feature 공개 index 연결, 페이지의 인라인 갱신 로직 교체, helper 테스트 추가.
- 비범위: 날짜 검증 정책·기본값·화면·라우트/API 변경, 전체 페이지나 범용 URL hook 추출.
- 선행: 없음.
- 수용 기준: spec의 네 기준 충족.
- 검증: `subscriptionSchedule.test.ts`, `SubscriptionListPage.test.tsx`, 이후 사용자 요청에 따라 최종 일괄 UI 검증.
