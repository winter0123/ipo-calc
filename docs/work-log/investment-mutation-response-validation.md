# 청약·배정·매도 mutation 응답 검증

## 2026-10-26 — 구현

- 청약·배정·매도 등록/수정 API client가 `apiFetch<T>`의 타입 인자만으로 응답을 신뢰하던 부분을 확인했다.
- 각 엔티티에 이미 존재하는 `subscriptionSchema`, `allocationSchema`, `saleSchema`를 `apiFetchParsed`에 전달해 응답을 런타임 검증한다.
- URL, HTTP method, request body, 반환 타입은 유지했다. 정상 응답 및 schema 불일치 테스트를 추가했다.
- UI 테스트·lint·typecheck·format:check·build는 누적 리팩토링 완료 시 실행한다: `NOT_RUN`.
