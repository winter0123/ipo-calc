# Query key factory 누락 조사

`docs/guide-frontend.md`는 query key를 엔티티별 factory에서 만들도록 규정한다. `rg` 조사에서 나머지 entity hook은 factory를 쓰지만 공지사항 페이지 3곳, 팝업 query, IPO 동기화 상태 query가 inline 배열을 직접 사용한다. `useAllInvestmentHistory`도 factory가 만든 목록 key에 `'all'` suffix를 직접 붙이고 있다.

변경은 캐시 key 문자열을 기존 값 그대로 factory에 모으는 범위로 제한한다. 공개 공지 목록·상세·팝업 및 관리자 목록은 서로 다른 root를 유지해 캐시 간 prefix 관계를 새로 만들지 않는다. 관리자 mutation은 기존처럼 관리자 목록 prefix만 invalidate한다. 동기화 상태 key도 `ipoSyncStatus`를 그대로 유지한다. 전체 투자내역 key는 목록 key의 하위 prefix 관계를 factory 안에 표현한다.
