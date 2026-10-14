#!/usr/bin/env bash
# 계정을 ADMIN으로 승격하고 승인한다. 사용: scripts/promote-admin.sh <이메일> [DB 이름(기본 vibe_ai_team)]
# 기본은 로컬 개발 컨테이너(ipocalc-postgres-dev). 운영 스택이면 PG_CONTAINER=ipocalc-postgres-prod 을 앞에 붙인다. 승격 뒤 해당 계정은 다시 로그인해야 한다.
set -euo pipefail

EMAIL="${1:?사용법: scripts/promote-admin.sh <이메일> [DB 이름]}"
DB="${2:-vibe_ai_team}"

# SQL에 그대로 들어가므로 이메일 형식이 아니면 거부한다.
if ! [[ "$EMAIL" =~ ^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$ ]]; then
  echo "이메일 형식이 올바르지 않습니다" >&2
  exit 1
fi
LOWER="$(printf '%s' "$EMAIL" | tr '[:upper:]' '[:lower:]')"

# psql은 UPDATE 뒤에 "UPDATE n" 상태 줄도 출력하므로 -q로 끄고 RETURNING 값만 받는다.
UPDATED="$(docker exec "${PG_CONTAINER:-ipocalc-postgres-dev}" psql -U vibe -d "$DB" -Atq \
   -c "UPDATE accounts SET role='ADMIN', approved=TRUE WHERE email='$LOWER' RETURNING id")"
if [ -z "$UPDATED" ]; then
  echo "해당 이메일의 계정이 없습니다: $LOWER" >&2
  exit 1
fi
echo "ADMIN으로 승격했습니다: accountId=$UPDATED (DB=$DB). 다시 로그인하세요."
