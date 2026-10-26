#!/usr/bin/env bash
# 고정 슬롯 DB 풀 관리 스크립트.
#
# 같은 Postgres 컨테이너 안에 슬롯 DB(ipo_slot_1 ~ ipo_slot_4)를 만들어 두고,
# 슬롯을 빌려 쓸 때마다 reset 으로 완전히 다시 만들어 이전 사용자의 마이그레이션 잔재를 지운다.
# 기본 DB(ipo_calc)는 건드리지 않는다. DB_NAME 환경변수를 주지 않으면 yml의 기본값이 그대로 쓰인다.
#
# 비밀번호는 이 스크립트에 쓰지 않는다. 컨테이너 내부 psql 은 로컬 접속으로 -U ipo 만으로 연결되며,
# 로컬 접속이 막힌 환경에서만 PGPASSWORD 환경변수를 주면 그 값이 사용된다.

set -euo pipefail

CONTAINER="ipo-calc-postgres-dev"
DB_USER="ipo"
ADMIN_DB="ipo_calc"
SLOT_MIN=1
SLOT_MAX=4

usage() {
  cat <<'EOF'
사용법:
  scripts/db-slot.sh reset <1-4>   슬롯 DB를 비우고 새로 만든 뒤 마지막 줄에 DB_NAME 을 출력한다
  scripts/db-slot.sh list          슬롯 DB의 존재 여부를 출력한다

예:
  scripts/db-slot.sh reset 3
  DB_NAME=ipo_slot_3 DB_PASSWORD=<로컬비밀번호> ./gradlew test --rerun
EOF
}

require_container() {
  if ! docker ps --format '{{.Names}}' | grep -qx "$CONTAINER"; then
    echo "컨테이너 '$CONTAINER' 가 떠 있지 않습니다. 먼저 'docker compose --env-file .env-local up -d postgres' 를 실행하세요." >&2
    exit 1
  fi
}

psql_exec() {
  # $1 = 실행할 SQL. PGPASSWORD 가 환경에 있으면 컨테이너에 넘기고, 없으면 로컬 접속만 쓴다.
  if [ -n "${PGPASSWORD:-}" ]; then
    docker exec -e PGPASSWORD="$PGPASSWORD" "$CONTAINER" \
      psql -U "$DB_USER" -d "$ADMIN_DB" -v ON_ERROR_STOP=1 -tAq -c "$1"
  else
    docker exec "$CONTAINER" \
      psql -U "$DB_USER" -d "$ADMIN_DB" -v ON_ERROR_STOP=1 -tAq -c "$1"
  fi
}

cmd_reset() {
  local slot="$1"
  local db="ipo_slot_$slot"
  require_container
  # DROP 과 CREATE 는 같은 트랜잭션 블록에 들어갈 수 없어 psql 호출을 나눈다.
  psql_exec "DROP DATABASE IF EXISTS $db WITH (FORCE);"
  psql_exec "CREATE DATABASE $db OWNER $DB_USER;"
  echo "DB_NAME=$db"
}

cmd_list() {
  require_container
  local existing
  existing="$(psql_exec "SELECT datname FROM pg_database WHERE datname ~ '^ipo_slot_[1-4]$' ORDER BY 1;")"
  local slot db
  for slot in $(seq "$SLOT_MIN" "$SLOT_MAX"); do
    db="ipo_slot_$slot"
    if printf '%s\n' "$existing" | grep -qx "$db"; then
      echo "$db: exists"
    else
      echo "$db: missing"
    fi
  done
}

main() {
  local sub="${1-}"
  case "$sub" in
    reset)
      if [ "$#" -ne 2 ]; then
        echo "reset 은 슬롯 번호 하나를 받아야 합니다." >&2
        usage >&2
        exit 1
      fi
      case "$2" in
        1|2|3|4)
          cmd_reset "$2"
          ;;
        *)
          echo "슬롯 번호는 ${SLOT_MIN}~${SLOT_MAX} 사이의 정수여야 합니다: '$2'" >&2
          usage >&2
          exit 1
          ;;
      esac
      ;;
    list)
      cmd_list
      ;;
    *)
      if [ -n "$sub" ] && [ "$sub" != "-h" ] && [ "$sub" != "--help" ]; then
        echo "알 수 없는 하위 명령: '$sub'" >&2
      fi
      usage >&2
      exit 1
      ;;
  esac
}

main "$@"
