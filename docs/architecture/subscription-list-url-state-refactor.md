# 청약 목록 URL 상태 리팩토링 구조

`features/subscription-list/subscriptionSchedule.ts`에 URLSearchParams를 복사·갱신하는 순수 함수 `updateDateRangeParams`를 둔다. 페이지는 현재 `useSearchParams`를 계속 소유하고 updater에 입력값과 이번 달 기본 범위를 전달한다. 반환된 파라미터를 기존 replace 옵션으로 반영한다.

라우팅과 React 상태는 그대로 두며 새 hook·의존성·API 변경은 없다. 기존 페이지 테스트에 더해 helper 테스트로 경계 순서, 무관 쿼리 보존, 입력 객체 불변성을 고정한다.
