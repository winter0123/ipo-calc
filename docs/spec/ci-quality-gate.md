# CI 품질 게이트 Spec

## Acceptance Criteria

1. `main` push 및 `main` 대상 pull request에서 UI와 backend job을 실행한다.
2. UI는 frozen lockfile 설치 후 test/lint/typecheck/format:check/build를 수행한다.
3. Backend는 일회성 PostgreSQL 17에 연결해 test, Checkstyle main/test, Spotless, build를 수행한다.
4. CI는 local/production secret이나 외부 API 자격 증명을 사용하지 않고, workflow permissions는 read-only다.
5. 두 job은 독립 실행하며 동일 ref의 이전 실행은 새 실행으로 대체 가능하다.

## 비범위

Branch protection 변경, 배포, 외부 service credentials, E2E browser workflow.
