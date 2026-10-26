# 도메인 검증 중복 정리 작업 기록

## 2026-10-26 — 구현

- SaleService 등록/수정의 다섯 금액 필드 음수 검사를 private helper로 통합했다. null 처리와 기존 필드별 오류 문구를 유지했다.
- AllocationService.update의 선행 음수 검사를 제거했다. 수량이 전달되면 이동/비이동 각각 후속 `validateQuantity`가 음수와 청약수량 상한을 검증한다. controller 테스트에 음수 PATCH 400 검증이 존재한다.
- 테스트·Checkstyle·Spotless·build는 사용자 요청에 따라 누적 backend 리팩토링 완료 후 실행한다: `NOT_RUN`.

## 후속 구현 — 청약 수정 계좌 조회 중복 제거 (2026-10-26)

- `SubscriptionService.update`에 새 계좌 ID가 있으면 계좌 조회·소유권 검증 결과를 저장해 최종 investor/account 관계 확인에 재사용한다. investorId만 변경되는 경우 기존 계좌 조회와 관계 검증은 유지한다.
- 같은 투자자 소유의 다른 계좌로 수정하는 MockMvc 회귀 테스트를 추가했다. 다른 계정 소유 계좌 403 및 관계 검증 관련 기존 테스트는 유지한다.
- Backend 테스트·Checkstyle·Spotless·build는 마지막 종합 검증까지 실행하지 않는다: `NOT_RUN`.

## 추가 조사 — 프론트 GET 경계

- API 경계 감사 중 `fetchPopupAnnouncements`만 직접 `apiFetch<unknown>` 후 별도 parse를 수행하는 것을 확인했다. 서버는 표시할 공지가 없을 때 HTTP 204를 반환한다.
- `apiGetParsed`와 optional-array transform으로 맞춰 204 → 빈 배열을 보존하고, 응답 검증·signal 전달 테스트를 추가했다. UI 테스트는 누적 프론트 리팩토링 종료 뒤 실행한다: `NOT_RUN`.
