#!/usr/bin/env bash
# Validates the observability configuration and runs the alert-rule unit tests. Needs Docker.
# Usage: infra/observability/check.sh
set -euo pipefail
cd "$(dirname "$0")"
PROM_IMAGE=prom/prometheus:v3.13.1
AM_IMAGE=prom/alertmanager:v0.33.1

echo "== prometheus.yml and alerts.yml"
docker run --rm --entrypoint promtool -v "$PWD":/etc/prometheus:ro "$PROM_IMAGE" check config /etc/prometheus/prometheus.yml
echo "== alert rule unit tests"
docker run --rm --entrypoint promtool -v "$PWD":/w -w /w "$PROM_IMAGE" test rules alerts_test.yml
echo "== alertmanager.yml"
docker run --rm --entrypoint amtool -v "$PWD":/etc/alertmanager:ro "$AM_IMAGE" check-config /etc/alertmanager/alertmanager.yml
