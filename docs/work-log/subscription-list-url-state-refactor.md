# 청약 목록 URL 상태 작업 기록

## 2026-10-26 — 구현

- `updateDateRangeParams`를 일정 feature의 순수 유틸로 추가하고, 페이지의 인라인 URL 갱신을 위임하도록 바꿨다.
- 다른 쿼리 값 보존, 역전된 범위 보정, 입력 URLSearchParams 불변성을 테스트에 추가했다.
- 페이지 동작과 query replace 옵션, 기본 월 계산은 유지했다.
- 검증은 사용자의 지시에 따라 종합 리팩토링 종료 때까지 `NOT_RUN`이다.

## 2026-10-26 — 누적 리팩토링 묶음 최종 검증

- URL updater 단위 테스트를 포함한 UI 전체 709 tests PASS. lint, typecheck, format:check, build도 PASS.
- `git diff --check` PASS. 해당 페이지의 `replace: true`와 화면 동작은 기존대로 유지했다.
# Follow-up — URL 날짜 실재성 검증

- 기존 query parser는 `YYYY-MM-DD` 형식과 범위 순서만 검사해 `2026-02-30`을 유효한 날짜로 받아들였다.
- 월별 일수와 Gregorian 윤년 규칙을 확인해 불가능한 날짜는 기본 이번 달로 fallback하도록 보완했다. 2024 윤년 2월 29일은 허용하고 2025년 및 2026년 2월 29/30일은 거부하는 단위 테스트를 추가했다.
- UI 전체 검증은 사용자 요청에 따라 누적 frontend 변경이 끝난 뒤 실행한다: `NOT_RUN`.
