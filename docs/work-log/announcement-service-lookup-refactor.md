# 공지 서비스 조회 중복 정리

## 2026-10-26 — 구현

- `AnnouncementService`의 관리자 상세 조회·수정·삭제에서 반복되던 `findByIdAndDeletedAtIsNull`과 404 예외 생성을 `findExistingAnnouncement`으로 통합했다.
- 조회 조건, 예외 유형·상태·메시지, 공개 조회 흐름은 바꾸지 않았다.
- 테스트·Checkstyle·Spotless·build는 누적 리팩토링 검증 시 실행한다: `NOT_RUN`.
