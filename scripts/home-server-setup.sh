#!/usr/bin/env bash
# 우분투 PC 한 번에 설치: Docker, 저장소, deploy/.env(키 자동 생성), 스택 기동, 백업 cron, 절전 끄기.
# 멱등: 여러 번 실행해도 안전하다(기존 .env 값은 절대 덮어쓰지 않는다).
# 사용: scripts/home-server-setup.sh   (저장소 밖이면 REPO_URL·INSTALL_DIR로 clone)
# 환경변수: TUNNEL=tailscale|cloudflared|none  DISABLE_SLEEP=1(기본)  DRY_RUN=1(설치·기동·cron 생략, 테스트용)
#          OPENDART_API_KEY 등을 환경변수로 주면 .env의 빈 칸에 채운다. 값은 출력하지 않는다.
set -euo pipefail

REPO_URL=${1:-${REPO_URL:-https://github.com/winter0123/vibe-ai-team.git}}
INSTALL_DIR=${2:-${INSTALL_DIR:-$HOME/vibe-ai-team}}
DISABLE_SLEEP=${DISABLE_SLEEP:-1}
DRY_RUN=${DRY_RUN:-0}
step() { printf '\n==> %s\n' "$*"; }
ok()   { printf '    [건너뜀/이미 됨] %s\n' "$*"; }
die()  { printf '오류: %s\n' "$*" >&2; exit 1; }

SUDO=""; [ "$(id -u)" -eq 0 ] || SUDO="sudo"
run() { if [ "$DRY_RUN" = 1 ]; then echo "    [DRY_RUN] $*"; else "$@"; fi; }

# ---------- 1. OS ----------
step "1/10 OS 확인"
[ -r /etc/os-release ] || die "/etc/os-release 없음. 우분투가 아닌 것 같다."
# shellcheck disable=SC1091
. /etc/os-release
if [ "${ID:-}" != ubuntu ]; then
  die "우분투 전용 스크립트다(감지: ${PRETTY_NAME:-알 수 없음}). docs/home-server.md의 수동 절차를 따르라."
fi
echo "    ${PRETTY_NAME}"
case "${VERSION_ID:-}" in 22.04|24.04) ;; *) echo "    경고: 22.04/24.04 외 버전은 검증되지 않았다." ;; esac
if [ "$DRY_RUN" != 1 ] && [ -n "$SUDO" ] && ! command -v sudo >/dev/null; then die "root가 아니면 sudo가 필요하다."; fi

# ---------- 2. Docker ----------
step "2/10 Docker Engine + compose 플러그인"
if [ "$DRY_RUN" = 1 ]; then echo "    [DRY_RUN] 생략"
elif command -v docker >/dev/null && docker compose version >/dev/null 2>&1; then
  ok "docker $(docker --version | cut -d' ' -f3 | tr -d ,)"
else
  $SUDO apt-get update -y
  $SUDO apt-get install -y ca-certificates curl gnupg
  $SUDO install -m 0755 -d /etc/apt/keyrings
  $SUDO curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
  $SUDO chmod a+r /etc/apt/keyrings/docker.asc
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu ${UBUNTU_CODENAME:-$VERSION_CODENAME} stable" \
    | $SUDO tee /etc/apt/sources.list.d/docker.list >/dev/null
  $SUDO apt-get update -y
  $SUDO apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
fi
if [ "$DRY_RUN" != 1 ]; then
  $SUDO systemctl enable --now docker
  if [ "$(id -u)" -ne 0 ] && ! id -nG | tr ' ' '\n' | grep -qx docker; then
    $SUDO usermod -aG docker "$USER"
    echo "    docker 그룹에 $USER 추가함(새 로그인부터 sudo 없이 사용. 이번 실행은 sudo docker로 진행)."
  fi
fi
DOCKER=(docker)
if [ "$DRY_RUN" != 1 ] && ! docker info >/dev/null 2>&1; then DOCKER=($SUDO docker); fi

# ---------- 3. git + 저장소 ----------
step "3/10 git과 저장소"
if ! command -v git >/dev/null; then run $SUDO apt-get install -y git; else ok "git"; fi
SELF_DIR=$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" 2>/dev/null && pwd || true)
if [ -n "$SELF_DIR" ] && [ -f "$SELF_DIR/../deploy/docker-compose.prod.yml" ]; then
  REPO=$(cd "$SELF_DIR/.." && pwd); ok "현재 저장소 사용: $REPO"
elif [ -f "$INSTALL_DIR/deploy/docker-compose.prod.yml" ]; then
  REPO=$INSTALL_DIR; ok "기존 클론 사용: $REPO"
else
  echo "    clone: $REPO_URL -> $INSTALL_DIR"
  if ! git clone "$REPO_URL" "$INSTALL_DIR"; then
    die "clone 실패. 비공개 저장소면 'sudo apt install gh && gh auth login' 후 재실행하거나, 배포 키(ssh-keygen 후 GitHub Deploy key 등록)를 만들어 REPO_URL=git@github.com:... 로 다시 실행하라."
  fi
  REPO=$INSTALL_DIR
