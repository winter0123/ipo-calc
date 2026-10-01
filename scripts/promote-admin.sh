#!/usr/bin/env bash
# 계정을 ADMIN으로 승격하고 승인한다. 사용: scripts/promote-admin.sh <이메일> [DB 이름(기본 vibe_ai_team)]
# 로컬 Postgres 컨테이너(vibe-ai-team-postgres)에서 실행한다. 승격 뒤 해당 계정은 다시 로그인해야 한다.
set -euo pipefail

EMAIL="${1:?사용법: scripts/promote-admin.sh <이메일> [DB 이름]}"
DB="${2:-vibe_ai_team}"

# SQL에 그대로 들어가므로 이메일 형식이 아니면 거부한다.
if ! [[ "$EMAIL" =~ ^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$ ]]; then
  echo "이메일 형식이 올바르지 않습니다" >&2
  exit 1
fi
LOWER="$(printf '%s' "$EMAIL" | tr '[:upper:]' '[:lower:]')"

UPDATED="$(docker exec vibe-ai-team-postgres psql -U vibe -d "$DB" -At \
  -c "UPDATE accounts SET role='ADMIN', approved=TRUE WHERE email='$LOWER' RETURNING id")"
if [ -z "$UPDATED" ]; then
  echo "해당 이메일의 계정이 없습니다: $LOWER" >&2
  exit 1
fi
echo "ADMIN으로 승격했습니다: accountId=$UPDATED (DB=$DB). 다시 로그인하세요."
