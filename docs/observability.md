# Observability

## Purpose

How the application is watched: metrics, logs and traces across the backend and the intelligence
service, and the owner-only page that shows them. It follows the settlement engine's
`docs/observability.md` (Invoice-Financing repo, `dev` branch) as the reference, and says where it
differs and why. Built in parts; this file describes only what exists.

## Current state

Staged Prompt 3 is built in five parts. **Parts 1 to 3 are done, and part 4 is half done (4a: Prometheus, Alertmanager and the alert rules).** Loki, Promtail, Grafana, Jaeger and tracing, and the page, are not built.

| Part | What | State |
|---|---|---|
| 1 | Backend: request id, JSON logs, metrics endpoint on its own port | Done |
| 2 | Intelligence service: request id passed through, JSON logs, request metrics on their own port | Done |
| 3 | Custom metrics (credits, syncs, note assessment, questioned credits) | Done |
| 4 | Compose profile `observability`: Prometheus, Grafana, Loki and Promtail, Jaeger, Alertmanager, OpenTelemetry, alert rules | In three steps. **4a Prometheus, Alertmanager and the alert rules: done.** 4b Loki and Promtail: not built. 4c Grafana, Jaeger and OpenTelemetry tracing: not built |
| 5 | Owner-only operations page in the frontend (working name `/watchtower`) | Not built |

### Part 1: the backend

- **Request id.** `RequestIdFilter` (`com.routinemachine.core.observability`) runs before Spring
  Security, so refused requests get one too. It reuses an incoming `X-Request-Id` only if it matches
  `[A-Za-z0-9._-]{1,64}`, otherwise generates a UUID (a header with a newline or spaces could forge or
  break a log line). The id is put in the MDC as `requestId` for the whole request, echoed on the
  response, and removed afterwards, also when the request fails.
- **One log line per request**, written when it ends: `request completed` with `method`, `path`,
  `status` and `durationMs`, carrying `requestId`. Never the query string, a header or a body. The
  `/actuator/` paths are not logged, so a metrics scrape every few seconds does not drown real lines.
- **JSON logs.** `logging.structured.format.console: logstash` in `application.yml`: one JSON object per
  line, MDC and key-value fields included. Spring Boot's built-in structured logging, not the
  `logstash-logback-encoder` library (see the decisions log).
- **Metrics.** `spring-boot-starter-actuator` and `micrometer-registry-prometheus` (version managed by
  Spring Boot). `/actuator/prometheus` and `/actuator/health` are served on **port 8081**
  (`management.server.port`, env `MANAGEMENT_PORT`), not on 8080. Nothing else is exposed
  (`env`, `beans`, `heapdump`, `loggers` are 404). `docker-compose.yml` does not publish 8081, so it
  is reachable only from other containers on the Compose network, which is where Prometheus will run.
  Health details are hidden (`show-details: never`).
- Tests: `RequestIdFilterTest` (7) and `ObservabilityEndpointsIntegrationTest` (5), both written first.

### Part 2: the intelligence service

- **Same request id rules** (`observability.py`, a pure ASGI middleware): an incoming `X-Request-Id` is
  reused only if it matches `[A-Za-z0-9._-]{1,64}`, otherwise a UUID is made; it is echoed on the
  response, stamped on every log record, and **sent on to the backend** on every call (`_backend_headers`),
  so one request has one id in both services' logs.
- **One log line per request**, `request completed`, with `method`, `path` (no query string), `status`,
  `durationMs`. Logs are one JSON object per line (`JsonFormatter`). Only `method`, `path`, `status` and
  `durationMs` can be added to a line; any other field a logger tries to attach is dropped, so a note or
  an amount cannot leak through `extra=`. uvicorn's own access log, which prints query strings, is off
  (`--no-access-log`).
- **Metrics**: `http_requests_total{method,route,status}` and `http_request_duration_seconds{method,route}`,
  served by `prometheus_client` on **port 9000** (`METRICS_PORT`; unset or 0 means off). `route` is the route
  template, and anything that matches no route is the single label `unmatched`, so a scan of random paths
  cannot create unlimited series. `/metrics` is not served on the API port (404). Compose does not publish
  9000.