fi
DEPLOY=$REPO/deploy; ENV_FILE=$DEPLOY/.env

# ---------- 4. deploy/.env ----------
step "4/10 deploy/.env"
get() { grep -E "^$1=" "$ENV_FILE" | tail -1 | cut -d= -f2- || true; }
set_kv() { # 키가 있으면 줄 교체, 없으면 추가. 값은 ENVIRON으로 넘겨 이스케이프 문제를 피한다.
  if grep -qE "^$1=" "$ENV_FILE"; then
    K=$1 V=$2 awk -F= '$1==ENVIRON["K"]{print ENVIRON["K"] "=" ENVIRON["V"]; next}{print}' "$ENV_FILE" >"$ENV_FILE.tmp"
    cat "$ENV_FILE.tmp" >"$ENV_FILE"; rm -f "$ENV_FILE.tmp"
  else printf '%s=%s\n' "$1" "$2" >>"$ENV_FILE"; fi
}
empty() { local v; v=$(get "$1"); [ -z "$v" ] || [ "$v" = change-me ]; }
[ -f "$ENV_FILE" ] || { cp "$DEPLOY/.env.example" "$ENV_FILE"; echo "    .env.example에서 .env 생성"; }
chmod 600 "$ENV_FILE"
command -v openssl >/dev/null || run $SUDO apt-get install -y openssl
# 자동 생성(이미 값이 있으면 절대 덮어쓰지 않는다. ENCRYPTION 키는 바꾸면 기존 데이터 복호화 불가)
for k in DB_PASSWORD APP_ENCRYPTION_KEY APP_ENCRYPTION_BROKER_KEY; do
  if empty "$k"; then
    v=$(openssl rand -base64 32); [ "$k" = DB_PASSWORD ] && v=$(printf '%s' "$v" | tr -d '/+=')
    set_kv "$k" "$v"; echo "    $k 자동 생성"
  else ok "$k"; fi
done
# 환경변수로 받은 값 채우기(빈 칸일 때만)
for k in OPENDART_API_KEY DATA_GO_KR_SERVICE_KEY TS_AUTHKEY TUNNEL_TOKEN; do
  if empty "$k" && [ -n "${!k:-}" ]; then set_kv "$k" "${!k}"; echo "    $k 환경변수에서 채움"; fi
done

# ---------- 5. 터널 ----------
step "5/10 터널 선택"
TUNNEL=${TUNNEL:-}
if [ -z "$TUNNEL" ]; then
  ts=0; cf=0; empty TS_AUTHKEY || ts=1; empty TUNNEL_TOKEN || cf=1
  if [ $ts = 1 ] && [ $cf = 1 ]; then die ".env에 TS_AUTHKEY와 TUNNEL_TOKEN이 둘 다 있다. TUNNEL=tailscale 또는 cloudflared를 지정하라."
  elif [ $ts = 1 ]; then TUNNEL=tailscale; elif [ $cf = 1 ]; then TUNNEL=cloudflared; else TUNNEL=none; fi
fi
FILES=(-f "$DEPLOY/docker-compose.prod.yml")
MISSING=(); empty OPENDART_API_KEY && MISSING+=("OPENDART_API_KEY (https://opendart.fss.or.kr)")
empty DATA_GO_KR_SERVICE_KEY && MISSING+=("DATA_GO_KR_SERVICE_KEY (공공데이터포털 특일 정보)")
case "$TUNNEL" in
  tailscale)   FILES+=(-f "$DEPLOY/docker-compose.tailscale.yml")
               empty TS_AUTHKEY && MISSING+=("TS_AUTHKEY (Tailscale 콘솔 > Settings > Keys; docs/home-server.md 6장 A)") ;;
  cloudflared) FILES+=(-f "$DEPLOY/docker-compose.cloudflared.yml")
               empty TUNNEL_TOKEN && MISSING+=("TUNNEL_TOKEN (Cloudflare Zero Trust > Tunnels)") ;;
  none)        echo "    터널 없음: 같은 네트워크(LAN)에서만 접속된다. 외부 접속은 TUNNEL=tailscale|cloudflared." ;;
  *) die "TUNNEL은 tailscale|cloudflared|none 중 하나여야 한다(입력: $TUNNEL)." ;;
