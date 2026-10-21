#!/usr/bin/env bash
# Checks a RUNNING observability stack's log pipeline (docs/observability.md): a request's id is findable in
# Loki, only the two intended services are shipped, and nothing that must never be logged is in Loki.
# Usage: infra/observability/verify-logs.sh   (stack up with --profile observability; backend on :8080)
set -euo pipefail
LOKI=${LOKI_URL:-http://localhost:3100}
ID="verify-$(date +%s)"
fail() { echo "FAIL: $*"; exit 1; }
count() { curl -sG "$LOKI/loki/api/v1/query_range" --data-urlencode "query=$1" \
  --data-urlencode "start=$(( $(date +%s) - 3600 ))000000000" --data-urlencode "limit=100" \
  | python3 -c 'import sys,json;print(sum(len(s["values"]) for s in json.load(sys.stdin)["data"]["result"]))'; }

curl -s -o /dev/null -H "X-Request-Id: $ID" 'http://localhost:8080/api/books?note=secret-words'
for _ in $(seq 1 30); do [ "$(count "{service=\"backend\"} | json | requestId=\"$ID\"")" -ge 1 ] && break; sleep 2; done
[ "$(count "{service=\"backend\"} | json | requestId=\"$ID\"")" -ge 1 ] || fail "the request id was not found in Loki"
echo "ok: request $ID found in Loki"

services=$(curl -s "$LOKI/loki/api/v1/label/service/values" | python3 -c 'import sys,json;print(" ".join(sorted(json.load(sys.stdin)["data"])))')
for s in $services; do case "$s" in backend|intelligence-service) ;; *) fail "unexpected service shipped: $s";; esac; done
echo "ok: services shipped: $services"

[ "$(count '{service=~"postgres|frontend"}')" = 0 ] || fail "postgres or frontend logs were shipped"
echo "ok: no postgres or frontend lines"
for needle in secret-words "@gmail.com" "password"; do
  [ "$(count "{service=~\".+\"} |= \"$needle\"")" = 0 ] || fail "found \"$needle\" in shipped logs"
done
echo "ok: no query-string text, no email address, no 'password' in shipped logs"
