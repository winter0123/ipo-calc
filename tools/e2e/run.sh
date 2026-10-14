#!/usr/bin/env bash
# E2E 전체 실행: DB 슬롯 4 리셋 → 빈 포트로 백엔드(가입 OPEN, 외부 동기화 끔, 요청 제한 크게)·vite 기동 → tools/e2e 실행 → 정리.
# 사용법(저장소 어디서든): DB_PASSWORD=vibe_local_password tools/e2e/run.sh
# CI(Jenkins): E2E_DB_CONTAINER=<일회용 pg 컨테이너> E2E_JDBC_URL=jdbc:postgresql://<컨테이너>:5432/<DB> [E2E_DB_NAME=<DB>] 를 주면
#   슬롯 리셋을 건너뛰고 그 DB를 쓴다(컨테이너 생명주기는 호출자 몫). 기동·정리는 tools/lib/stack.sh.
# 실패한 시나리오가 있으면 종료 코드 1. 접근성 스캔·가이드 캡처와 같은 슬롯 4를 쓰므로 동시에 돌리지 않는다.
set -uo pipefail
source "$(dirname "$0")/../lib/stack.sh"
cd "$ROOT"
stack_up OPEN
cd tools/e2e && ([ -d node_modules ] || pnpm install --frozen-lockfile >/dev/null 2>&1)
BASE_URL="http://127.0.0.1:$WEB" E2E_DB_NAME="$DBN" pnpm run e2e > "$TMP/e2e.log" 2>&1
rc=$?
grep -E "PASS|FAIL|시나리오|Error|error" "$TMP/e2e.log" | tail -40
# 결과 표에 FAIL이 하나라도 있으면 실패로 본다(러너 종료 코드와 별개로).
if grep -qE "\| FAIL \||PASS [0-9]+ / FAIL [1-9]" "$TMP/e2e.log"; then rc=1; fi
exit $rc
