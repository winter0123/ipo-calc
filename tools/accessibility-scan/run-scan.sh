#!/usr/bin/env bash
# 빈 포트로 백엔드+프론트를 띄우고 접근성 스캔을 실행한다(63화면 미달·위반이면 실패). 기동·정리·DB 선택은 tools/lib/stack.sh.
# 사용: DB_PASSWORD=... [UI_DIR=<node_modules가 설치된 ui 경로>] ./run-scan.sh   (CI: E2E_DB_CONTAINER 등은 tools/e2e/run.sh 와 같음)
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
source "$HERE/../lib/stack.sh"
: "${DB_PASSWORD:?DB_PASSWORD가 필요합니다}"
stack_up OPEN
[ -d "$HERE/node_modules" ] || (cd "$HERE" && npm ci --silent)
E2E_DB_NAME="$DBN" BASE_URL="http://127.0.0.1:$WEB" node "$HERE/scan.mjs"
