# 집 PC 운영 가이드 (Docker)

현재 운영 계획은 Oracle VM이 아니라 **집에 남는 PC + Docker**다. 무료, 공유기 포트포워딩 없이 HTTPS로 접속한다. 시크릿 값은 이 문서에 적지 않는다.

## 1. 준비물

- 상시 켜 둘 PC 한 대: RAM 4GB 이상(8GB 권장, 첫 빌드는 Gradle·pnpm 때문에 메모리를 쓴다), 여유 디스크 20GB 이상, 유선 LAN 권장.
- 인터넷 회선(공인 IP 불필요), 터널용 계정 하나: Tailscale(도메인 없음) 또는 Cloudflare(내 도메인 있음).
- 키 3개: `OPENDART_API_KEY`, `DATA_GO_KR_SERVICE_KEY`, `APP_ENCRYPTION_BROKER_KEY`(`openssl rand -base64 32`로 직접 생성). 추가로 `APP_ENCRYPTION_KEY`(같은 방식 생성)와 `DB_PASSWORD`.

## 2. OS: Ubuntu Server 24.04 LTS 권장

서버로는 우분투가 가장 단순하다(Docker 네이티브, systemd 자동 시작, 메모리 여유). 설치 시 OpenSSH만 추가한다. 윈도우를 그대로 써야 하면 13장.

## 3. Docker 설치 (우분투)

```bash
curl -fsSL https://get.docker.com | sudo sh        # Docker Engine + compose 플러그인
sudo usermod -aG docker $USER && newgrp docker      # sudo 없이 docker 사용
sudo systemctl enable --now docker containerd       # 부팅 때 자동 시작(대개 이미 enable)
sudo apt install -y git cron
```

모든 서비스는 `restart: unless-stopped`라 도커가 부팅 때 올라오면 스택도 함께 올라온다. 컨테이너 로그는 서비스당 30MB로 제한돼 있다(compose `logging`).

## 4. 저장소 받기와 `deploy/.env`

```bash
git clone <저장소 주소> ~/vibe-ai-team && cd ~/vibe-ai-team
cp deploy/.env.example deploy/.env && chmod 600 deploy/.env
```

`deploy/.env`에서 채울 것: `DB_PASSWORD`, `APP_ENCRYPTION_KEY`, `APP_ENCRYPTION_BROKER_KEY`, `OPENDART_API_KEY`, `DATA_GO_KR_SERVICE_KEY`. 터널을 쓰면 `SITE_ADDRESS=:80`, `SESSION_COOKIE_SECURE=true`(그대로)로 둔다. `APP_ENCRYPTION_*` 키는 **분실하면 저장된 증권사 키를 복호화할 수 없으니** 비밀번호 관리자 등 PC 밖에 따로 보관한다.

## 5. 첫 배포: Jenkins 없이 직접 `compose up` 권장

"운영은 Jenkins로만 재배포"라는 기존 규칙의 이유는 **DB 비밀번호 일치**다. Postgres는 볼륨을 처음 만들 때의 `DB_PASSWORD`를 데이터 안에 굳히므로, Jenkins와 사람이 서로 다른 `.env`로 올리면 backend가 DB에 못 붙는다. 그래서 "배포 경로를 하나로" 통일하는 것이 본질이고, 그 하나가 꼭 Jenkins일 필요는 없다.

집 PC에서는 **직접 `docker compose`를 그 하나의 경로로 쓰기로 정했다.** 이유: Jenkins 파이프라인은 백엔드 테스트·E2E·접근성까지 돌려 메모리·시간이 크고 로컬 Postgres(5433)·로컬 경로 체크아웃 가정이 있어 남는 PC에 과하다. 테스트는 개발 맥에서 PR 단계에 이미 통과시킨 `main`만 받아 올린다. 규칙: 이 PC에서는 항상 아래 같은 명령(프로젝트 이름 `vibe-prod`, 같은 `deploy/.env`)만 쓴다. 컨테이너 이름이 `vibe-prod-postgres-1`이 돼 백업 스크립트 기본값과 맞는다.

```bash
cd ~/vibe-ai-team/deploy
docker compose -p vibe-prod -f docker-compose.prod.yml up -d --build            # 터널 없음(LAN에서 http://PC주소:HTTP_PORT)
```

