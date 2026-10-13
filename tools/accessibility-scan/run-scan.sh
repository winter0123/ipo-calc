#!/usr/bin/env bash
# 다른 프로젝트와 포트가 겹치지 않도록 빈 포트를 매번 새로 골라 백엔드+프론트를 띄우고 접근성 스캔을 실행한다.
# 사용: DB_PASSWORD=... [UI_DIR=<node_modules가 설치된 ui 경로>] ./run-scan.sh
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
UI_DIR="${UI_DIR:-$ROOT/ui}"
: "${DB_PASSWORD:?DB_PASSWORD가 필요합니다}"
# 다른 도구(E2E·가이드 캡처)처럼 슬롯 4를 리셋해 쓴다. 기본 개발 DB를 쓰면 시드(증권사 등)가 달라 화면을 건너뛴다.
export DB_NAME="${DB_NAME:-vibe_slot_4}"
"$ROOT/scripts/db-slot.sh" reset "${DB_NAME#vibe_slot_}" | tail -1

free_port() { node -e "const s=require('net').createServer().listen(0,'127.0.0.1',()=>{console.log(s.address().port);s.close()})"; }
API_PORT="$(free_port)"
WEB_PORT="$(free_port)"
TMP="$(mktemp -d)"
PIDS=()
cleanup() { for p in "${PIDS[@]:-}"; do kill "$p" 2>/dev/null || true; done; rm -rf "$TMP"; }
trap cleanup EXIT

echo "백엔드 포트 $API_PORT, 프론트 포트 $WEB_PORT"

cat > "$UI_DIR/vite.scan.config.ts" <<CFG
import { defineConfig, mergeConfig } from 'vite'
import base from './vite.config.ts'
export default mergeConfig(base, defineConfig({
  server: { proxy: { '/api-proxy': { target: 'http://127.0.0.1:${API_PORT}', rewrite: (p) => p.replace(/^\/api-proxy/, '') } } },
}))
CFG
trap 'rm -f "$UI_DIR/vite.scan.config.ts"; cleanup' EXIT

KEY="$(head -c 32 /dev/urandom | base64)"
(cd "$ROOT/backend" && DB_NAME="$DB_NAME" DB_PASSWORD="$DB_PASSWORD" APP_IPO_SYNC_ENABLED=false APP_HOLIDAY_SYNC_ENABLED=false APP_ENCRYPTION_KEY="$KEY" APP_ENCRYPTION_BROKER_KEY="$(head -c 32 /dev/urandom | base64)" APP_RATE_LIMIT_AUTH_PER_MINUTE=100000 APP_RATE_LIMIT_PUBLIC_READ_PER_MINUTE=100000 APP_SIGNUP_MODE=OPEN SERVER_PORT="$API_PORT" ./gradlew bootRun >"$TMP/backend.log" 2>&1) &
PIDS+=($!)
(cd "$UI_DIR" && VITE_API_BASE_URL=/api-proxy pnpm exec vite --config vite.scan.config.ts --host 127.0.0.1 --port "$WEB_PORT" --strictPort >"$TMP/vite.log" 2>&1) &
PIDS+=($!)

for _ in $(seq 1 90); do
  curl -s -o /dev/null "http://127.0.0.1:$API_PORT/auth/me" && curl -s -o /dev/null "http://127.0.0.1:$WEB_PORT/login" && break
  sleep 2
done
curl -s -o /dev/null "http://127.0.0.1:$API_PORT/auth/me" || { echo "백엔드 기동 실패"; tail -20 "$TMP/backend.log"; exit 1; }

[ -d "$HERE/node_modules" ] || (cd "$HERE" && npm ci --silent)
E2E_DB_NAME="$DB_NAME" BASE_URL="http://127.0.0.1:$WEB_PORT" node "$HERE/scan.mjs"
rc=$?

# bootRun은 자식 JVM을 남기므로 이 실행이 고른 포트의 리스너를 직접 종료한다.
for port in "$API_PORT" "$WEB_PORT"; do lsof -tiTCP:"$port" -sTCP:LISTEN | xargs -r kill 2>/dev/null || true; done
exit $rc