esac
echo "    터널: $TUNNEL"
if [ ${#MISSING[@]} -gt 0 ]; then
  echo; echo "다음 값이 비어 있다(값은 출력하지 않는다). $ENV_FILE 을 열어 채운 뒤 이 스크립트를 다시 실행하라:"
  printf '  - %s\n' "${MISSING[@]}"
  echo "자동 생성된 APP_ENCRYPTION_* 키는 분실하면 저장된 증권사 키를 복호화할 수 없으니 PC 밖(비밀번호 관리자)에도 보관하라."
  exit 3
fi

# ---------- 6. 기동 ----------
step "6/10 스택 기동 (첫 빌드는 10분 안팎)"
PORT=$(get HTTP_PORT); PORT=${PORT:-18080}
dc() { (cd "$DEPLOY" && "${DOCKER[@]}" compose -p vibe-prod "${FILES[@]}" "$@"); }
if [ "$DRY_RUN" = 1 ]; then echo "    [DRY_RUN] docker compose -p vibe-prod ${FILES[*]} up -d --build"
else
  dc up -d --build
  echo "    backend healthy 대기(최대 10분)..."
  cid=$(dc ps -q backend); st=""
  for _ in $(seq 1 120); do
    st=$("${DOCKER[@]}" inspect -f '{{.State.Health.Status}}' "$cid" 2>/dev/null || echo none)
    [ "$st" = healthy ] && break; sleep 5
  done
  [ "$st" = healthy ] || die "backend가 healthy가 되지 않았다(상태: $st). 'docker compose -p vibe-prod logs backend'로 확인하라."
  curl -fsS -o /dev/null "http://localhost:$PORT/" && echo "    웹 응답 OK (http://localhost:$PORT)" \
    || echo "    경고: http://localhost:$PORT 응답 없음. docker compose -p vibe-prod ps 확인."
fi

# ---------- 7. 부팅 자동 시작 ----------
step "7/10 부팅 시 자동 시작"
if [ "$DRY_RUN" = 1 ]; then echo "    [DRY_RUN] 생략"
else
  if systemctl is-enabled docker >/dev/null 2>&1; then
    echo "    docker 서비스 enable + 모든 컨테이너 restart: unless-stopped 이므로 별도 systemd 유닛 불필요."
  else echo "    경고: docker 서비스가 enable 되어 있지 않다."; fi
fi

# ---------- 8. 백업 cron ----------
step "8/10 백업 cron (매일 03:30)"
if [ "$DRY_RUN" = 1 ]; then echo "    [DRY_RUN] 생략"
else
  command -v crontab >/dev/null || { $SUDO apt-get install -y cron; $SUDO systemctl enable --now cron; }
  if crontab -l 2>/dev/null | grep -q "$REPO/scripts/backup-db.sh"; then ok "cron 이미 등록됨"
  else
    mkdir -p "$HOME/vibe-backups"
    # docker 그룹이 아직 적용 전이어도 cron은 새 세션이라 그룹이 적용된다
    ( crontab -l 2>/dev/null || true; echo "30 3 * * * $REPO/scripts/backup-db.sh >> $HOME/vibe-backups/backup.log 2>&1" ) | crontab -
    echo "    등록함. 로그: ~/vibe-backups/backup.log (PC 밖 사본은 docs/home-server.md 8장 BACKUP_REMOTE)"
  fi
fi

# ---------- 9. 절전 끄기 ----------
step "9/10 절전 끄기"
if [ "$DRY_RUN" = 1 ]; then echo "    [DRY_RUN] 생략"
elif [ "$DISABLE_SLEEP" = 1 ] && [ -d /run/systemd/system ]; then
  $SUDO systemctl mask sleep.target suspend.target hibernate.target hybrid-sleep.target >/dev/null 2>&1
  echo "    절전/최대절전 target을 mask 했다(되돌리기: systemctl unmask ...). 건너뛰려면 DISABLE_SLEEP=0."
  echo "    노트북이면 덮개 닫음 무시도 필요: docs/home-server.md 10장."
else ok "DISABLE_SLEEP=$DISABLE_SLEEP"; fi

# ---------- 10. 방화벽 점검 + 요약 ----------
step "10/10 방화벽·노출 점검"
if [ "$DRY_RUN" != 1 ]; then
  if command -v ufw >/dev/null && $SUDO ufw status 2>/dev/null | grep -q "Status: active"; then
    echo "    ufw 활성: 터널 방식은 외부 인바운드 포트 개방이 필요 없다. 포트를 열지 않았다(SSH만 허용 권장)."
  fi
  if ss -ltn 2>/dev/null | awk '{print $4}' | grep -E ':(5432|18081|8080)$' | grep -vE '^(127\.0\.0\.1|\[::1\]):' ; then
    echo "    경고: 위 포트가 외부에 노출돼 있다. 공유기 포워딩/방화벽으로 막아라."
  else echo "    5432·18081·8080 외부 노출 없음."; fi
fi

HOSTN=$(get TS_HOSTNAME)
case "$TUNNEL" in
  tailscale)   URL="https://${HOSTN:-vibe}.<tailnet>.ts.net  (Tailscale 콘솔에서 tailnet 이름 확인)" ;;
  cloudflared) URL="Cloudflare 터널에 지정한 공개 호스트 이름" ;;
  *)           URL="http://<이 PC의 LAN 주소>:$PORT" ;;
esac
cat <<MSG

설치 완료.
  접속 주소     : $URL
  첫 관리자     : 앱에서 가입 후  PG_CONTAINER=vibe-prod-postgres-1 $REPO/scripts/promote-admin.sh <이메일>
                  (다시 로그인 후 관리자로 POST /api/ipos/insight/sync 1회)
  업데이트      : $REPO/scripts/home-server-update.sh
  키 보관       : $ENV_FILE 의 APP_ENCRYPTION_* 키를 PC 밖에도 보관하라.
MSG
