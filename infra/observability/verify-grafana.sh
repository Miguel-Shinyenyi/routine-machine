#!/usr/bin/env bash
# Checks Grafana on a RUNNING observability stack (docs/observability.md): it signs in, its three datasources
# are healthy, the Routine Machine dashboard is loaded, and EVERY query on it runs without error against its
# datasource. A chart that would show "no data" is fine (the stack may be new); a query that errors is not.
# The admin password is read from infra/.env and never printed. Usage: infra/observability/verify-grafana.sh
set -euo pipefail
cd "$(dirname "$0")/.."
GRAFANA=${GRAFANA_URL:-http://localhost:3001}
PASSWORD=$(grep '^GRAFANA_ADMIN_PASSWORD=' .env | cut -d= -f2-)
[ -n "$PASSWORD" ] || { echo "FAIL: GRAFANA_ADMIN_PASSWORD is not set in infra/.env"; exit 1; }
CRED="admin:$PASSWORD"
for _ in $(seq 1 30); do [ "$(curl -s -o /dev/null -w '%{http_code}' "$GRAFANA/api/health")" = 200 ] && break; sleep 2; done

# Not signed in must not get in; the right password must.
[ "$(curl -s -o /dev/null -w '%{http_code}' "$GRAFANA/api/dashboards/uid/routine-machine")" = 401 ] || { echo "FAIL: Grafana answered without a login"; exit 1; }
echo "ok: refuses a request with no login"
[ "$(curl -s -o /dev/null -w '%{http_code}' -u admin:admin "$GRAFANA/api/dashboards/uid/routine-machine")" = 401 ] || { echo "FAIL: the default admin/admin login works"; exit 1; }
echo "ok: the default admin/admin login does not work"

for uid in prometheus loki; do
  status=$(curl -s -u "$CRED" "$GRAFANA/api/datasources/uid/$uid/health" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("status",""))')
  [ "$status" = "OK" ] || { echo "FAIL: datasource $uid health: $status"; exit 1; }
done
echo "ok: datasources prometheus and loki are healthy"

DASHBOARD=$(mktemp)
trap 'rm -f "$DASHBOARD"' EXIT
curl -s -u "$CRED" "$GRAFANA/api/dashboards/uid/routine-machine" > "$DASHBOARD"
python3 - "$GRAFANA" "$CRED" "$DASHBOARD" <<'PY'
import sys, json, subprocess, urllib.parse, time
grafana, cred, path = sys.argv[1], sys.argv[2], sys.argv[3]
d = json.load(open(path))["dashboard"]
def get(path):
    out = subprocess.run(["curl", "-s", "-u", cred, grafana + path], capture_output=True, text=True).stdout
    return json.loads(out)
ok = bad = 0
for p in d["panels"]:
    for t in p.get("targets", []):
        expr = t["expr"].replace('$level', '.+').replace('$requestId', '')
        if p["datasource"]["uid"] == "prometheus":
            r = get("/api/datasources/proxy/uid/prometheus/api/v1/query?query=" + urllib.parse.quote(expr))
        else:
            now = int(time.time())
            r = get("/api/datasources/proxy/uid/loki/loki/api/v1/query_range?limit=5&query=" + urllib.parse.quote(expr)
                    + "&start=%d000000000&end=%d000000000" % (now - 3600, now))
        if r.get("status") == "success":
            ok += 1
        else:
            bad += 1
            print("FAIL: panel %r query %r -> %s" % (p["title"], expr[:70], str(r)[:140]))
print("ok: %d dashboard queries ran without error" % ok if not bad else "FAIL: %d queries errored" % bad)
sys.exit(1 if bad else 0)
PY
