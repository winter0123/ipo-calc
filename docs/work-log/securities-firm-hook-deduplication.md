# 증권사 조회 hook 중복 제거

## 2026-10-26 — 구현

- `securities-account-register` feature와 `entities/securities-firm`에 동일한 `useSecuritiesFirms` 구현이 중복돼 있음을 확인했다.
- feature wrapper를 삭제하고 폼에서 entity 공개 hook을 사용하도록 변경했다. query key, fetch 함수, AbortSignal 전달 및 화면의 로딩/오류 상태는 entity hook이 제공하므로 그대로 유지된다.
- UI 테스트·lint·typecheck·format:check·build는 누적 프론트 리팩토링 종료 시 실행한다: `NOT_RUN`.
