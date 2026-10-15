#!/usr/bin/env bash
# 운영 DB(deploy/docker-compose.prod.yml의 postgres-prod) 백업. 압축 덤프를 날짜별로 남기고 오래된 것은 지운다.
# 사용법: scripts/backup-db.sh [백업 폴더(기본 ~/ipo-calc-backups)] [보관 개수(기본 14)]
# PC 밖 사본: BACKUP_REMOTE=gdrive:ipo-calc-backups 처럼 rclone 원격을 주면 새 백업을 rclone copy 한다(선택, rclone 필요).
# 매일 자동: crontab -e 에  `30 3 * * * /path/to/repo/scripts/backup-db.sh >> ~/ipo-calc-backups/backup.log 2>&1`
set -euo pipefail
DIR=${1:-$HOME/ipo-calc-backups}
KEEP=${2:-14}
CONTAINER=${BACKUP_CONTAINER:-ipo-calc-postgres-prod}
DB=${BACKUP_DB:-ipo_calc}
mkdir -p "$DIR"
chmod 700 "$DIR"
OUT="$DIR/${DB}_$(date +%Y%m%d_%H%M%S).sql.gz"
# 컨테이너 안의 pg_dump를 써서 서버와 버전을 맞춘다. 실패하면 반쯤 쓴 파일을 남기지 않는다.
if ! docker exec "$CONTAINER" pg_dump -U ipo -d "$DB" --no-owner --clean --if-exists | gzip > "$OUT.tmp"; then
  rm -f "$OUT.tmp"; echo "백업 실패: $CONTAINER/$DB" >&2; exit 1
fi
mv "$OUT.tmp" "$OUT"
chmod 600 "$OUT"
# 보관 개수를 넘는 오래된 백업 삭제
ls -1t "$DIR"/"${DB}"_*.sql.gz 2>/dev/null | tail -n +$((KEEP + 1)) | xargs -r rm -f
# 밖으로 복사(선택). 실패해도 로컬 백업은 이미 성공이므로 경고만 남기고 종료 코드로 알린다.
if [ -n "${BACKUP_REMOTE:-}" ]; then
  rclone copy "$OUT" "$BACKUP_REMOTE" || { echo "원격 복사 실패: $BACKUP_REMOTE" >&2; exit 2; }
fi
echo "백업 완료: $OUT ($(du -h "$OUT" | cut -f1))"
