#!/usr/bin/env bash
set -uo pipefail
# 사용법: tools/screenshot-guide/run.sh  (저장소 어디서든, 도커 DB 기동 상태에서). 기동·정리는 tools/lib/stack.sh(가입 모드 APPROVAL).
source "$(dirname "$0")/../lib/stack.sh"
OUT=${OUT:-$ROOT/docs/user-guides/manual/screenshots}
ADMIN_IPO_PATH=${ADMIN_IPO_PATH:-/admin/ipos}
cd "$ROOT"
stack_up APPROVAL
(cd tools/screenshot-guide && pnpm install --frozen-lockfile >/dev/null 2>&1; OUT="$OUT" ADMIN_IPO_PATH="$ADMIN_IPO_PATH" BASE_URL="http://127.0.0.1:$WEB" node capture.mjs 2>&1 | tail -15)
