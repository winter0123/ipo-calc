#!/usr/bin/env bash
set -uo pipefail
# 사용법: tools/screenshot-guide/run.sh  (저장소 어디서든, 도커 DB 기동 상태에서)
ROOT=$(git rev-parse --show-toplevel)
OUT=${OUT:-$ROOT/docs/user-guides/manual/screenshots}
ADMIN_IPO_PATH=${ADMIN_IPO_PATH:-/admin/ipos}
cd "$ROOT"
free_port() { node -e "const s=require('net').createServer().listen(0,'127.0.0.1',()=>{console.log(s.address().port);s.close()})"; }
API=$(free_port); WEB=$(free_port); TMP=$(mktemp -d)
cleanup() { for p in "$API" "$WEB"; do lsof -tiTCP:"$p" -sTCP:LISTEN | xargs -r kill 2>/dev/null || true; done; rm -f "$ROOT/ui/vite.scan.config.ts"; }
trap cleanup EXIT
scripts/db-slot.sh reset 4 | tail -1
cat > ui/vite.scan.config.ts <<CFG
import { defineConfig, mergeConfig } from 'vite'
import base from './vite.config.ts'
export default mergeConfig(base, defineConfig({
  server: { proxy: { '/api-proxy': { target: 'http://127.0.0.1:${API}', rewrite: (p) => p.replace(/^\/api-proxy/, '') } } },
}))
CFG
KEY=$(head -c 32 /dev/urandom | base64); BKEY=$(head -c 32 /dev/urandom | base64)
(cd backend && DB_NAME=vibe_slot_4 DB_PASSWORD=vibe_local_password APP_IPO_SYNC_ENABLED=false APP_HOLIDAY_SYNC_ENABLED=false APP_ENCRYPTION_KEY="$KEY" APP_ENCRYPTION_BROKER_KEY="$BKEY" APP_RATE_LIMIT_AUTH_PER_MINUTE=100000 APP_RATE_LIMIT_PUBLIC_READ_PER_MINUTE=100000 APP_SIGNUP_MODE=APPROVAL SERVER_PORT="$API" ./gradlew bootRun >"$TMP/backend.log" 2>&1) &
(cd ui && VITE_API_BASE_URL=/api-proxy pnpm exec vite --config vite.scan.config.ts --host 127.0.0.1 --port "$WEB" --strictPort >"$TMP/vite.log" 2>&1) &
for _ in $(seq 1 120); do curl -s -o /dev/null "http://127.0.0.1:$API/auth/me" && curl -s -o /dev/null "http://127.0.0.1:$WEB/login" && break; sleep 2; done
(cd tools/screenshot-guide && pnpm install --frozen-lockfile >/dev/null 2>&1; OUT="$OUT" ADMIN_IPO_PATH="$ADMIN_IPO_PATH" BASE_URL="http://127.0.0.1:$WEB" node capture.mjs 2>&1 | tail -15)
