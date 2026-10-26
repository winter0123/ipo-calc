# CI 품질 게이트 Breakdown

## SF-1 — UI 품질 job

- frozen pnpm install 및 test/lint/typecheck/format:check/build를 수행한다.

## SF-2 — Backend 품질 job

- PostgreSQL 17 서비스, Java 21 및 test/Checkstyle/Spotless/build 명령을 구성한다.
- test/기본 Spring profile 모두 workflow 전용 datasource에 연결한다.

## SF-3 — 트리거와 최소 권한

- main push/PR 트리거, contents read-only 권한, 같은 ref 중복 실행 취소를 설정한다.
- 검증: workflow YAML과 작업 구성을 정적으로 검토한다. 현재 저장소 환경에서 hosted runner 실행 결과는 미확인으로 기록한다.
