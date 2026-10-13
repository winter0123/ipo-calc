#!/usr/bin/env bash
# E2E 전체 실행: DB 슬롯 4 리셋 → 빈 포트로 백엔드(가입 OPEN, 외부 동기화 끔, 요청 제한 크게)·vite 기동 → tools/e2e 실행 → 정리.
# 사용법(저장소 어디서든): DB_PASSWORD=vibe_local_password tools/e2e/run.sh
# 실패한 시나리오가 있으면 종료 코드 1. 접근성 스캔·가이드 캡처와 같은 슬롯 4를 쓰므로 동시에 돌리지 않는다.
set -uo pipefail
ROOT=$(git -C "$(dirname "$0")" rev-parse --show-toplevel)
cd "$ROOT"
: "${DB_PASSWORD:=vibe_local_password}"
free_port() { node -e "const s=require('net').createServer().listen(0,'127.0.0.1',()=>{console.log(s.address().port);s.close()})"; }
API=$(free_port); WEB=$(free_port); TMP=$(mktemp -d)
cleanup() {
  for p in "$API" "$WEB"; do lsof -tiTCP:"$p" -sTCP:LISTEN | xargs -r kill 2>/dev/null || true; done
  rm -f "$ROOT/ui/vite.scan.config.ts"
}
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
(cd backend && DB_NAME=vibe_slot_4 DB_PASSWORD="$DB_PASSWORD" APP_ENCRYPTION_KEY="$KEY" APP_ENCRYPTION_BROKER_KEY="$BKEY" \
  APP_IPO_SYNC_ENABLED=false APP_HOLIDAY_SYNC_ENABLED=false \
  APP_RATE_LIMIT_AUTH_PER_MINUTE=100000 APP_RATE_LIMIT_PUBLIC_READ_PER_MINUTE=100000 APP_SIGNUP_MODE=OPEN \
  SERVER_PORT="$API" ./gradlew bootRun >"$TMP/backend.log" 2>&1) &
(cd ui && VITE_API_BASE_URL=/api-proxy pnpm exec vite --config vite.scan.config.ts --host 127.0.0.1 --port "$WEB" --strictPort >"$TMP/vite.log" 2>&1) &
for _ in $(seq 1 120); do
  curl -s -o /dev/null "http://127.0.0.1:$API/auth/me" && curl -s -o /dev/null "http://127.0.0.1:$WEB/login" && break
  sleep 2
done
curl -s -o /dev/null "http://127.0.0.1:$API/auth/me" || { echo "백엔드 기동 실패"; tail -30 "$TMP/backend.log"; exit 1; }
cd tools/e2e && ([ -d node_modules ] || pnpm install --frozen-lockfile >/dev/null 2>&1)
BASE_URL="http://127.0.0.1:$WEB" E2E_DB_NAME=vibe_slot_4 pnpm run e2e > "$TMP/e2e.log" 2>&1
rc=$?
grep -E "PASS|FAIL|시나리오|Error|error" "$TMP/e2e.log" | tail -40
# 결과 표에 FAIL이 하나라도 있으면 실패로 본다(러너 종료 코드와 별개로).
if grep -qE "\| FAIL \||PASS [0-9]+ / FAIL [1-9]" "$TMP/e2e.log"; then rc=1; fi
exit $rc