첫 빌드는 10분 안팎. `docker compose -p vibe-prod ps`에서 backend가 `healthy`면 정상이다. Jenkins(:18081)는 이 PC에 올리지 않는다. 올린다면 18081은 외부로 열지 않는다(11장).

## 6. 외부 접속 + HTTPS: 터널 중 하나 선택

두 방식 모두 TLS를 터널이 종료하고, Caddy는 `SITE_ADDRESS=:80` HTTP만 받는다(override가 설정). override가 `TRUSTED_PROXIES=private_ranges`를 켜서 Caddy가 터널이 넘긴 실제 방문자 IP를 해석하고, 그 값을 backend로 `X-Forwarded-For`에 담아 보낸다(backend `APP_RATE_LIMIT_TRUST_FORWARDED`는 prod compose 기본 true). 브라우저는 https로 접속하므로 `Secure` 세션 쿠키가 그대로 동작한다. 공유기 포트포워딩은 필요 없다.

### A. Tailscale Funnel (도메인 없음, `https://<기기이름>.<tailnet>.ts.net`)

1. tailscale.com 가입 → 관리 콘솔 DNS에서 **MagicDNS와 HTTPS Certificates 켜기**.
2. Access controls(ACL)에 Funnel 허용(`nodeAttrs`에 `"attr": ["funnel"]`, 콘솔의 Funnel 안내 버튼으로 추가 가능).
3. Settings > Keys에서 인증키(auth key) 발급 → `deploy/.env`에 `TS_AUTHKEY`, 원하는 `TS_HOSTNAME` 기록.
4. 실행:
   ```bash
   docker compose -p vibe-prod -f docker-compose.prod.yml -f docker-compose.tailscale.yml up -d --build
   ```
5. 주소 `https://<TS_HOSTNAME>.<tailnet>.ts.net` 접속 확인. 인증키는 첫 등록에만 쓰이고 상태는 볼륨에 저장된다.

### B. Cloudflare Tunnel (내 도메인이 Cloudflare에 있을 때)

1. Cloudflare Zero Trust > Networks > Tunnels에서 터널 생성(Docker 선택) → 토큰을 `deploy/.env`의 `TUNNEL_TOKEN`에.
2. 터널의 Public hostname에 원하는 (서브)도메인, 서비스는 `HTTP` / `web:80`.
3. 실행:
   ```bash
   docker compose -p vibe-prod -f docker-compose.prod.yml -f docker-compose.cloudflared.yml up -d --build
   ```

(override 없이 올리면 지금 로컬 운영 스택과 설정이 동일하다.) 이후 모든 `up`·`pull`·`down`에는 같은 `-f` 조합을 붙인다.

## 7. 첫 관리자 만들기

앱에서 가입한 뒤(가입은 기본 `APPROVAL`이라 승인 대기), 운영 컨테이너를 대상으로 승격한다.

```bash
PG_CONTAINER=vibe-prod-postgres-1 scripts/promote-admin.sh 내이메일@example.com
```

다시 로그인한 뒤 관리자로 `POST /api/ipos/insight/sync`를 1회 실행해 공모주 일정을 채운다.

## 8. 백업 (cron, PC 밖 사본, 복원 리허설)

`scripts/backup-db.sh`는 압축 덤프를 날짜별로 `~/vibe-backups`에 남기고 최근 14개만 보관한다(보관 개수는 두 번째 인자).

```bash
mkdir -p ~/vibe-backups && crontab -e
# 매일 03:30
30 3 * * * /home/<사용자>/vibe-ai-team/scripts/backup-db.sh >> /home/<사용자>/vibe-backups/backup.log 2>&1
```

**PC 밖 복사(선택, 권장)**: 디스크가 죽으면 백업도 같이 사라진다. `sudo apt install rclone` → `rclone config`로 구글 드라이브 원격(예: `gdrive`) 생성 → cron 줄 앞에 `BACKUP_REMOTE=gdrive:vibe-backups`를 붙이면 새 백업을 `rclone copy` 한다(원격 복사 실패 시 종료 코드 2와 로그). 덤프에는 암호화된 증권사 키가 들어 있으니 계정은 본인 전용으로 쓰고, `APP_ENCRYPTION_*` 키 없이는 그 값을 못 푼다는 점을 기억한다.

