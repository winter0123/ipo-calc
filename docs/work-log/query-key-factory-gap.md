# Query key factory 작업 기록

## 2026-10-26 — 구현

- query key factory 규칙을 따르지 않던 공지사항 목록/상세/팝업/관리자 목록과 IPO sync status를 확인했다.
- 현재 단계에서 key 값은 유지한 채 entity factory로 이전한다. 검증은 누적 UI 작업이 끝난 뒤 수행한다: `NOT_RUN`.
- 전체 투자내역 훅도 `investmentHistoryKeys.allPages(filters)`를 사용하도록 해 factory 외부에서 suffix를 조립하지 않게 했다. 목록 key prefix는 그대로다.
# Query key factory 누락 후속 조사·수정

## 2026-10-26 — 유효하지 않은 IPO 상세 key

- 앱 query 호출부를 검색해 확인한 결과, production 코드에서 유일하게 inline 배열로 조립되던 key는 `useIpo(undefined)`의 `['ipos', 'detail', 'invalid']`였다. 다른 직접 배열 사용은 테스트 fixture였다.
- `ipoKeys.invalidDetail()` factory를 추가하고 `useIpo`에서 재사용했다. 기존 key 값과 disabled query 동작은 유지한다. key unit test를 추가했다.
- 테스트, lint, typecheck, format:check 및 build는 누적 마지막 검증까지 실행하지 않는다: `NOT_RUN`.
