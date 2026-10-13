# 아키텍처 설계: 무료 배포 — Terraform + Docker + Jenkins (Oracle Cloud Always Free)

> **상태**: 설계 합의 완료(2026-10-01), 구현 전
> **범위**: 인프라(Terraform)·컨테이너(Dockerfile/compose)·CI/CD(Jenkins) 설계
> **제약**: 개인 프로젝트이므로 **비용 0원**이어야 한다. 유료 서비스·유료 플랜 전환 금지.
> **관련 문서**: `docs/current-status.md`(비공개 운영, 운영 환경변수), `docs/guide-backend.md`, `docs/guide-frontend.md`

---

## 1. 배경과 목표

현재 저장소에는 배포 인프라가 없다(Dockerfile·CI/CD·클라우드 설정 전무, 로컬 Postgres용 `docker-compose.yml`만 존재). 목표는 다음과 같다.

1. **Terraform**으로 인프라를 코드로 만든다(학습 목적 포함).
2. **Dockerfile**로 backend·UI를 컨테이너화한다.
3. **Jenkins**로 push → 테스트 → 빌드 → 배포를 자동화한다.
4. 위 전부를 **무료**로 운영한다.

운영 성격은 `current-status.md`의 "비공개 운영(D-B-029, 가입 모드 APPROVAL)"과 같은 개인용 소규모 트래픽이다. 일반 공개 배포는 KIS 약관·법률 검토 전까지 보류 상태이므로 고가용성·오토스케일은 범위 밖이다.

## 2. 호스팅 선택: Oracle Cloud Always Free

| 안 | 판단 | 이유 |
|---|---|---|
| **A. Oracle Always Free (채택)** | VM 1대에 Docker | 영구 무료, ARM 합계 2 OCPU/12GB(공식 문서 2026-10-02 확인, 옛 한도 4/24에서 축소됨), 공식 Terraform provider(`oracle/oci`), 구조 단순 |
| B. 집 PC + Cloudflare Tunnel | 대안(A 실패 시) | 확실한 0원이나 24시간 가동 기기 필요. Dockerfile·compose는 그대로 재사용 가능 |
| C. Cloud Run + Neon + Cloudflare Pages | 비채택 | JVM 콜드스타트, 서비스 3분할로 동일 도메인 쿠키 설정 번거로움, GCP 결제계정 필요(실수 과금 위험) |
| D. Render/Koyeb 무료 | 비채택 | 유휴 슬립, 무료 DB 만료·제한으로 지속 운영 불안 |
| AWS / GCP VM | 비채택 | AWS는 크레딧 후 과금, GCP e2-micro는 RAM 1GB로 JVM+Postgres 부족 |

> 무료 플랜 조건은 자주 바뀐다. 구현 착수 시 Oracle 공식 Always Free 페이지로 한도를 재확인한다.

## 3. 전체 구조

```
[1회] 개발자 맥북
  terraform apply ──▶ Oracle Cloud: VCN·서브넷·보안규칙·고정IP·ARM VM 생성
                                    (cloud-init으로 Docker 설치)

[상시] 
  git push ──▶ GitHub ── webhook ──▶ Oracle VM (1대)
                                   ┌──────────────────────────────────────────┐
                                   │ Jenkins 컨테이너                           │
                                   │  1) checkout                             │
                                   │  2) 테스트 (gradle test / pnpm test)       │
                                   │  3) docker build (backend, ui)           │
                                   │  4) docker compose up -d ──┐             │
                                   │                            ▼             │
                                   │ Caddy ─┬─▶ /api/* → backend (Spring)     │
                                   │        ├─▶ /jenkins → Jenkins            │
                                   │        └─▶ 그 외  → UI 정적 파일             │
                                   │                       backend ─▶ Postgres│
                                   └──────────────────────────────────────────┘
                                          ▲ HTTPS (Let's Encrypt 자동)
                                   브라우저 (이름.duckdns.org)
```

### 도구별 역할 분리

| 도구 | 역할 | 실행 시점 |
|---|---|---|
| Terraform | 서버·네트워크·방화벽 생성(건물) | 최초 1회, 인프라 변경 시 |
| Docker | 앱을 실행 가능한 이미지로 포장 | 배포마다 |
| Jenkins | 빌드·테스트·배포 자동화 | push마다 |
| Caddy | 리버스 프록시 + 자동 HTTPS | 상시 |

**Terraform은 VM 생성 이후 배포에 관여하지 않는다.** 배포는 Jenkins가 전담한다. Jenkins와 앱이 같은 VM에 있으므로 배포 단계에 SSH가 필요 없다.

