# 인수인계: 무료 배포(Terraform + Docker + Jenkins) — 2026-10-12 기준

> 이 문서만 읽고 이어서 작업할 수 있게 쓴 인수인계 문서다. 설계의 "왜"는 `docs/architecture/free-deployment-terraform-jenkins.md`, 시간순 기록은 `docs/work-log/free-deployment-terraform-jenkins.md`, 설치·복구 절차는 `docs/setup-history.md`를 본다. 시크릿 값(키, 비밀번호, OCID)은 이 문서에 적지 않았다.

## 1. 목표와 제약

- 목표: 개인용 공모주 수익 관리 서비스를 **Terraform + Docker + Jenkins**로 배포한다. 목적은 둘 다다 — (A) Terraform 도구 익히기, (B) 실제 배포.
- **제약(사용자 결정): 비용 0원.** 유료 서비스·유료 shape·유료 플랜 전환 금지. 사용자는 개인이라 돈이 드는 선택지는 쓰지 않는다.
- 일반 공개 배포는 KIS 약관 법률 검토 전까지 보류 상태(`docs/current-status.md`). 개인용 비공개 운영이다.
- 이 저장소의 `main` 체크아웃은 코드를 직접 작업하는 곳이 아니다(CLAUDE.md). 새 작업은 브랜치/worktree에서 하고 PR로 병합한다.

## 2. 한눈에 보는 현재 상태

