#!/usr/bin/env bash
# Validates the observability configuration and runs the alert-rule unit tests. Needs Docker.
# Usage: infra/observability/check.sh
set -euo pipefail
cd "$(dirname "$0")"
PROM_IMAGE=prom/prometheus:v3.13.1
AM_IMAGE=prom/alertmanager:v0.33.1
LOKI_IMAGE=grafana/loki:3.7.9
ALLOY_IMAGE=grafana/alloy:v1.21.0

echo "== prometheus.yml and alerts.yml"
docker run --rm --entrypoint promtool -v "$PWD":/etc/prometheus:ro "$PROM_IMAGE" check config /etc/prometheus/prometheus.yml
echo "== alert rule unit tests"
docker run --rm --entrypoint promtool -v "$PWD":/w -w /w "$PROM_IMAGE" test rules alerts_test.yml
echo "== alertmanager.yml"
docker run --rm --entrypoint amtool -v "$PWD":/etc/alertmanager:ro "$AM_IMAGE" check-config /etc/alertmanager/alertmanager.yml
echo "== loki.yml"
docker run --rm -v "$PWD/loki.yml":/etc/loki/loki.yml:ro "$LOKI_IMAGE" -config.file=/etc/loki/loki.yml -verify-config
echo "  ok"
echo "== alloy.alloy"
docker run --rm -v "$PWD/alloy.alloy":/etc/alloy/config.alloy:ro "$ALLOY_IMAGE" validate /etc/alloy/config.alloy
echo "  ok"
