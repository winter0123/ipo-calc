#!/usr/bin/env bash
# 집 PC 업데이트: 백업 1회 -> git pull -> compose up --build -> healthy 확인 -> (PRUNE=1이면) 이전 이미지 정리.
# 터널은 setup과 같은 규칙(TUNNEL 환경변수, 없으면 .env의 TS_AUTHKEY/TUNNEL_TOKEN 유무)으로 고른다.
set -euo pipefail
REPO=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
DEPLOY=$REPO/deploy; ENV_FILE=$DEPLOY/.env
[ -f "$ENV_FILE" ] || { echo "deploy/.env 없음. 먼저 scripts/home-server-setup.sh" >&2; exit 1; }
get() { grep -E "^$1=" "$ENV_FILE" | tail -1 | cut -d= -f2- || true; }
DOCKER=(docker); docker info >/dev/null 2>&1 || DOCKER=(sudo docker)

TUNNEL=${TUNNEL:-}
if [ -z "$TUNNEL" ]; then
  ts=$(get TS_AUTHKEY); cf=$(get TUNNEL_TOKEN)
  if [ -n "$ts" ] && [ -n "$cf" ]; then echo "TS_AUTHKEY와 TUNNEL_TOKEN이 둘 다 있다. TUNNEL=tailscale|cloudflared 지정" >&2; exit 1
  elif [ -n "$ts" ]; then TUNNEL=tailscale; elif [ -n "$cf" ]; then TUNNEL=cloudflared; else TUNNEL=none; fi
fi
FILES=(-f "$DEPLOY/docker-compose.prod.yml")
case "$TUNNEL" in
  tailscale) FILES+=(-f "$DEPLOY/docker-compose.tailscale.yml") ;;
  cloudflared) FILES+=(-f "$DEPLOY/docker-compose.cloudflared.yml") ;;
  none) ;;
  *) echo "TUNNEL 값 오류: $TUNNEL" >&2; exit 1 ;;
esac
dc() { (cd "$DEPLOY" && "${DOCKER[@]}" compose -p vibe-prod "${FILES[@]}" "$@"); }

echo "==> 1/4 배포 전 백업"
"$REPO/scripts/backup-db.sh" || echo "경고: 백업 실패(처음 설치 직후가 아니면 중단을 고려하라)." >&2
echo "==> 2/4 git pull"
git -C "$REPO" pull --ff-only
echo "==> 3/4 빌드·기동 (터널: $TUNNEL)"
dc up -d --build
echo "==> 4/4 backend healthy 대기(최대 10분)"
cid=$(dc ps -q backend); st=""
for _ in $(seq 1 120); do
  st=$("${DOCKER[@]}" inspect -f '{{.State.Health.Status}}' "$cid" 2>/dev/null || echo none)
  [ "$st" = healthy ] && break; sleep 5
done
[ "$st" = healthy ] || { echo "backend 상태: $st. docker compose -p vibe-prod logs backend 확인" >&2; exit 1; }
echo "업데이트 완료."
if [ "${PRUNE:-0}" = 1 ]; then "${DOCKER[@]}" image prune -f; else echo "(이전 이미지 정리: PRUNE=1 $0)"; fi