## 4. 컴포넌트 설계

### 4.1 Caddy (리버스 프록시)

- 사용자가 요청한 구성요소가 아니라 설계자가 제안한 선택이다. 필요 이유는 두 가지다.
  1. **동일 도메인 통합**: 세션 쿠키가 `SameSite=Lax`(`current-status.md` #245)이므로 UI와 API가 같은 도메인이어야 한다. Caddy가 하나의 도메인으로 묶는다.
  2. **HTTPS**: 운영은 `SESSION_COOKIE_SECURE=true`라 HTTPS가 필수다. Caddy가 Let's Encrypt 인증서를 자동 발급·갱신한다.
- 대안은 nginx + certbot이나 인증서 설정이 더 번거롭다.
- 설정 개요:

```
이름.duckdns.org {
    handle /api/*    { reverse_proxy backend:8080 }
    handle /jenkins* { reverse_proxy jenkins:8080 }
    handle           { root * /srv/ui
                       try_files {path} /index.html
                       file_server }
}
```

- 실제 API 경로 접두사(`/api` 여부)는 구현 시 backend 컨트롤러 매핑과 `ui` 의 API 클라이언트 baseURL을 확인해 맞춘다. 접두사가 없다면 경로별 라우팅 규칙으로 바꾼다.

### 4.2 backend 이미지 (`backend/Dockerfile`)

- multi-stage: 1단계 Gradle(JDK)로 `bootJar` 빌드 → 2단계 JRE 슬림 이미지에서 jar 실행.
- **ARM64(aarch64)** 베이스 이미지를 사용한다(Oracle Ampere). 멀티아키 이미지를 쓰면 로컬 맥(Apple Silicon)과도 동일하다.
- 환경변수로 주입(이미지에 굽지 않는다): `DB_PASSWORD`, `APP_ENCRYPTION_KEY`(32바이트 키의 Base64), `SESSION_COOKIE_SECURE=true`, 가입 모드 `APP_SIGNUP_MODE`(운영 기본 `APPROVAL`).
- 비root 사용자로 실행한다.

### 4.3 UI 이미지 (`ui/Dockerfile`)

- multi-stage: pnpm으로 `build` → 산출물(`dist`)을 Caddy가 서빙할 공유 볼륨 또는 Caddy 이미지에 복사.
- 단순화 방안: UI 이미지 = `caddy` 베이스에 `dist`와 `Caddyfile`을 포함한 단일 이미지로 만들어 컨테이너 수를 줄인다.

### 4.4 운영 compose (`deploy/docker-compose.prod.yml`)

- 서비스: `caddy`(80/443 공개), `backend`(내부망만), `postgres`(내부망만, 호스트 포트 미공개), `jenkins`(내부망만, Caddy 경유).
- Postgres 데이터와 Caddy 인증서, Jenkins 홈은 **named volume**으로 영속화한다.
- `restart: unless-stopped`로 VM 재부팅 시 자동 복구한다.
- 시크릿은 VM의 `.env`(권한 600, git 미추적)에서 읽는다. Jenkins가 `compose up`을 하려면 이 파일을 읽어야 하므로, `.env`의 **호스트 경로를 Jenkins 컨테이너에 같은 경로로 마운트**하고 소유자·권한을 Jenkins 사용자에 맞춘다(§4.6 참조).

### 4.5 Terraform (`infra/`)

- provider: `oracle/oci`.
- 리소스: VCN, 인터넷 게이트웨이, 라우트 테이블, 서브넷, 보안 리스트(인바운드 22·80·443만), 예약 공인 IP, `VM.Standard.A1.Flex` 인스턴스(Always Free 한도 합계 2 OCPU/12GB 이내), cloud-init(Docker·compose 설치, 디렉터리 준비). 저장소 clone은 cloud-init에서 하지 않는다 — 저장소가 비공개면 인증이 필요하므로 코드 checkout은 Jenkins의 GitHub 자격증명(Deploy key 또는 토큰)이 맡는다.
- 변수: `tenancy_ocid`, `user_ocid`, `fingerprint`, `private_key_path`, `region`, `ssh_public_key`, `ssh_allowed_cidr`. 민감 값은 `terraform.tfvars`(git 제외)로 주입한다.
- **상태 파일**: 1인 사용이므로 로컬 `terraform.tfstate`로 시작한다. 상태 파일에는 민감정보가 있으므로 `.gitignore`에 추가하고 별도 백업한다. (원격 백엔드는 필요해질 때 도입)
- 무료 한도를 넘는 리소스(추가 볼륨, 로드밸런서, NAT 게이트웨이 등)는 만들지 않는다. `terraform plan` 결과에서 유료 리소스 유무를 매번 확인한다.

### 4.6 Jenkins 파이프라인 (`Jenkinsfile`)

단계:

1. **Checkout**
2. **Test**: backend `./gradlew test`(CI용 Postgres 테스트 컨테이너 필요 — §6 참조), UI `pnpm run test`/`lint`/`typecheck`
3. **Build**: `docker build`로 backend·UI 이미지 생성
4. **Deploy**: `docker compose -f deploy/docker-compose.prod.yml up -d`
5. **Smoke check**: 헬스 엔드포인트 호출, 실패 시 파이프라인 실패 처리

- 트리거: GitHub webhook(`main` 브랜치 push/PR 병합). 처음에는 수동 빌드로 시작하고 webhook은 나중에 붙인다.
- Jenkins 컨테이너가 호스트 Docker를 조종하도록 `/var/run/docker.sock`을 마운트한다.
- 빌드 캐시(Gradle·pnpm)는 볼륨으로 유지해 빌드 시간을 줄인다.

**Jenkins 컨테이너 함정 (로컬 연습 단계에서 먼저 겪고 푼다)**

1. **커스텀 이미지 필요**: 공식 `jenkins/jenkins` 이미지에는 docker CLI와 compose 플러그인이 없다. 이를 설치한 `deploy/jenkins/Dockerfile`을 직접 만든다(ARM64 대응).
2. **볼륨 경로는 호스트 기준**: 컨테이너 안에서 `docker compose up`을 하면 데몬은 호스트에 있으므로 compose의 bind mount 경로가 *호스트 경로*로 해석된다. 워크스페이스·`.env`를 컨테이너 내부와 호스트에서 **같은 절대경로**로 마운트하거나, bind mount 대신 named volume·이미지 COPY만 쓴다.
3. **`/jenkins` 접두사**: Caddy로 `/jenkins` 아래에 노출하려면 Jenkins도 `--prefix=/jenkins`(또는 `JENKINS_OPTS`)로 띄워야 한다. 접두사를 맞추지 않으면 리다이렉트·정적 파일이 깨진다.
4. **docker 그룹 권한**: Jenkins 사용자가 docker.sock에 접근할 수 있도록 GID를 맞춘다.

## 5. 배포 흐름 (코드 한 줄 수정 시)

1. 맥북에서 `git push` (PR 병합 시 `main`)
2. GitHub webhook → Jenkins
3. Jenkins: 테스트 → 실패 시 중단 / 성공 시 이미지 빌드 → compose 교체 배포
4. 수 분 내 새 버전 반영

## 6. 알려진 제약과 리스크

| 항목 | 내용 | 대응 |
|---|---|---|
| 가입 | 카드 인증 필요, 가입 거절 사례, **Home Region 변경 불가** | 서울/춘천 중 선택. 막히면 대안 B(집 PC + Cloudflare Tunnel) |
| ARM 용량 | 지역에 따라 "Out of capacity" 발생 가능 | 재시도·다른 availability domain. 작은 VM만 확보 시 Jenkins 제외 |
| Jenkins 메모리 | 1~2GB 소요. VM이 작으면 빠듯 | 12GB면 동거 가능. 6GB(1 OCPU)는 빠듯해서 스왑을 잡거나 Jenkins를 로컬에 둔다 |
| docker.sock 마운트 | Jenkins 침해 시 VM 전체 위험 | 초기 비밀번호 변경, 가입 차단, 관리자 화면 접근 제한 |
| Jenkins 보안 vs webhook | 관리자 화면을 IP로 제한하면 GitHub webhook도 막힌다 | Caddy에서 webhook 경로(`/jenkins/github-webhook/`)만 공개하고 나머지는 내 IP로 제한. 또는 초기에는 수동 빌드 |
| CI 테스트 DB | 현재 테스트는 `localhost:5433` 공유 Postgres 전제(Testcontainers 미사용) | 파이프라인에서 테스트용 Postgres 컨테이너를 띄우고 `DB_NAME`/포트 지정 |
| 유휴 회수 | Always Free VM은 장기 저사용 시 회수될 수 있음 | 상시 서비스(Jenkins·앱)로 최소 사용량 유지, 정책 주기적 확인 |
| 단일 장애점 | VM 1대, 백업 없음 | Postgres 정기 `pg_dump`를 Oracle Object Storage(무료 한도 내)에 보관 — 후속 작업 |
| 도메인 | DuckDNS 무료 서브도메인 | 서비스 종료 위험은 수용. 필요 시 유료 도메인 없이 교체 가능하도록 도메인을 변수화 |
| 초기 수동 작업 | Jenkins 최초 설정(플러그인, GitHub 연결) | 1회 수동, 이후 Jenkins 설정은 문서화 |

## 7. 사용자 선행 작업

1. Oracle Cloud 계정 생성(Home Region 신중히 선택)
2. DuckDNS 도메인 발급
3. OCI API 키 발급(`tenancy_ocid`, `user_ocid`, `fingerprint`, 비밀키)
4. 로컬 Terraform 설치(`brew install terraform`)

## 8. 구현 단계

목적은 두 가지다: **A. Terraform 도구 자체 익히기**, **B. 개인 프로젝트 실제 무료 배포**. 첫 관문이 Oracle 가입·서버 확보(막힐 수 있음)가 되지 않도록, 가입이 필요 없는 로컬 단계를 앞에 둔다.

| 단계 | 목적 | 산출물 | 검증 |
|---|---|---|---|
| 0 | A | 로컬 실습(Terraform `docker` provider로 Postgres 컨테이너 생성). 학습 후 삭제, 예제는 커밋 `fc85452` 이력에 보존 | `init/plan/apply/destroy` 숙달, plan의 create/update/replace 읽기, `tfstate` 내용 확인, 컨테이너를 손으로 지운 뒤 plan(drift) 관찰 |
| 1 | B 준비 | `backend/Dockerfile`, `ui/Dockerfile`, `deploy/docker-compose.prod.yml`, Caddyfile | 로컬(맥)에서 compose 기동 후 로그인·화면 동작 확인 |
| 2 | B 준비 | `deploy/jenkins/Dockerfile`, `Jenkinsfile` — **로컬 Jenkins 컨테이너**로 테스트→빌드→`compose up` 연습 | 수동 빌드로 로컬 배포 성공, 테스트 실패 시 배포 중단, §4.6 함정 4개 해결 |
| 3 | A·B | `infra/*.tf`, cloud-init (Oracle 가입 후) | `plan`에서 유료 리소스 없음 → `apply` → SSH 접속·Docker 동작. 가입이 막히면 이 단계만 대안 B(집 PC + Cloudflare Tunnel)로 교체 |
| 4 | B | 2단계 파이프라인을 VM으로 이전, webhook 연결 | 실제 도메인에서 서비스, push 시 자동 배포 |
| 5 (후속) | B | DB 백업, 모니터링 | 복구 리허설 |

선택 확장: 0단계 이후 연습용 폴더를 `shared/`(공통 output)와 `env-dev/`(`terraform_remote_state`로 읽음)로 나눠 보면 모듈·환경 분리 구조도 연습할 수 있다.

## 9. 범위 밖

- 고가용성·오토스케일·멀티 리전
- 유료 관리형 서비스(RDS류, 로드밸런서, 관리형 CI)
- Kubernetes
- 일반 공개 배포(법률 검토 전까지 보류)

## 10. 결정 사항

| ID | 결정 | 근거 |
|---|---|---|
| D-DEP-001 | 호스팅은 Oracle Cloud Always Free, 실패 시 집 PC + Cloudflare Tunnel | 영구 무료 + Terraform 지원 |
| D-DEP-002 | 앱·Postgres·Jenkins를 VM 1대에 Docker compose로 동거 | 비용 0원, 동일 도메인 쿠키 요구, SSH 없는 배포 |
| D-DEP-003 | Caddy를 리버스 프록시로 사용 | 자동 HTTPS, 설정 최소화 |
| D-DEP-004 | Terraform은 인프라 생성 전용, 배포는 Jenkins 전담 | 책임 분리 |
| D-DEP-005 | Terraform 상태는 로컬 파일로 시작 | 1인 사용, 필요 시 원격 백엔드 도입 |
| D-DEP-006 | 목적은 A(Terraform 학습)+B(실배포) 둘 다. 로컬 `docker` provider 실습(0단계)을 맨 앞에 둔다 | Oracle 가입·용량 문제로 학습이 막히는 위험 제거 |
| D-DEP-007 | Jenkins는 로컬 컨테이너로 먼저 연습하고 VM 이전은 후속 | docker.sock·경로·접두사 함정을 로컬에서 먼저 해결 |
| D-DEP-008 | 코드 checkout은 Jenkins 자격증명이 담당, cloud-init은 clone하지 않음 | 비공개 저장소 인증 문제 회피 |
