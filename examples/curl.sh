#!/usr/bin/env bash
set -euo pipefail
BASE=${BASE:-http://localhost:8086}
for mode in VIRTUAL PLATFORM; do
  for scenario in SUCCESS SLOW ERROR NEVER; do
    curl --fail-with-body -sS "$BASE/api/quotes" \
      -H 'Content-Type: application/json' \
      -d "{\"mode\":\"$mode\",\"scenario\":\"$scenario\",\"providers\":6}"
    printf '\n'
  done
done
