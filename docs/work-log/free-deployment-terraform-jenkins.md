# Work Log: free-deployment-terraform-jenkins (2026-10-01)

개인 프로젝트를 비용 0원으로 배포하는 구성(Terraform + Docker + Jenkins)을 설계하고 단계별로 구현한다. 설계: `docs/architecture/free-deployment-terraform-jenkins.md`, PR #302. 이 Epic은 직접 구현했다(워커 위임 없음).

## 원칙

- **비용 0원**: 유료 서비스·유료 플랜 전환 금지. Terraform `plan`에서 유료 리소스가 없는지 매번 확인한다.
- 목적은 둘 다다: A) Terraform이라는 도구 익히기, B) 이 프로젝트를 실제로 무료 배포하기. 그래서 가입이 필요 없는 로컬 단계를 앞에 둔다.
- Terraform은 서버·네트워크 생성 전용, 배포는 Jenkins 전담(D-DEP-004).
- 조회 전용 원칙 등 기존 서비스 스코프는 건드리지 않는다. backend·UI 애플리케이션 코드는 바꾸지 않는다.

## 단계

| 단계 | 내용 | 상태 |
|---|---|---|
| 0 | Terraform `docker` provider로 로컬 Postgres 컨테이너 실습(init·plan·apply·destroy, state, drift) | 완료(학습 후 폴더 삭제, 예제는 커밋 `fc85452` 이력) |
| 1 | `backend/Dockerfile`, `ui/Dockerfile`, `ui/Caddyfile`, `deploy/docker-compose.prod.yml` — 로컬에서 가입→승인→로그인→세션 쿠키 확인 | 완료(#302) |
| 2 | 로컬 Jenkins 컨테이너(`deploy/jenkins/*`)와 `Jenkinsfile`: 백엔드 테스트→UI 테스트→compose 배포→smoke | 완료(#302), 빌드 #2 성공 |
| 3 | Oracle Cloud 가입 후 `infra/oracle/`에서 Terraform으로 VM 생성 | **대기**(사용자의 계정 가입·도메인·API 키 선행) |
| 4 | 2단계 파이프라인을 VM으로 이전, webhook 연결, Jenkins를 운영 compose에 합침 | 대기 |
| 5 | DB 백업·모니터링 | 후속 |

## 결정 (설계 문서 §10에 ID 부여)

- 호스팅은 Oracle Cloud Always Free. 비교한 대안: AWS·GCP VM(무료 기간 한정·RAM 부족), Cloud Run+Neon(콜드스타트·쿠키 도메인 문제), Render/Koyeb(슬립·무료 DB 제한), 집 PC+Cloudflare Tunnel(Oracle 가입 실패 시 대안). 무료 조건은 바뀔 수 있어 구현 시 공식 페이지로 재확인한다.
- 앱·Postgres·Jenkins를 VM 1대에 compose로 동거한다. 세션 쿠키가 `SameSite=Lax`라 UI와 API가 같은 도메인이어야 하기 때문에 Caddy(리버스 프록시, 자동 HTTPS)를 둔다.
- 로컬 연습(0·1·2단계)을 Oracle 가입보다 먼저 한다 — 외부 가입·용량 문제로 학습이 막히는 위험을 없앤다(외부 리뷰 피드백 반영, D-DEP-006/007).
- 폴더 구조: Dockerfile은 소스 옆(`backend/`, `ui/`), `Jenkinsfile`은 루트, 런타임 compose·Jenkins 설정은 `deploy/`, Terraform은 `infra/`(3단계). `ci/cd` 한 폴더로 묶지 않는다 — CI 설정은 `Jenkinsfile` 하나뿐이고 CD(compose·Terraform)와 성격이 달라 뒤섞인다. 학습용 `terraform-local/`은 별도 폴더로 옮기지 않고 삭제했다.

## 1단계 메모 (2026-10-01)

- **API 경로에 `/api` 접두사가 없다.** 컨트롤러가 `/auth`, `/investors` 등을 직접 매핑하고 UI 라우트와 이름이 겹친다. backend를 바꾸지 않고 UI를 `VITE_API_BASE_URL=/api`로 빌드한 뒤 Caddy `handle_path /api/*`가 접두사를 떼서 전달하도록 했다.
- **DB 주소가 `localhost:5433`으로 고정**돼 있다(`application.yml`). 코드 수정 없이 환경변수 `SPRING_DATASOURCE_URL`로 덮어쓴다(Spring relaxed binding).
- **Caddyfile을 bind mount하지 않고 UI 이미지에 포함**했다. Jenkins 컨테이너가 `docker.sock`으로 `compose up`을 하면 bind mount 경로가 호스트 기준으로 해석되어 어긋나기 때문이다.
- actuator가 없어 backend 헬스체크를 넣지 않았다. smoke는 비로그인 `/auth/me`가 401인지로 대신한다(backend 기동·DB 연결·Flyway 마이그레이션까지 통과했다는 뜻).
- 가입 모드 기본값이 `APPROVAL`이라 로컬 검증 때 가입 후 `accounts.approved`를 DB에서 직접 `TRUE`로 바꿔 로그인했다.

## 2단계 메모 (2026-10-01)

- **Jenkins 부팅 실패(재시작 반복)**: JCasC의 Job DSL에서 `remote { url(...) }`와 `branches(...)`를 한 줄에 쓰면 Groovy가 `remote(...).branches(...)` 체이닝으로 해석해 NPE가 났다. 두 줄로 분리해 해결. 스택트레이스 맨 아래는 shutdown 훅이라 `grep -nE "Caused by|SEVERE"`로 최초 원인을 찾아야 한다.
- **file:// 체크아웃 거부**: Git 플러그인이 로컬 경로를 보안상 막는다(`GitSCM.ALLOW_LOCAL_CHECKOUT`). 로컬 연습 compose에만 허용하고, 운영(GitHub)에서는 제거한다.
- **Jenkins는 git에 커밋된 `Jenkinsfile`만 읽는다.** 수정 후 커밋하지 않으면 반영되지 않는다.
- **CI 테스트 DB**: 백엔드 테스트는 `localhost:5433` 공유 Postgres 전제다. 파이프라인이 테스트 직전에 임시 Postgres `ci-pg`를 `ci-net` 네트워크에 띄우고 `SPRING_DATASOURCE_URL`/`SPRING_DATASOURCE_PASSWORD`로 덮어쓰며, `post { always }`로 정리한다(성공·실패 무관).
- **검증 결과**: 빌드 #2 SUCCESS — 백엔드 테스트 통과(첫 실행 2분 8초), UI lint·typecheck·test 통과, `ipo-calc-prod` 스택 기동, `http://localhost:18080` UI 200·`/api/auth/me` 401.
- **docker.sock 권한**: Docker Desktop(mac)에서는 `group_add: 0`으로 접근된다. Linux VM에서는 `DOCKER_GID`를 소켓의 GID로 맞춰야 한다(4단계에서 확인).
- **아직 확인하지 못한 것**: 테스트를 일부러 실패시켰을 때 배포가 중단되는지, GitHub webhook 자동 트리거, `/jenkins` 접두사를 Caddy 뒤에서 쓸 때의 동작(로컬에서는 직접 접속만 확인).

## 운영 메모

- **Terraform provider 설치 실패**: `kreuzwerker/docker` 최신 3.9.0은 `init` 중 서명 파일 조회가 GitHub 500으로 실패했다(GitHub 상태는 정상, 같은 파일 직접 다운로드는 200이라 레지스트리 쪽 문제로 추정). 3.6.2로 고정해 해결. 복구되면 `~> 3.0`으로 되돌려도 된다.
- **누락한 문서화**: 새 프로그램·이미지를 설치하는 즉시 `docs/setup-history.md`에 적어야 하는데 1~2단계 구현 중에는 적지 않았다. 사용자 지적 후 `b5ab9a2`에서 소급 기록했다. 3단계부터는 설치·실행 즉시 기록한다.
- 사용자가 `docs/user-guides` 정적 가이드 삭제를 의도해 같은 PR에 포함했다(26개 파일).