**복원 리허설(분기에 1회)**: 임시 DB로 복원해 본다 — `docker exec vibe-prod-postgres-1 createdb -U vibe rehearsal` → `scripts/restore-db.sh ~/vibe-backups/<최신>.sql.gz rehearsal` → `docker exec vibe-prod-postgres-1 psql -U vibe -d rehearsal -c "select count(*) from accounts"`로 행이 보이는지 확인 → `dropdb -U vibe rehearsal`. 실제 장애 복구는 `docker compose -p vibe-prod ... stop backend` 후 같은 스크립트를 대상 DB 기본값으로 실행하고 backend를 다시 올린다.

## 9. 업데이트 방법

```bash
cd ~/vibe-ai-team && git pull --ff-only
cd deploy && docker compose -p vibe-prod -f docker-compose.prod.yml [-f 터널파일] up -d --build
docker compose -p vibe-prod ps          # backend healthy 확인
```

업데이트 전에 `scripts/backup-db.sh`를 한 번 돌린다. 이미지 정리: `docker image prune -f`. OS는 `sudo apt install unattended-upgrades`로 보안 패치를 자동화하고, 커널 업데이트 후엔 가끔 재부팅한다(스택은 자동 복귀).

## 10. 절전 끄기와 정전 대비

- 우분투 서버는 기본적으로 절전하지 않는다. 노트북이면 덮개 닫음 무시: `/etc/systemd/logind.conf`의 `HandleLidSwitch=ignore`, `HandleLidSwitchExternalPower=ignore` 후 `sudo systemctl restart systemd-logind`. 데스크톱 GUI 설치라면 `sudo systemctl mask sleep.target suspend.target hibernate.target`.
- BIOS/UEFI에서 **AC Power Recovery(Restore on AC Power Loss) = Power On**으로 설정해 정전 후 자동 켜짐. 공유기·모뎀도 같이 복구되어야 한다. 가능하면 소형 UPS.

## 11. 보안

- **5432(Postgres)·18081(Jenkins)·backend 8080을 외부에 노출하지 않는다.** compose는 postgres·backend 포트를 호스트에 열지 않으니 그대로 둔다. 공유기 포트포워딩·DMZ를 켜지 말고(터널이 대신한다), 우분투 방화벽은 `sudo ufw allow OpenSSH && sudo ufw enable`로 SSH만 열어도 터널 방식엔 지장 없다(터널은 PC에서 밖으로 나가는 연결). 단 `HTTP_PORT`는 호스트에 열려 있어 LAN에서 보인다.
- SSH는 키 인증만(`PasswordAuthentication no`). `deploy/.env`는 `chmod 600`, 저장소·채팅에 올리지 않는다. 한 번이라도 노출된 키는 재발급한다.
- Tailscale 방식은 Funnel로 인터넷 공개이므로 가입 모드 `APP_SIGNUP_MODE=APPROVAL`(기본)을 유지한다.

## 12. 증권사 IP 등록 주의 (키움)

키움은 호출 서버의 **공인 IP를 등록**해야 하는데 집 회선은 보통 유동 IP라 재접속·재부팅·ISP 정책으로 바뀔 수 있다. 바뀌면 키움 조회만 실패하니 `curl ifconfig.me`로 현재 IP를 확인해 재등록한다. 고정 IP는 유료라 비용 0원 원칙상 권하지 않는다. KIS·LS는 같은 제약이 없다면 영향 없음.

## 13. 윈도우 + Docker Desktop (우분투를 못 쓸 때)

- WSL2 백엔드로 Docker Desktop 설치(Windows 10/11 Pro·Home 모두 가능), 명령은 WSL 우분투 터미널에서 위 절차대로 실행(저장소도 WSL 홈 `~/` 안에 두어야 빌드가 빠르다).
- Settings > General에서 **Start Docker Desktop when you sign in** 켜기. 단 이것은 *로그인 후* 시작이므로 PC가 자동 로그인(netplwiz)되거나 로그인된 채 두어야 정전 후 복구된다. 서비스 모드로 부팅 직후 시작되는 우분투보다 약하다.
- 전원 옵션에서 절전·최대 절전 끄기, **Windows Update 자동 재시작 시간**을 새벽으로 지정. WSL2는 메모리를 많이 잡으니 `%UserProfile%\.wslconfig`의 `memory=4GB`로 제한.
- cron이 없으므로 백업은 WSL 안에서 `sudo service cron start`를 쓰거나 작업 스케줄러에서 `wsl -e /home/<사용자>/vibe-ai-team/scripts/backup-db.sh`를 매일 실행하게 한다.
