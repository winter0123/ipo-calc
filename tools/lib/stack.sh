# source 해서 쓰는 공용 함수: 빈 포트 백엔드(bootRun, 외부 동기화 끔, 요청 제한 크게, 임시 암호화 키) + vite 프록시 기동·대기·정리.
# 사용: source tools/lib/stack.sh; stack_up OPEN|APPROVAL  → API·WEB·DBN·TMP·ROOT 변수 제공, 종료 시 자동 정리(EXIT trap).
# DB: 기본은 슬롯 4 리셋(ipo_slot_4). E2E_DB_CONTAINER 가 있으면 리셋을 건너뛰고 E2E_JDBC_URL/E2E_DB_NAME(기본 ipo_calc)을 쓴다.
# 이 도구들(e2e·접근성 스캔·가이드 캡처)은 같은 슬롯 4를 쓰므로 동시에 돌리지 않는다.
ROOT=$(git -C "$(dirname "${BASH_SOURCE[0]}")" rev-parse --show-toplevel)
UI_DIR="${UI_DIR:-$ROOT/ui}"
if [ -f "$ROOT/.env-local" ]; then
  load_dev_env() {
    local line key index
    local -a existing_names=() existing_values=()
    while IFS= read -r line || [ -n "$line" ]; do
      [[ "$line" =~ ^([A-Za-z_][A-Za-z0-9_]*)= ]] || continue
      key=${BASH_REMATCH[1]}
      if declare -p "$key" >/dev/null 2>&1; then
        existing_names+=("$key")
        existing_values+=("${!key}")
      fi
    done < "$ROOT/.env-local"
    set -a
    # shellcheck disable=SC1091
    source "$ROOT/.env-local"
    set +a
    for index in "${!existing_names[@]}"; do
      printf -v "${existing_names[index]}" '%s' "${existing_values[index]}"
      export "${existing_names[index]}"
    done
  }
  load_dev_env
fi
: "${DB_PASSWORD:=ipo_local_password}"

stack_down() {
  # lsof 없는 CI 이미지에서도 동작하도록 포트가 박힌 명령줄로 찾는다(gradle 클라이언트·java·vite).
  pkill -f -- "server.port=$API" 2>/dev/null || true
  pkill -f -- "--port $WEB" 2>/dev/null || true
  rm -f "$UI_DIR/vite.scan.config.ts"
}

stack_up() {
  local mode=${1:-OPEN} free_port key bkey
  free_port() { node -e "const s=require('net').createServer().listen(0,'127.0.0.1',()=>{console.log(s.address().port);s.close()})"; }
  API=$(free_port); WEB=$(free_port); TMP=$(mktemp -d)
  trap stack_down EXIT
  if [ -z "${E2E_DB_CONTAINER:-}" ]; then
    "$ROOT/scripts/db-slot.sh" reset 4 | tail -1
    DBN=ipo_slot_4
  else
    DBN=${E2E_DB_NAME:-ipo_calc}
  fi
  echo "백엔드 포트 $API, 프론트 포트 $WEB"
  cat > "$UI_DIR/vite.scan.config.ts" <<CFG
import { defineConfig, mergeConfig } from 'vite'
import base from './vite.config.ts'
export default mergeConfig(base, defineConfig({
  server: { proxy: { '/api-proxy': { target: 'http://127.0.0.1:${API}', rewrite: (p) => p.replace(/^\/api-proxy/, '') } } },
}))
CFG
  key=$(head -c 32 /dev/urandom | base64); bkey=$(head -c 32 /dev/urandom | base64)
  ([ -z "${E2E_JDBC_URL:-}" ] || export SPRING_DATASOURCE_URL="$E2E_JDBC_URL"; cd "$ROOT/backend" && DB_NAME="$DBN" DB_PASSWORD="$DB_PASSWORD" APP_ENCRYPTION_KEY="$key" APP_ENCRYPTION_BROKER_KEY="$bkey" \
    APP_IPO_SYNC_ENABLED=false APP_HOLIDAY_SYNC_ENABLED=false \
    APP_RATE_LIMIT_AUTH_PER_MINUTE=100000 APP_RATE_LIMIT_PUBLIC_READ_PER_MINUTE=100000 APP_SIGNUP_MODE="$mode" \
    SERVER_PORT="$API" ./gradlew bootRun --args="--server.port=$API" >"$TMP/backend.log" 2>&1) &
  (cd "$UI_DIR" && VITE_API_BASE_URL=/api-proxy pnpm exec vite --config vite.scan.config.ts --host 127.0.0.1 --port "$WEB" --strictPort >"$TMP/vite.log" 2>&1) &
  for _ in $(seq 1 120); do
    curl -s -o /dev/null "http://127.0.0.1:$API/auth/me" && curl -s -o /dev/null "http://127.0.0.1:$WEB/login" && break
    sleep 2
  done
  curl -s -o /dev/null "http://127.0.0.1:$API/auth/me" || { echo "백엔드 기동 실패"; tail -30 "$TMP/backend.log"; exit 1; }
}
