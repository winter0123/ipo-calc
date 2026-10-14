#!/usr/bin/env bash
# 코디네이터 알림: scripts/orca-coord-watch.sh <run_id>
# 백그라운드로 실행하고, 끝나면(알림 1건) 처리한 뒤 같은 턴에서 다시 실행한다.
# RUN은 필수 인자(없으면 사용법 출력 후 종료 코드 2).
# SEEN 기본 경로를 /tmp/orca-coord-seen-<run_id>.txt로(환경변수 SEEN으로 덮어쓰기 가능).
# 점검 간격 INTERVAL 환경변수(기본 60초).
if [ $# -eq 0 ]; then
  echo "usage: $0 <run_id>" >&2
  exit 2
fi
RUN=$1
SEEN=${SEEN:-/tmp/orca-coord-seen-${RUN}.txt}
INTERVAL=${INTERVAL:-60}
touch "$SEEN"
while true; do
  # 같은 디스패치·같은 판정은 한 번만 알린다(재시도 중인 모델이 매 점검마다 다시 뜨는 것 방지)
  hits=$(grep -E '^(ERROR|STALL)' /tmp/orca-worker-status.txt 2>/dev/null | awk '{print $1, $3}' | sort -u | grep -vxF -f "$SEEN")
  if [ -n "$hits" ]; then
    echo "$hits" >> "$SEEN"
    echo "== watchdog"; grep -E '^(ERROR|STALL)' /tmp/orca-worker-status.txt
    exit 0
  fi
  # 이미 처리한 메시지 id(SEEN)는 다시 알리지 않는다
  new=$(orca orchestration check --run "$RUN" --peek --types "worker_done,question,escalation" --json 2>/dev/null | jq -r '.result.messages[]? | .id' | grep -vxF -f "$SEEN")
  if [ -n "$new" ]; then
    echo "$new" >> "$SEEN"
    echo "== messages"; orca orchestration check --run "$RUN" --peek --types "worker_done,question,escalation" --json | jq -c --arg ids "$new" '[.result.messages[] | select(.id as $i | ($ids | split("\n")) | index($i)) | {id, type, subject, body: .body[0:500]}]'
    exit 0
  fi
  sleep "$INTERVAL"
done