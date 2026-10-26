# 공모주 등록 응답 런타임 검증

## 2026-10-26 — 구현

- backend `POST /ipos`의 응답 타입이 `IpoResponse`이며 조회 API와 같은 DTO임을 확인했다.
- `registerIpo`를 `apiFetchParsed`와 기존 `ipoSchema`로 연결했다. 요청 본문, HTTP method, 반환 shape는 변경하지 않았다.
- 필수 응답 필드 누락 시 `ZodError`가 발생하는 API client 회귀 테스트를 추가했다. 테스트 실행은 누적 UI 검증 단계까지 보류한다: `NOT_RUN`.