| 단계 | 내용 | 상태 |
|---|---|---|
| 0 | Terraform `docker` provider 로컬 실습 | 완료, 폴더 삭제(이력 `fc85452`) |
| 1 | Dockerfile·Caddyfile·`deploy/docker-compose.prod.yml` | 완료, **main에 병합**(#302) |
| 2 | 로컬 Jenkins + `Jenkinsfile` 파이프라인 | 완료, **main에 병합**(#302). 실패 시 배포 중단까지 검증함 |
| 3 | Oracle Cloud에 VM을 Terraform으로 생성 | **막힘: VM 생성이 용량 부족으로 계속 실패**(§5) |
| 4 | 파이프라인을 VM으로 이전, webhook, Jenkins를 운영 compose에 합침 | 3단계 대기 |
| 5 | DB 백업·모니터링 | 후속 |

### 미커밋 변경 (main 작업 트리에만 있음 — 가장 먼저 처리할 것)

`/Users/joy/IdeaProjects/vibe-ai-team`(main 체크아웃)에 아래가 **커밋되지 않은 채** 있다. 다른 체크아웃/worktree에서는 보이지 않는다.

- `infra/oracle/` 전체(Terraform 코드) — `.tf` 6개, `cloud-init.yaml`, `terraform.tfvars.example`
- `docs/setup-history.md`(Oracle 가입·키 항목 1줄), `docs/architecture/free-deployment-terraform-jenkins.md`(무료 한도 정정), 이 문서

→ **새 브랜치(예: `feat/oracle-infra`)로 옮겨 커밋하고 PR을 올린다.** `terraform.tfvars`·`*.tfstate*`는 `.gitignore`로 제외돼 커밋되지 않는다(정상).

### 로컬에서 지금 돌고 있는 것

- `vibe-prod-{web,backend,postgres}-1` — Jenkins가 배포한 앱 스택. `http://localhost:18080`
- `jenkins-jenkins-1` — 로컬 Jenkins. `http://localhost:18081/jenkins`, 계정 `admin`, 비밀번호는 `deploy/.env`의 `JENKINS_ADMIN_PASSWORD`
- 정리 명령은 `docs/setup-history.md` "배포 스택 로컬 기동" 절에 있다. 사용자가 정리하라고 하기 전에는 내리지 않는다.

## 3. 만들어진 것 (파일 지도)

```
backend/Dockerfile, .dockerignore   Gradle bootJar → JRE alpine, 비root
ui/Dockerfile, Caddyfile, .dockerignore   pnpm 빌드(VITE_API_BASE_URL=/api) → Caddy 이미지
deploy/docker-compose.prod.yml      postgres + backend + web(Caddy), 시크릿은 deploy/.env
deploy/.env.example                 필요한 환경변수 이름 목록 (.env는 git 제외)
deploy/jenkins/                     Dockerfile(docker CLI·compose·node·pnpm), plugins.txt, casc.yaml(JCasC+Job DSL), docker-compose.yml
Jenkinsfile                         백엔드 테스트(ci-pg) → UI lint/typecheck/test → compose 배포 → smoke
infra/oracle/                       (미커밋) Terraform: VCN·IGW·라우트·보안리스트·서브넷·VM, cloud-init
```

### 구조의 핵심 결정과 그 이유

- **VM 1대에 compose로 동거**: 앱·Postgres·(나중에)Jenkins. 비용 0원 + 세션 쿠키 요구.
- **Caddy가 `/api/*`를 떼고 backend로 프록시**: backend에는 `/api` 접두사가 없고(`/auth`, `/investors` …) UI 라우트와 이름이 겹친다. 세션 쿠키가 `SameSite=Lax`라 UI·API가 같은 도메인이어야 한다. 운영 도메인은 `stocklog.duckdns.org`(DuckDNS), 자동 HTTPS는 Caddy가 처리.
- **Caddyfile을 이미지에 포함**(bind mount 안 함): Jenkins 컨테이너가 `docker.sock`으로 `compose up`을 하면 bind mount 경로가 *호스트 기준*으로 해석돼 어긋난다.
- **DB 주소는 `SPRING_DATASOURCE_URL`로 덮어씀**: `application.yml`이 `localhost:5433`으로 고정돼 있다. 코드는 건드리지 않았다.
- Terraform은 인프라 생성 전용, 배포는 Jenkins 전담. Terraform 상태는 로컬 파일.

## 4. 이어서 작업하는 방법

### 앱 스택 로컬 기동 / 검증
- `docs/setup-history.md`의 "배포 스택 로컬 기동 (2026-10-01 추가)" 절을 순서대로 따른다.
- 검증된 것: 가입→(DB에서 `approved=TRUE`)→로그인→세션 쿠키로 `/api/auth/me` 200. 가입 모드 기본값이 `APPROVAL`이라 로컬 테스트 때는 승인 처리가 필요하다.
- smoke는 backend에 actuator가 없어서 "비로그인 `/auth/me`가 401"로 대신한다.

### Jenkins 파이프라인
- Jenkins는 **git에 커밋된 `Jenkinsfile`만** 읽는다. 수정 후 반드시 커밋.
- 잡이 보는 브랜치는 `deploy/.env`의 `JOB_BRANCH`(현재 `main`). 바꾸면 `docker compose -f deploy/jenkins/docker-compose.yml --env-file deploy/.env up -d`로 재생성.
- 로컬 연습용 설정: `file:///repo` 체크아웃을 위해 `GitSCM.ALLOW_LOCAL_CHECKOUT=true`, `docker.sock` 권한은 `group_add: 0`(Docker Desktop). **운영(VM)에서는 이 옵션을 제거**하고 GitHub 주소를 쓴다. Linux VM에서는 `DOCKER_GID=$(stat -c %g /var/run/docker.sock)`.
- 검증 완료: 빌드 성공(백엔드 테스트 통과 → UI 테스트 → 배포 → smoke), 의도적으로 UI 테스트를 깨뜨리면 `Deploy`·`Smoke`가 skipped.
- 미검증: GitHub webhook 트리거, Caddy 뒤 `/jenkins` 접두사 동작.

### Terraform (Oracle)
```bash
cd infra/oracle
terraform init        # provider oracle/oci ~> 7.0
terraform plan        # 변경 없으면 VM 1개 추가만 나온다
terraform apply -auto-approve -var ocpus=2 -var memory_gbs=12   # 안 되면 ocpus=1 memory_gbs=6
```
- 필요한 값은 `terraform.tfvars`(git 제외, 사용자 PC의 `infra/oracle/`에 있음). 형식은 `terraform.tfvars.example`. API 개인키는 `~/.oci/oci_api_key.pem`, VM 접속용 SSH 키는 `~/.ssh/oracle_vm`(공개키 `.pub`).
- **Terraform state는 `infra/oracle/terraform.tfstate`(로컬 파일, git 제외)에만 있다.** 이미 Oracle에 네트워크 5개(VCN·IGW·라우트 테이블·보안 리스트·서브넷)가 만들어져 있고 그 정보가 이 파일에 있다. **다른 체크아웃에서 작업하면 state가 없어서 네트워크를 또 만들려고 한다.** 같은 체크아웃에서 이어가거나 state 파일을 복사해야 한다.

## 5. 막힌 지점: Oracle VM 생성 실패

- 증상: `500-InternalError, Out of host capacity.` — `VM.Standard.A1.Flex` 2 OCPU/12GB, 1 OCPU/6GB 모두. 10/02부터 10/12까지 약 40회 시도, **한 번도 성공 못 함**.
- 리전은 `ap-osaka-1`(가입 시 Home Region, 변경 불가). 오사카는 가용 도메인이 1개라 다른 AD로 우회할 수 없다.
- 코드·인증·한도 문제가 아니다. `plan`은 통과하고(인증 정상), 네트워크 생성은 성공했다. 순수하게 무료 ARM 자리가 없다.
- 시도 도중 한 번 `terraform apply`가 17분 넘게 `Creating...`에서 멈췄다가 `Plugin did not respond`로 죽었다. Oracle을 직접 조회해 VM이 생기지 않은 것을 확인했다. **그 뒤로 apply에는 5분 시간 제한을 건다**(아래 주의 참고).
- 현재 Oracle에는 VM이 없고 네트워크 5개만 있다(전부 무료).

### 선택지 (사용자가 아직 고르지 않음 — 반드시 물어볼 것)

| 안 | 내용 | 비고 |
|---|---|---|
| **AMD Micro 2대** (추천) | `VM.Standard.E2.1.Micro` 최대 2대 무료. 앱(backend+Caddy)과 DB(Postgres)를 한 대씩 | RAM 1GB라 JVM 힙 256~384MB 제한 + 스왑 2GB 필요. 실제로 돌려봐야 확실. Jenkins는 올리지 못함(로컬 유지). Terraform 코드를 Micro 2대 구성으로 고쳐야 함 |
| 계속 기다리기 | 가끔(특히 새벽) 수동 시도 | 언제 풀릴지 모름 |
| 다른 무료 호스팅 | Cloud Run + Neon + Cloudflare Pages 등 | 쿠키 도메인 문제로 구조 변경 필요, 과금 위험 |

사용자에게는 집에 쓸 PC가 없다고 들었다(집 PC + Cloudflare Tunnel 안은 불가).

## 6. 반드시 지킬 것 (실수했거나 위험했던 것들)

1. **무료 한도를 넘기지 않는다.** 공식 문서(`docs.oracle.com/.../freetier_topic-Always_Free_Resources.htm`, 2026-10-02 확인) 기준 A1은 **합계 2 OCPU / 12GB**, 블록 스토리지 합계 200GB, AMD Micro는 `VM.Standard.E2.1.Micro` 최대 2대. 이전에 "4 OCPU/24GB"라고 안내했던 것은 옛 한도로 틀렸다. `VM.Standard.E5.Flex` 같은 shape는 유료다. 콘솔에서 "Always Free-eligible" 표시가 있는 것만 쓴다.
2. **공인 IP는 임시(ephemeral) IP**로 했다(무료 보장을 위해, 설계 문서의 "예약 IP"에서 변경). VM을 삭제·재생성하면 IP가 바뀌므로 DuckDNS를 다시 갱신해야 한다.
3. **DuckDNS `stocklog`의 current ip는 아직 VM IP가 아니다**(DuckDNS가 자동으로 넣은 값). VM이 만들어지면 사용자가 DuckDNS 화면에서 그 IP로 바꾸게 안내한다.
4. **macOS에는 `timeout` 명령이 없다.** apply에 시간 제한을 걸 때는 `terraform apply ... & pid=$!; (sleep 300; kill -INT $pid) & wait $pid` 패턴을 쓴다. `apply`를 병렬로 돌리지 않는다(state 잠금·중복 생성 위험).
5. **자동 반복 재시도는 쓰지 말 것.** 세션 기반 cron은 작업이 밀려 한꺼번에 실행됐고 API가 멈추면 위험했다. 필요하면 사람이 한 번씩 돌린다. (현재 예약은 모두 삭제됨.)
6. **비밀을 커밋·기록하지 않는다.** `deploy/.env`, `infra/oracle/terraform.tfvars`, `*.tfstate*`, `~/.oci/oci_api_key.pem`, `~/.ssh/oracle_vm`은 전부 저장소 밖이거나 `.gitignore` 대상이다. 문서에는 환경변수 **이름**과 파일 **경로**만 적는다. 사용자의 Oracle 로그인 계정 정보(이메일, 비밀번호)는 받은 적도 기록한 적도 없다.
7. **설치·실행하면 즉시 `docs/setup-history.md`에 적는다**(이 저장소의 규칙, AGENTS.md 9.2). 1~2단계 때 한 번 놓쳐서 소급 기록했다.
8. OCI API 키는 사용자당 3개까지만 만들 수 있다(`api-key-count` 한도 에러). 현재 키는 사용자가 이미 발급해 `~/.oci/`에 있다.
9. 커밋 메시지·PR에는 저장소 규칙의 attribution 줄을 붙인다. main에는 직접 커밋하지 않는다.

## 7. 다음 작업 제안 (우선순위 순)

1. 미커밋 변경을 새 브랜치로 옮겨 커밋·PR (§2).
2. 사용자와 §5 선택지 확정. AMD Micro 2대로 가면:
   - `infra/oracle/compute.tf`를 `VM.Standard.E2.1.Micro` 2대(앱용, DB용)로 수정, 보안 리스트에서 DB 포트(5432)는 앱 VM에서만 접근되도록 제한, cloud-init에 스왑 2GB 추가.
   - 운영 compose를 앱 VM용·DB VM용으로 나누거나, 같은 compose를 쓰되 서비스를 분리. backend 힙 제한(`JAVA_TOOL_OPTIONS=-Xmx...`) 추가.
   - `plan`에서 유료 리소스가 없는지 확인 후 사용자 승인을 받고 `apply`.
3. VM 접속(`ssh -i ~/.ssh/oracle_vm ubuntu@<IP>`) → Docker 동작 확인 → DuckDNS IP 갱신 → 앱 기동 → HTTPS 확인.
4. 파이프라인을 VM 대상으로 이전(Jenkins는 로컬 유지 시 SSH로 배포하는 방식으로 변경 필요), webhook 연결.
5. 각 단계마다 `docs/setup-history.md`·work-log 갱신.