- uvicorn's access logger is kept handler-free (`--no-access-log` alone is not enough once the root logger has a handler: uvicorn re-enables it), and `httpx`'s per-call URL logging is set to WARNING.
- Tests: `test_observability.py` (16), written first.

### Part 3: the custom metrics

| Metric | Where | Tags | Meaning |
|---|---|---|---|
| `credits_events_total` | backend, `EvidenceCreditService` | `status` (ACCEPTED, REJECTED, PENDING, SELF_REPORTED), `rule_version` | Each new piece of evidence, by what was decided and by which published rule version (0 = no rule). A duplicate is not counted again |
| `credits_questioned_total` | backend, `EvidenceEventController` | none | The first time a credit is questioned (asking again does not count) |
| `sync_runs_total` | backend, each integration's client (`SyncMetrics`) | `source` (hub, wakatime, activitywatch, github, rescuetime), `outcome` (success, failure) | One count per fetch from that source. A missing API key is neither (the source is simply off). Hub counts directory listings only |
| `sync_consecutive_failures` | backend, `SyncMetrics` | `source` | Failures in a row, back to 0 on a success: what the "failing three runs in a row" alert reads |
| `sync_last_success_timestamp_seconds` | backend, `SyncMetrics` | `source` | When the source last succeeded |
| `assessment_requests_total` | intelligence service | `outcome` (success, unavailable, upstream_error, unparseable) | Reading-note assessments by how they ended |
| `assessment_duration_seconds` | intelligence service | none | How long an assessment takes (histogram) |
| `assessment_tokens_total` | intelligence service | `direction` (input, output) | Model tokens, from the model's own usage report |
| `assessment_estimated_cost_usd_total` | intelligence service | none | Tokens times the prices you set in `ANTHROPIC_INPUT_USD_PER_MTOK` and `ANTHROPIC_OUTPUT_USD_PER_MTOK` (USD per million tokens). **0 until you set them**: no price is assumed |

No tag is a user, goal, book or note. Tests assert that on the credit and sync meters and that no assessment
metric or log line carries the note text.

Known limitation: the sync clients swallow failures into an empty result, so the metric is recorded where each
client already logs the failure. Their HTTP calls have fixed URLs and no tests, so that wiring is verified by
running the stack, not by unit tests. In Docker there is no local ActivityWatch server, so that source counts a
failure on every poll; the alerts in part 4 must account for sources that are not expected to be up.

### Part 4a: Prometheus, Alertmanager and the alerts

Started only with the profile: `docker compose --profile observability up -d` (a plain `docker compose up`
does not start them). Files are in `infra/observability/`.

