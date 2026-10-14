#!/usr/bin/env bash
# 백업 파일을 DB로 복원한다. 대상 DB의 기존 데이터는 덮어써진다(덤프에 DROP ... IF EXISTS 포함).
# 사용법: scripts/restore-db.sh <백업.sql.gz> [대상 DB(기본 vibe_ai_team)]
# 운영 복원 전 backend를 멈추는 것을 권장: docker compose -p vibe-ai-team -f deploy/docker-compose.prod.yml stop backend
set -euo pipefail
FILE=${1:?백업 파일 경로가 필요합니다}
DB=${2:-vibe_ai_team}
CONTAINER=${BACKUP_CONTAINER:-vibe-ai-team-prod-postgres-1}
[ -f "$FILE" ] || { echo "파일 없음: $FILE" >&2; exit 1; }
gzip -t "$FILE"
if [ "${RESTORE_YES:-}" != "1" ]; then
  read -r -p "$CONTAINER/$DB 를 $FILE 로 덮어씁니다. 계속하려면 'restore' 입력: " answer
  [ "$answer" = "restore" ] || { echo "취소"; exit 1; }
fi
gunzip -c "$FILE" | docker exec -i "$CONTAINER" psql -U vibe -d "$DB" -v ON_ERROR_STOP=1 -q > /dev/null
echo "복원 완료: $DB ← $FILE"
