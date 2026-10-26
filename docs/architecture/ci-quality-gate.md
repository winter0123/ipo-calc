# CI 품질 게이트 구조

```text
pull_request(main) / push(main)
  ├─ ui: Node 22 + pnpm 10 → test → lint → typecheck → format:check → build
  └─ backend: Java 21 + ephemeral PostgreSQL 17
       → test → checkstyleMain/checkstyleTest → spotlessCheck → build
```

Backend job은 workflow 전용 DB `ipo_ci`를 service container에 만들고, Spring datasource 환경 변수로 test profile과 기본 profile 모두 같은 서비스에 연결한다. 저장소 데이터와 운영 자격 증명은 사용하지 않는다.