- **Prometheus** (`prom/prometheus:v3.13.1`, the reference's version; 3.13.4 exists as a newer patch) scrapes
  three static targets every 15 seconds: the backend's management port (`backend:8081`), the intelligence
  service's metrics port (`intelligence-service:9000`) and itself. UI at `http://localhost:9090`, bound to this
  machine only (`127.0.0.1`). 7 days of data, memory capped at 512 MB.
- **Alertmanager** (`prom/alertmanager:v0.33.1`) at `http://localhost:9093`, same binding, 128 MB cap. No
  notification integration: a firing alert is visible in its own page and in Prometheus's Alerts page.
- **The rules** (`alerts.yml`): `BackendDown` and `IntelligenceServiceDown` (target down for a minute),
  `SyncSourceFailing` (`sync_consecutive_failures` of 3, with two deliberate exceptions: hub makes three
  listing calls per run so it alerts at 9, and ActivityWatch, a local-only source with no server in Docker,
  never alerts) and `AssessmentFailureRateHigh` (more than half of assessments ending in `upstream_error` or
  `unparseable` for 15 minutes; a missing key and no traffic at all are not failures).
- **Tests first**: `alerts_test.yml` is a `promtool` unit test of each rule (down, recovery, thresholds, the
  exceptions, the no-traffic case). `infra/observability/check.sh` validates both configs and runs it.
- Metrics and both UIs are not reachable from the browser-facing frontend: the backend's metrics port is
  8081 and unpublished, the intelligence service's is 9000 and unpublished, and Prometheus and Alertmanager
  bind to 127.0.0.1.

## What it records about people

- Logs: method, path, status, duration, request id, and the application's own messages. No query
  string, no body, no header, no email, no note text, no money amount. The path can contain a numeric
  row id (for example `/api/tasks/57`), which is not personal on its own.
- Metrics: JVM and HTTP counters and timings, tagged by route template, never by user.
- The owner claim and owner role log lines name tables and counts, never an address.
- The intelligence service follows the same rules; it receives note text only on `POST /reading-notes/assess`, whose body is never logged.
- Later parts must keep to this list; each part's report states what it added.

## Turning it off

- The observability containers: `docker compose --profile observability down` (add `-v` to delete their
  stored data). Nothing in the application depends on them; the services keep serving their metrics ports.

- Metrics port: set `MANAGEMENT_PORT` to a different port, or `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health`
  to drop the Prometheus endpoint.
- JSON logs: set `LOGGING_STRUCTURED_FORMAT_CONSOLE=` (empty) to return to plain text lines.
- The request id and the completion line have no switch; removing `RequestIdFilter` removes both.
- The cost of the whole stack is stated when part 4 is finished. So far (4a, measured on this machine with the services idle): Prometheus about 97 MB of memory and Alertmanager about 38 MB, under 1% CPU each; images 335 MB and 114 MB on disk; Prometheus data about 0.1 MB after a few minutes, growing at roughly that rate for the 7 days it keeps.

## Decisions log

| Date | Decision | Reason |
|------|----------|--------|
| 2026-10-21 | Spring Boot's built-in structured logging (`logstash` format) instead of `logstash-logback-encoder` | The latest encoder (9.0) needs Jackson 3, and Spring Boot 3.5 uses Jackson 2; Boot 3.4 and later emit the same Logstash JSON with the MDC included and no extra dependency. Differs from the staged prompt, which named the library |
| 2026-10-21 | Metrics on a separate management port (8081), not published to the host, instead of exposing `/actuator/**` on the API port and relying on routing | The staged prompt requires it never share the port the browser talks to. Following the reference's lesson: an endpoint that is never served on the public port cannot be reached through an auth mistake there |
| 2026-10-21 | Alert rules are unit-tested with `promtool test rules` before they were written | An alert that has never been shown to fire (and to stay quiet when it should) is a guess. The tests cover firing, the `for` delay, recovery and the deliberate exceptions |
| 2026-10-21 | `SyncSourceFailing` skips ActivityWatch and alerts hub at 9, not 3 | ActivityWatch has no server in the Docker stack and fails every poll by design; hub counts three listing calls per run, so 3 is one failed run, not three |
| 2026-10-21 | Prometheus and Alertmanager UIs bound to 127.0.0.1, behind a compose profile | Operations data is for the owner on this machine; a plain `docker compose up` stays light |
| 2026-10-21 | Only `health` and `prometheus` exposed | Everything else Actuator offers (env, beans, heap dump, loggers) can leak configuration or data |
| 2026-10-21 | Incoming `X-Request-Id` accepted only if short and plain | An unchecked header written into logs can forge log lines |
| 2026-10-21 | Intelligence service metrics with `prometheus_client` directly, not `prometheus-fastapi-instrumentator` | The instrumentator's support for Starlette 1.x (this service runs 1.6) is unverified; a counter and a histogram in a small ASGI middleware are about 30 lines, fully tested, and add no compatibility risk. Differs from the staged prompt's wording only in the library; the endpoint and port are as asked |
| 2026-10-21 | Intelligence service metrics on port 9000 (own server), not a `/metrics` route on the API app | The staged prompt requires metrics never share the port other services call; a route on the API app would |
| 2026-10-21 | Log records can carry only `method`, `path`, `status`, `durationMs` into the JSON line | An allow-list is safer than a block-list for keeping note text and amounts out of logs |
| 2026-10-21 | Request completion line logs method and path only | Query strings and bodies can carry note text or amounts, which must never reach logs |

## Open questions

- Whether the owner-only page should embed Grafana, or read Prometheus and Loki directly, is decided
  in part 5.
- Alert delivery: like the reference, no pager or email integration is planned for a personal tool;
  alerts will be visible in Alertmanager's own page. Say if you want one.
