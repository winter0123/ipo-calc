# 설치 이력 및 환경 복구 절차

> 이 문서의 목적: DB가 날아가거나 로컬/서버 환경이 망가졌을 때, 또는 다른 서버로 이전할 때 **이 문서만 보고 처음부터 다시 세팅**할 수 있게 하기 위함이다. 새 프로그램/인프라를 설치하거나 DB 마이그레이션을 실행하면 그 즉시(몰아서 적지 않고) 이 문서의 "1. 설치 이력"에 추가한다. 시크릿(비밀번호, API 키 등)은 절대 값 자체를 적지 않고, 필요한 환경변수 이름만 `.env.example` 참조로 안내한다.

---

## 1. 설치 이력 (append-only, 시간순)

- 2026-09-16 — PostgreSQL 17 설치 (Docker Compose) — 이유: 메인 DB — 명령: `docker compose up -d postgres` (커밋 `8886c41`)
- 2026-09-20 — `docs/setup-history.md` 신설 — 이유: 설치/환경 이력을 한 곳에 모아 서버 이전·복구를 빠르게 하기 위함(사용자 지시)
- 2026-09-20 — Gradle 9.7.1 설치 (Homebrew, `brew install gradle`) — 이유: SF-1(프로젝트 초기 셋업)에서 `backend/`의 Gradle Wrapper(gradle-wrapper.jar)를 생성하려면 시스템 Gradle이 필요했음. 부수 설치: gradle-completion, openjdk@25(Gradle 자체 구동용, 프로젝트 빌드는 여전히 Java 21 사용), glib/harfbuzz/xz/little-cms2(의존성) — 명령: `brew install gradle`
- 2026-09-20 — `backend/` Gradle Wrapper 생성 — 명령: `cd backend && gradle wrapper`(설치된 시스템 Gradle 9.7.1 버전 그대로 사용, JAVA_HOME을 Java 21로 지정) — 이후 `gradle` 시스템 설치 없이 `./gradlew`만으로 빌드 가능. `./gradlew build --dry-run`으로 설정 단계 통과 확인(BUILD SUCCESSFUL). Gradle 10 호환성 관련 deprecation 경고가 있어 추후 재확인 필요.
- 2026-09-20 — **PostgreSQL 호스트 포트를 5432 → 5433으로 변경**(사용자 결정) — 이유: SF-2 테스트(`@SpringBootTest`) 실행 중 이 프로젝트와 무관한 다른 Postgres 컨테이너(`postgres-db`, postgis 이미지, 다른 프로젝트용으로 추정)가 이미 호스트 포트 5432를 점유하고 있어 `ipo-calc-postgres` 컨테이너가 한 번도 기동되지 못했음을 발견. 컨테이너 내부 포트는 5432 그대로, 호스트 노출 포트만 5433으로 변경(`docker-compose.yml`). 다른 컨테이너는 손대지 않음. `backend/src/main/resources/application.yml`의 datasource URL도 `localhost:5433`으로 함께 수정 필요(SF-2 구현 워커가 반영).
- 2026-09-22 — **GitHub Issue를 Orca 태스크와 병행 운영하기로 결정**(사용자 지시, AGENTS.md 8.4장) — 이유: Orca 태스크는 실행 단위일 뿐 사람이 보기 편한 진행 현황 창구가 아니므로, 사용자가 GitHub에서 Epic 단위 진행 상황을 볼 수 있게 함. `status:planned/in-progress/review/changes-requested/approved` 라벨 신설. 기존에 방치돼 있던 Epic 2~4 이슈(#64/#71/#77)를 실제 완료 상태에 맞춰 정리하고, Epic 5~16에 대한 이슈(#122~#133)를 각 Epic의 실제 진행 상태(완료/진행중/계획됨)에 맞춰 신규 생성·정리함. 이후로는 Epic/SF 상태가 바뀔 때마다 그 즉시 이슈 상태도 함께 갱신한다.

- 2026-10-01 — **무료 배포 구성 도입**(Terraform·Jenkins·Docker, 설계: `docs/architecture/free-deployment-terraform-jenkins.md`, PR #302) — 이유: 개인 프로젝트를 비용 0원으로 배포하기 위함(사용자 결정). 이 항목 아래 세부 이력은 모두 같은 날 작업.
- 2026-10-01 — Terraform 학습용 로컬 실습 — 로컬에 이미 설치돼 있던 Terraform 1.16.4 사용(신규 설치 아님). provider `kreuzwerker/docker`는 최신 3.9.0의 서명 파일 조회가 GitHub 500으로 실패해 **3.6.2로 고정**하고 init 성공. 실습 폴더는 학습 후 삭제(예제는 커밋 `fc85452` 이력에 보존).
- 2026-10-01 — 컨테이너 이미지 추가 사용(모두 Docker 이미지, 호스트 설치 없음) — `eclipse-temurin:21-jdk-alpine`/`21-jre-alpine`(backend 빌드·실행), `node:22-alpine`·`caddy:2-alpine`(UI 빌드·서빙), `postgres:17`(운영 compose·CI 테스트 DB), `jenkins/jenkins:lts-jdk21`(Jenkins), `docker:cli`(Jenkins 이미지에 docker CLI·compose 플러그인 복사용).
- 2026-10-01 — 로컬 Jenkins 컨테이너 구축 — 파일: `deploy/jenkins/{Dockerfile,plugins.txt,casc.yaml,docker-compose.yml}`, 저장소 루트 `Jenkinsfile`. 이미지에 docker CLI·compose·node 22·pnpm 10 포함, 플러그인은 `workflow-aggregator`, `git`, `configuration-as-code`, `job-dsl`. 설정은 JCasC로 자동화(셋업 위저드 생략, `admin` 계정 비밀번호는 환경변수 `JENKINS_ADMIN_PASSWORD`). 로컬 연습 전용 옵션 `GitSCM.ALLOW_LOCAL_CHECKOUT=true`(file:// 저장소 체크아웃)는 운영(GitHub)에서 제거한다. 겪은 문제: Job DSL에서 `remote { ... }`와 `branches(...)`를 한 줄에 쓰면 Groovy가 체이닝으로 해석해 Jenkins 부팅이 실패(두 줄로 분리해 해결).

- 2026-10-02 — Oracle Cloud(Always Free) 계정 가입, Home Region `ap-osaka-1`(오사카) — 이유: 무료 배포 3단계. API 키 쌍을 만들어 개인키를 `~/.oci/oci_api_key.pem`(권한 600, **저장소 밖**)에 보관, VM 접속용 SSH 키 `~/.ssh/oracle_vm`(ed25519)을 생성. 키는 사용자당 3개까지라 `api-key-count` 한도 에러가 났으면 안 쓰는 키를 삭제한다. `infra/oracle/` Terraform 코드 작성(provider `oracle/oci ~> 7.0`, `terraform init` 완료). 필요한 변수는 `infra/oracle/terraform.tfvars.example` 참조(실제 `terraform.tfvars`는 git 제외).

---

## 2. 환경 복구 절차 (현재 기준 최신 상태로 계속 갱신, 순서대로 실행하면 전체 환경이 재현되어야 함)

### 사전 준비
1. 저장소 클론: `git clone https://github.com/winter0123/ipo-calc.git`
2. 환경변수 파일 준비: `.envrc`(direnv 사용 중)에 아래 값을 채운다. 실제 값은 이 문서에 적지 않는다.
   - `JAVA_HOME` — 로컬 JDK 21 설치 경로 (예: Microsoft Build of OpenJDK 21)
   - `DB_PASSWORD` — PostgreSQL 비밀번호 (docker-compose.yml의 `POSTGRES_PASSWORD`와 일치해야 함)
3. `direnv allow` (direnv 미설치 시 `.envrc`의 `export`를 셸 프로파일에 직접 반영)

### 인프라 기동
4. `docker compose up -d postgres` — PostgreSQL 17 컨테이너 기동 (컨테이너명 `ipo-calc-postgres-dev`, DB명 `ipo_calc`, 사용자 `ipo`, **호스트 포트 `5433`**(컨테이너 내부는 5432, 다른 프로젝트의 컨테이너와 충돌 회피), 볼륨 `postgres_data`). 기동 전 `docker ps`로 5433 포트가 비어있는지 먼저 확인 권장.

### 백엔드
5. Gradle 설치: `brew install gradle` (시스템에 Gradle이 없으면 `backend/`의 Gradle Wrapper가 이미 커밋되어 있으므로 보통 불필요 — wrapper가 없거나 재생성해야 할 때만 설치)
6. `cd backend && ./gradlew build` — Java 21, Spring Boot 3.3.4 기반 프로젝트 빌드(현재 `winter0123/account-authentication` 브랜치에서 진행 중, main에는 아직 merge 안 됨)
7. 참고: 2026-09-20에 기존 `backend/` 스캐폴딩(과거 세션 산출물, 미승인 상태)을 한 번 삭제하고 Epic 1(account-authentication) SF-1에서 처음부터 재생성함.

### 프론트엔드
6. 아직 프론트엔드 코드 없음. 생성되면 `npm install && npm run dev` 형태로 이 절차에 추가한다.

### 테스트용 슬롯 DB (2026-09-30 추가)

같은 Postgres 컨테이너(`ipo-calc-postgres-dev`) 안에 슬롯 DB `ipo_slot_1`~`ipo_slot_4`를 두고 백엔드 테스트를 격리한다. 슬롯 1~3은 워커, 4는 Master 검증용이다.

1. 슬롯 초기화(없으면 생성): `scripts/db-slot.sh reset <1-4>` — 마지막 줄에 `DB_NAME=ipo_slot_N`을 출력한다. `scripts/db-slot.sh list`로 존재 여부를 본다.
2. 테스트 실행: `DB_NAME=ipo_slot_N DB_PASSWORD=<로컬비밀번호> ./gradlew test --rerun`
3. `DB_NAME`을 주지 않으면 기본 DB(`ipo_calc`)를 쓴다 — 이 DB는 병합된 마이그레이션만 쌓인 dev 상태로만 유지한다.
4. 슬롯을 reset 한 직후(마이그레이션이 처음부터 실행된 상태)에도 전체 테스트가 통과한다. 처음에는 V15 시드 증권사와 테스트 데이터가 충돌하는 기존 테스트가 실패했으나 #216에서 고쳤다. 새 테스트는 시드와 겹치지 않는 고유 이름을 쓰고 자기가 만든 행만 지운다.

### 배포 스택 로컬 기동 (2026-10-01 추가)

앱 전체(Postgres·backend·Caddy+UI)를 운영과 같은 구조로 로컬에서 띄우고, 로컬 Jenkins로 파이프라인을 돌리는 절차다. 환경변수 이름은 `deploy/.env.example` 참조(값은 적지 않는다).

1. `cd deploy && cp .env.example .env` 후 `DB_PASSWORD`, `APP_ENCRYPTION_KEY`, `APP_REMEMBER_ME_KEY`(둘 다 `openssl rand -base64 32`)를 채운다. `.env`는 git 제외. 로컬 http 테스트는 `SITE_ADDRESS=:80`, `SESSION_COOKIE_SECURE=false`. 포트가 충돌하면 `HTTP_PORT`/`HTTPS_PORT`를 바꾼다(기본 예시 18080/18443).
2. 앱 기동: `docker compose -f docker-compose.prod.yml up -d --build` → `http://localhost:$HTTP_PORT`. UI는 `/api` 접두사로 API를 호출하고 Caddy가 접두사를 떼서 backend로 전달한다. backend의 DB 주소는 `SPRING_DATASOURCE_URL`로 덮어쓴다.
3. 로컬 Jenkins: `.env`에 `JENKINS_ADMIN_PASSWORD`, `JOB_BRANCH`(빌드할 브랜치)를 추가하고 `docker compose -f jenkins/docker-compose.yml --env-file .env up -d --build` → `http://localhost:18081/jenkins`(포트는 `JENKINS_PORT`). 잡 `ipo-calc-deploy`가 자동 생성된다. Jenkinsfile은 **git에 커밋된 내용**만 읽으므로 수정 후 커밋해야 반영된다.
4. 파이프라인 단계: 백엔드 테스트(임시 Postgres `ci-pg`, 네트워크 `ci-net`) → UI lint·typecheck·test → `docker compose -p ipo-calc up -d --build` → smoke(`/auth/me`가 401).
5. 정리: `docker compose -p ipo-calc -f docker-compose.prod.yml down -v` 및 `docker compose -p ipo-calc -f jenkins/docker-compose.yml --env-file .env down`.
6. Linux VM에서는 Jenkins가 docker.sock에 접근하도록 `DOCKER_GID=$(stat -c %g /var/run/docker.sock)`를 지정한다(mac Docker Desktop은 기본값 0으로 동작).

---

## 3. 참고

- 인프라 정의 파일: `docker-compose.yml` (저장소 루트)
- Docker/PostgreSQL 외 추가 인프라(Redis 등)가 필요해지면 그 즉시 위 1~2절에 추가한다.
- Git Manager는 새 설치/마이그레이션이 실제 설치된 것과 이 문서가 일치하는지 주기적으로 감사한다(AGENTS.md 9.2 참고).
