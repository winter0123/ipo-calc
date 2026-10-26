# CI 품질 게이트 조사

저장소에는 `.github/workflows`가 없고 검증은 개발자가 로컬에서 실행한다. UI `package.json`에는 test/lint/typecheck/format:check/build 스크립트가 있다. Backend Gradle은 test, Checkstyle, Spotless를 제공하고 `test`에서 외부 IPO/휴장일/알림 동기화를 끈다.

Backend 테스트는 PostgreSQL을 사용한다. `application.yml`은 DB 환경 변수 기반이며 `application-test.yml`은 로컬 5433 포트를 설정한다. CI에서는 임시 PostgreSQL 17 서비스를 사용하고 `SPRING_DATASOURCE_*` 환경변수로 두 설정을 같은 일회성 DB에 연결한다. production credential이나 external API secret은 필요하지 않다.
