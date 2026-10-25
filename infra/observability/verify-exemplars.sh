#!/usr/bin/env bash
# Checks metric exemplars on a RUNNING stack started with TRACING_ENABLED=true and --profile observability:
# after traced requests, Prometheus holds an exemplar (a trace id) for the backend's and the intelligence
# service's latency histograms, and each of those trace ids is a real trace in Jaeger.
# Needs the owner's JWT secret (read from the backend container, never printed).
# Usage: infra/observability/verify-exemplars.sh
set -euo pipefail
cd "$(dirname "$0")/.."
export COMPOSE_IGNORE_ORPHANS=1
PROM=${PROMETHEUS_URL:-http://localhost:9090}
JAEGER=${JAEGER_URL:-http://localhost:16686}
fail() { echo "FAIL: $*"; exit 1; }
SECRET=$(docker compose exec -T backend printenv AUTH_JWT_SECRET | tr -d '\r\n')
TOKEN=$(python3 - "$SECRET" "${VERIFY_USER_ID:-1}" <<'PY'
import sys,hmac,hashlib,base64,json,time
b=lambda x: base64.urlsafe_b64encode(x).rstrip(b'=')
h=b(json.dumps({"alg":"HS256","typ":"JWT"}).encode()); p=b(json.dumps({"sub":sys.argv[2],"exp":int(time.time())+120}).encode())
print((h+b'.'+p+b'.'+b(hmac.new(sys.argv[1].encode(),h+b'.'+p,hashlib.sha256).digest())).decode())
PY
)
for _ in 1 2 3; do
  curl -s -o /dev/null -H "Authorization: Bearer $TOKEN" http://localhost:8000/patterns
done

# Prometheus scrapes every 15 s and keeps one exemplar per bucket: wait for each metric's exemplar to arrive.
exemplar_trace() {
  curl -s -G "$PROM/api/v1/query_exemplars" --data-urlencode "query=$1" \
      --data-urlencode "start=$(( $(date +%s) - 600 ))" --data-urlencode "end=$(date +%s)" |
    python3 -c '
import sys, json
for series in json.load(sys.stdin)["data"]:
    for e in series["exemplars"]:
        if len(e["labels"].get("trace_id", "")) == 32:
            print(e["labels"]["trace_id"]); sys.exit(0)'
}
check() {
  local name=$1 query=$2 trace=""
  for _ in $(seq 1 30); do
    trace=$(exemplar_trace "$query" || true)
    [ -n "$trace" ] && break
    sleep 3
  done
  [ -n "$trace" ] || fail "Prometheus holds no exemplar for $name ($query): is TRACING_ENABLED=true and exemplar storage on?"
  curl -s "$JAEGER/api/traces/$trace" | grep -q '"spans"' || fail "the $name exemplar's trace id $trace is not a trace in Jaeger"
  echo "ok: $name exemplar $trace is a real trace in Jaeger"
}
check "backend" 'http_server_requests_seconds_bucket{job="backend"}'
check "intelligence service" 'http_request_duration_seconds_bucket{route="/patterns"}'
