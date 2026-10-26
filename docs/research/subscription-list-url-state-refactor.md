# 청약 목록 URL 상태 조사

## 질문

청약 목록의 조회 기간 URL 갱신 규칙을 화면에서 분리해 순수 함수로 검증할 실익이 있는가?

## 확인 결과

- `SubscriptionListPage`는 `from`/`to`를 읽고 이번 달 기본값을 채우며, 날짜 입력 변경 시 두 경계의 순서를 보정한다.
- 날짜 변경 함수는 기존 쿼리 파라미터를 복사해 보존하고 `replace: true`로 URL을 갱신한다.
- 날짜 범위 및 일정 집계는 `features/subscription-list/subscriptionSchedule.ts`에 이미 있다. 새 hook이나 재사용 범용 URL 프레임워크는 필요하지 않다.
- 투자자·IPO·투자내역 조회와 로딩/오류 상태는 페이지에 남겨 화면 조합 책임을 유지한다.

## 범위 판단

두 입력이 같은 범위 정렬 규칙을 공유하므로 URLSearchParams 변경만 순수 함수로 추출한다. 화면 동작, API, 라우트, 날짜 검증 정책은 바꾸지 않는다.
