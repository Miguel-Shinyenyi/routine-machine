#!/usr/bin/env bash
# Checks tracing on a RUNNING stack started with TRACING_ENABLED=true and --profile observability: one request
# through the intelligence service and the backend is one trace, its id is on both services' log lines, and
# the trace carries no token. Needs the owner's JWT secret (read from the backend container, never printed).
# Usage: infra/observability/verify-traces.sh
set -euo pipefail
cd "$(dirname "$0")/.."
export COMPOSE_IGNORE_ORPHANS=1
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
ID="verify-trace-$(date +%s)"
curl -s -o /dev/null -H "X-Request-Id: $ID" -H "Authorization: Bearer $TOKEN" http://localhost:8000/patterns
sleep 2
TRACE=$(docker compose logs intelligence-service 2>/dev/null | grep "\"requestId\": \"$ID\"" | grep -o '"traceId": "[0-9a-f]*"' | head -1 | cut -d'"' -f4)
[ -n "$TRACE" ] || fail "no trace id on the intelligence service's log line (is TRACING_ENABLED=true?)"
echo "ok: intelligence service log line carries trace id $TRACE"
docker compose logs backend 2>/dev/null | grep "\"requestId\":\"$ID\"" | grep -q "\"trace_id\":\"$TRACE\"" || fail "the backend's log line does not carry the same trace id"
echo "ok: the backend's log line carries the same trace id"
# Spans are exported in batches, a few seconds apart per service: wait until both services have arrived.
for _ in $(seq 1 20); do
  curl -s "$JAEGER/api/traces/$TRACE" | grep -q "routine-machine-backend" \
    && curl -s "$JAEGER/api/traces/$TRACE" | grep -q "routine-machine-intelligence" && break
  sleep 2
done
curl -s "$JAEGER/api/traces/$TRACE" | python3 -c '
import sys,json
d=json.load(sys.stdin)["data"][0]
services=sorted({p["serviceName"] for p in d["processes"].values()})
assert services==["routine-machine-backend","routine-machine-intelligence"], services
print("ok: one trace,", len(d["spans"]), "spans across", services)
blob=json.dumps(d).lower()
for needle in ("bearer", "authorization", "eyj"):
    assert needle not in blob, "found %r in the trace" % needle
print("ok: no bearer token or authorization header in the trace")
' || fail "trace content check"
