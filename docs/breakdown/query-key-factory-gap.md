# Query key factory 누락 Breakdown

## SF-1 — 공지사항 query key 이전

- 대상: announcement entity queryKeys/public API 및 공지 목록·상세·팝업·관리자 페이지.
- 완료: 기존 key 배열을 factory 함수로 대체하고 관리 페이지 invalidation 범위를 보존한다.

## SF-2 — IPO sync status query key 이전

- 대상: `ipoKeys` 및 `useIpoSyncNow`.
- 완료: 기존 `['ipoSyncStatus']`를 factory가 생성하고 동기화 전후 캐시 갱신을 유지한다.

## SF-3 — 전체 투자내역 suffix factory 이전

- 대상: `investmentHistoryKeys`, `useAllInvestmentHistory`.
- 완료: `list(filters)` 하위 prefix를 유지하며 `'all'` suffix 생성을 factory가 소유한다.

검증: key factory 테스트와 기존 UI 전체 테스트/lint/typecheck/format/build를 누적 리팩토링 종료 시 실행한다.
