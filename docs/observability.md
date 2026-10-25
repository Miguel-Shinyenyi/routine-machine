# Observability

## Purpose

How the application is watched: metrics, logs and traces across the backend and the intelligence
service, and the owner-only page that shows them. It follows the settlement engine's
`docs/observability.md` (Invoice-Financing repo, `dev` branch) as the reference, and says where it
differs and why. Built in parts; this file describes only what exists.

## Current state

Staged Prompt 3 is built in five parts. **Parts 1 to 3 are done, and part 4 is mostly done (4a Prometheus, Alertmanager and the alert rules; 4b Loki and Alloy for logs; 4c-i Jaeger and tracing; 4c-ii Grafana and its dashboard; metric exemplars).** Part 5, the owner-only page, is in progress: pieces 1 to 3 of 6 are done (the owner gate and service health; the charts' data; the logs and trace links, in the backend); the frontend page is not built.

| Part | What | State |
|---|---|---|
| 1 | Backend: request id, JSON logs, metrics endpoint on its own port | Done |
| 2 | Intelligence service: request id passed through, JSON logs, request metrics on their own port | Done |
| 3 | Custom metrics (credits, syncs, note assessment, questioned credits) | Done |
| 4 | Compose profile `observability`: Prometheus, Grafana, Loki and Promtail, Jaeger, Alertmanager, OpenTelemetry, alert rules | In three steps. **4a Prometheus, Alertmanager and the alert rules: done. 4b Loki and Alloy: done. 4c-i Jaeger and OpenTelemetry tracing: done.** 4c-ii Grafana and the dashboard: done, checked by `verify-grafana.sh` |
| 5 | Owner-only operations page in the frontend (working name `/watchtower`) | In progress, 6 pieces. **Pieces 1 to 4 done: backend owner gate and service health; metrics for the charts; logs and trace links; sync status.** Not built: the frontend page, the API-docs link (needs Prompt 4) |

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
- Logs reach Loki only through Alloy; see part 4b.
- Metrics and both UIs are not reachable from the browser-facing frontend: the backend's metrics port is
  8081 and unpublished, the intelligence service's is 9000 and unpublished, and Prometheus and Alertmanager
  bind to 127.0.0.1.

### Part 4b: logs in Loki

- **Loki** (`grafana/loki:3.7.9`, single binary, filesystem storage, 7 days kept, 512 MB cap) at
  `http://localhost:3100`, bound to this machine only.
- **Grafana Alloy** (`grafana/alloy:v1.21.0`) ships the logs. The reference used Promtail, which is deprecated
  (its last release was May 2026); Alloy is its maintained replacement.
- **Scope, on purpose** (`alloy.alloy`): only containers of the Compose project `infra` (the folder name of
  `infra/docker-compose.yml`, so another project's container also called `backend` is never read), and only the
  two services that write the scrubbed JSON logs, `backend` and `intelligence-service`. **Postgres is left
  out** because an error line can contain row values such as an email address; the **frontend is left out**
  because its server logs are not covered by the no-personal-data rule yet.
- **Docker access is read-only and narrow.** Alloy talks to Docker through `tecnativa/docker-socket-proxy`
  (v0.5.0) allowing only containers and networks, no writes. The raw `/var/run/docker.sock` is mounted into
  that proxy and nowhere else.
- **Labels**: `service` and `level` only. The request id stays in the line (a label per request would explode the
  index). Find a request with `{service=~".+"} | json | requestId="<id>"`: one id returns the backend lines and
  the intelligence service's line for the same request.
- **Check**: `infra/observability/verify-logs.sh` against the running stack: the request id is found in Loki,
  only the two services appear, no Postgres or frontend line, and no `secret-words` (a query-string value),
  `@gmail.com` or `password` in anything shipped. `check.sh` also validates `loki.yml` and `alloy.alloy`.
- Cost, measured idle: Loki about 97 MB, Alloy about 110 MB, the socket proxy about 28 MB of memory. Images:
  Loki 191 MB, Alloy 928 MB (large), proxy 67 MB. Log data about 0.2 MB after a few minutes.

### Part 4c-i: traces in Jaeger

- **Off by default.** `TRACING_ENABLED=true` turns it on (for example
  `TRACING_ENABLED=true docker compose --profile observability up -d`). Off, no agent is loaded and nothing is
  exported; the request id, JSON logs and metrics all work as before.
- **Backend**: the OpenTelemetry Java agent (2.32.0), downloaded in the Dockerfile and **checked against the
  SHA-256 Maven Central publishes**, so a changed download fails the build. `backend/entrypoint.sh` attaches it
  only when tracing is on. It traces HTTP, repository calls and JDBC. It also puts `trace_id` and `span_id` in
  the log MDC, so they appear on the JSON log lines with no code.
- **Intelligence service**: `opentelemetry-instrument` (auto-instrumentation 0.66b1, SDK 1.45.1) around uvicorn,
  covering FastAPI and the outbound httpx calls to the backend; `traceId` is added to its JSON log lines
  (`observability.py`, tested). W3C `traceparent` carries the trace from the intelligence service into the
  backend, so one request is one trace.
- **Jaeger v2** (`jaegertracing/jaeger:2.22.0`, in-memory storage, so traces do not survive a restart) receives
  OTLP over gRPC on port 4317 inside the Compose network; UI at `http://localhost:16686`, bound to this machine.
  Traces only: the metrics and logs exporters are off, those have their own pipelines.
- **Check**: `infra/observability/verify-traces.sh` (stack running with tracing on): the intelligence service's
  log line carries a trace id, the backend's log line carries the same one, Jaeger has one trace with spans from
  both services, and the trace contains no bearer token or authorization header.
- **What a trace records**: operation names, timings, status codes, the **URL of each request including its
  query string**, and the SQL text with parameters as `?` (never the values). No headers, no bodies. The query
  string is the part to watch: today the API's query strings hold dates and ids only, but a future route that
  put a note or an amount in one would put it in the trace; keep such values in bodies.
- Cost, measured idle with tracing on: the backend about 445 MB against 327 MB off (about 120 MB more), the
  intelligence service about 55 MB against 42 MB, Jaeger about 20 MB; Jaeger's image 164 MB.
- **The trace id on a metric (exemplars), done in four steps.** The staged prompt asks to show the
  trace id in the metric, the log line and the trace. A metric carries it as a Prometheus *exemplar*, a trace id
  attached to one observation of a histogram bucket. Steps: (1) the intelligence service attaches the id to
  `http_request_duration_seconds` observations made inside a trace (done); (2) the backend does the same for
  `http_server_requests` (done: `OtelSpanContext` is a `SpanContext` bean that reads the OpenTelemetry API, which the
  Java agent backs; Spring Boot's own version needs Micrometer Tracing, which this service does not use); (3) Prometheus
  stores them (done: `--enable-feature=exemplar-storage` on the Prometheus service; it negotiates OpenMetrics with
  both services on its own, so `prometheus.yml` did not change); (4) Grafana shows them on the two p95 panels
  (`exemplar: true`) and links each to its trace in the Jaeger UI (done, see 4c-ii). An exemplar appears only in the OpenMetrics text, which both services serve
  when the scraper asks for it; the plain text is unchanged. With tracing off there is no span, so none is attached.
  Checked live (tracing on): a request's latency bucket carried `trace_id="c4989b2f…"` on the backend and
  `trace_id="92f77443…"` on the intelligence service, and the backend's id was a real trace in Jaeger and on the
  backend's `request completed` log line. Prometheus keeps the latest exemplar per bucket for the same 7 days as its
  samples. Check: `infra/observability/verify-exemplars.sh` (stack running with tracing on): after traced requests,
  Prometheus's exemplar API returns a trace id for each service's latency histogram and each is a real trace in
  Jaeger. Run on 2026-10-25: both exemplars carried the same trace id, one `/patterns` request across both services.
  It first failed with the flag off. With tracing off no exemplar is attached, so the script fails by design.

### Part 4c-ii: Grafana and the dashboard

- **Grafana** (`grafana/grafana:13.2.4`, profile `observability`) at `http://localhost:3001`, bound to this machine.
  Anonymous access, sign-up, Gravatar and update checks are off.
- **Admin password**: `GRAFANA_ADMIN_PASSWORD` in `infra/.env` (name only in `.env.example`; generate one with
  `openssl rand -base64 24`). The service's entrypoint refuses to start when it is empty, so Grafana never runs on its
  default `admin/admin` login. The variable is **not** required by other compose commands: a `:?` on it would fail
  every `docker compose` call, profile or not.
- **Provisioned, not clicked together** (`infra/observability/grafana/provisioning/`): two datasources (Prometheus,
  Loki; not editable) and one dashboard, `Routine Machine` (`dashboards/routine-machine.json`, not editable
  in the UI). Change the JSON in the repo.
- **Dashboard**: services up; p95 latency by route for the backend and the intelligence service; credits decided per
  hour by status and by rule version; questioned credits; sync fetches, failures in a row and time since last success
  per source; assessments, p95 latency, tokens and estimated cost; and the application logs, filtered by level and by
  request or trace id. The level filter lists `WARN` (backend) and `WARNING` (intelligence service) and `CRITICAL`,
  because the two services name levels differently.
- **Metric to trace**: the two p95 panels show exemplars as dots; the Prometheus datasource's
  `exemplarTraceIdDestinations` turns each one's `trace_id` into a link to `http://localhost:16686/trace/<id>`.
  Needs tracing on; with it off the charts are as before. Shown through Grafana's own query API (the call a panel
  makes), not yet seen as dots in a browser.
- **Log to trace**: a log line that carries a trace id (`traceId` or `trace_id`) gets a link that opens that trace in
  Jaeger (`http://localhost:16686/trace/<id>`). Tracing must be on (`TRACING_ENABLED=true`) for lines to carry one.
- **No Jaeger datasource in Grafana.** Jaeger 2.22 dropped the v1 listing and search endpoints (`/api/services`,
  `/api/operations` and `/api/traces?service=...` return 404); fetching one trace by id (`/api/traces/<id>`, which
  `verify-traces.sh` uses) and `/api/v3/*` still work. Grafana's Jaeger datasource (plugin 13.0.5) calls
  `/api/services`, so its health check fails with `404 Not Found`. Traces are read in the Jaeger UI, not inside Grafana.
  Revisit when Grafana's plugin supports the v3 API.
- **Latency buckets**: the backend now publishes `http_server_requests_seconds_bucket`
  (`management.metrics.distribution.percentiles-histogram`), which Spring Boot does not do by default. The p95 panel
  needs it. A test covers it.
- **Check**: `infra/observability/verify-grafana.sh` (stack running): no login is refused, the default login does not
  work, the two datasources are healthy, the dashboard is loaded, every query on it runs without error, the
  Prometheus datasource links exemplars to Jaeger, and the p95 panels' exemplar queries return trace ids through
  Grafana. A chart
  with no data is fine on a new stack; a query that errors is not. Run on 2026-10-25: all checks pass, 14 queries.
  The backend p95 panel shows no data until the backend image is rebuilt, because the running image predates the
  latency-bucket setting.

### Part 5: the owner-only operations page

Six pieces: (1) the owner gate and service health in the backend; (2) metrics for the charts, read from Prometheus
by the backend; (3) logs, read from Loki with allow-listed filters, and trace links; (4) each sync source's last run
and result; (5) the frontend page and its 404; (6) docs, with the API-docs link waiting for Prompt 4.

**Piece 1, done.** `GET /api/watchtower/services` (`WatchtowerController`, `ServiceHealthService`):
- **Owner only.** The owner is the account named by `OWNER_EMAIL` (`OwnerAccount`). A signed-in person who is not the
  owner, a signed-out visitor, and everyone when no owner is configured all get a plain **404**, never a 401 or a
  login prompt, so the page is not known to exist. For that the path is on the optional-token security chain (the
  hillside's), not the token-required one. A malformed token still gets 401, as on the hillside.
- **What it returns**: a list of `{name, status}` with `UP`, `DOWN` or `NOT_CONFIGURED`. `backend` and `database`
  come from the app's own health; the intelligence service, Prometheus, Alertmanager, Loki, Jaeger and Grafana are
  probed (2 s timeout, in parallel) at addresses in `WATCHTOWER_*_URL`, set in `infra/docker-compose.yml`. The
  addresses are configuration, never request input, so a request cannot make the backend fetch anything. The
  answer holds no address and no error text. Without the `observability` profile the observability services show as
  `DOWN`.
- **Route sweep**: `EveryRouteNeedsATokenTest` lists `/api/watchtower` as open on purpose, with the reason. The
  route's own tests (`WatchtowerControllerTest`, `WatchtowerWithNoOwnerTest`) prove it gives nothing without the
  owner's token.
- **Checked live**: owner 200 with all eight services `UP`; another account 404; signed out 404; a malformed token
  401; stopping Loki made it `DOWN`.
- **Seen once, not explained**: Loki's own `/ready` answered 503 twice for a short time, so the page showed Loki
  `DOWN` although logs were flowing; it then answered 200 on every try. The probe does not retry, so a brief dip
  shows as `DOWN` for that refresh.

**Piece 2, done.** `GET /api/watchtower/metrics?range=1h|6h|24h` (`WatchtowerMetricsService`), same owner gate:
- **A fixed list of 12 queries** (`WatchtowerMetricsService.QUERIES`): the p95 latency of both services, credits by status
  and rule version, questioned credits, sync runs, failures in a row and time since last success, assessments, their
  latency, tokens and estimated cost. They are the same expressions as the Grafana dashboard, and
  `WatchtowerQueriesMatchDashboardTest` fails if either side changes alone. A caller chooses only the range (default
  6h; 1h steps 30 s, 6h steps 60 s, 24h steps 300 s). Anything else is a 400, and a query string or `expr` parameter in
  the request is ignored: it never reaches Prometheus.
- **The answer** is `{available, range, charts: [{id, title, unit, series: [{label, points: [[seconds, value]]}]}]}`.
  `available` is false, with no charts and a 200, when Prometheus is not configured or answers none of the queries
  (the stack is not running). One failing query empties only its own chart. `NaN`, `+Inf` and `-Inf` points are
  dropped, so a chart shows a gap. A chart carries at most 12 series (`MAX_SERIES`); the extra are cut, in
  Prometheus's order.
- **Prometheus address**: `WATCHTOWER_PROMETHEUS_BASE_URL` (`http://prometheus:9090` in Compose); configuration, never
  request input. Labels in the answer are route templates, sync sources, outcomes, statuses and rule versions: the
  tags the metrics already carry, never a user, goal, book or note.
- **Checked live** (owner token, real Prometheus): 200 with 12 charts and real points for the latency and sync charts;
  `range=24h` 200; `range=9999d` 400; another account 404; signed out 404. The credit, assessment and questioned-credit
  charts were empty because this stack has recorded none of those events yet.
- **Seen, not changed**: the backend latency chart reached the 12-series cap, so some routes are cut from the page
  (Grafana shows all). A series whose points were all `NaN` is returned with an empty `points` list.
- **A bug the tests found**: Java cannot parse Prometheus's `+Inf`, and the first version lost a whole chart to one such
  point. One unreadable point is now skipped.

**Piece 3, done.** `GET /api/watchtower/logs?level=&requestId=&range=` (`WatchtowerLogsService`), same owner gate. The gate
runs before any validation, so a non-owner gets 404 whatever they send.
- **What reaches Loki.** Only two values from the request, both checked: `level`, which must be one of `INFO`, `WARN`,
  `WARNING`, `ERROR`, `CRITICAL`, `DEBUG` (the Grafana dashboard's list, kept equal by a test), and `requestId`, which
  must be 1 to 64 of letters, digits, dot, underscore, hyphen (the services' own request-id pattern; a trace id fits it
  too). It is placed as a plain-text match (`|= "..."`), never a regex. Anything else is a 400 and Loki is not asked. The
  services read are fixed (`backend|intelligence-service`); a `service` or `query` parameter in the request is ignored.
  A blank `requestId` is refused too, so the page must leave the parameter out when its box is empty.
- **The answer** is `{available, range, entries: [{time, service, level, message, requestId, traceId, traceUrl}]}`:
  at most 100 entries (`MAX_ENTRIES`), merged from Loki's streams newest first, messages cut at 1,000 characters. Each line is
  reduced to those fields, so a logger name, a thread name or any other field in the line is not passed on. A line that
  is not JSON (for example a JVM start-up message) is returned as its text with no ids. `available` is false with a 200
  when Loki is not configured or does not answer.
- **Trace links.** `traceUrl` is `WATCHTOWER_JAEGER_UI_URL` + `/trace/` + the id, only when the line carries a real
  32-character trace id and the address is set. That address is the one a browser opens (`http://localhost:16686` in
  Compose), not the one the backend probes inside Docker. Lines carry a trace id only when `TRACING_ENABLED=true`.
- **Addresses** are configuration: `WATCHTOWER_LOKI_BASE_URL` (`http://loki:3100`) and `WATCHTOWER_JAEGER_UI_URL`.
- **Shared range.** `WatchtowerRange` (1h, 6h, 24h and each step) is now one enum used by the charts and the logs.
- **Checked live** (tracing on): a request sent with a known id was found by that id and, after Alloy picked up the
  recreated backend container, returned the 2 backend lines and the 1 intelligence line (matching the container log and
  Loki directly), each with a trace link; `level=ERROR` 200; an injection attempt in the id and in the level 400; another
  account 404; signed out 404.
- **Seen**: right after the backend container was recreated, the endpoint returned only the intelligence service's line
  for a few seconds, because Alloy had not yet attached to the new container. Logs from before a restart are still there.

**Piece 4, done.** `GET /api/watchtower/sync` (`WatchtowerSyncService`), same owner gate. It adds no new data. It joins what
`/api/watering-holes` already knew with the run state Prometheus already held.
- **The answer** is `{available, sources: [{source, enabled, lastActivityAt, result, consecutiveFailures, lastSuccessAt,
  successes24h, failures24h}]}` for the six sources (HUB, WAKATIME, ACTIVITYWATCH, GITHUB, RESCUETIME, HEALTH).
  `enabled` and `lastActivityAt` come from the database through `WateringHoleStatusService`, which I extracted from
  `WateringHoleStatusController` so the two pages read one copy; the watering holes page still masks it for non-owners.
- **Prometheus is asked three fixed questions** (`WatchtowerSyncService.QUERIES`): failures in a row, the last success
  (`max_over_time` over 7 days, Prometheus's retention) and successes and failures over 24 hours by source. Nothing in
  the request reaches it. The address is `WATCHTOWER_PROMETHEUS_BASE_URL`, as for the charts.
- **The result word**, in this order: `off` (switched off), `push only` (HEALTH, which the backend never fetches),
  `unknown` (Prometheus did not answer), `failing` (failures in a row above 0), `ok` (a last success is known),
  `never succeeded`. `consecutiveFailures` and `lastSuccessAt` are null for a source Prometheus has no series for, not
  0, because a source that has not run is not one with no failures.
- **What survives a restart.** The 24-hour counts and the last success do (Prometheus keeps them). Failures in a row
  do not: the gauge lives in the backend process, so it starts again after a restart. A source that failed before a
  restart reads `ok` or `never succeeded` until it fails again. The first live run showed this for the last success, so the
  query now looks back 7 days.
- **Known gap.** Nothing records when a source last failed or last ran. A last-failure gauge would change the metrics,
  the alert docs and the dashboard, so I did not add one. `lastActivityAt` is when data last arrived, not when the sync
  last ran.
- **Checked live** (owner token, real Prometheus): 200 with six sources; HUB, GITHUB and ACTIVITYWATCH `failing`, WAKATIME
  `ok`, RESCUETIME and HEALTH `off`; another account 404; signed out 404. Before the 7-day lookback, HUB and GITHUB
  showed no last success right after a backend recreate, although they had succeeded earlier that day.
- **Seen, not looked into**: HUB and GITHUB were failing in the live stack (HUB 3 failures in a row; GITHUB 1), and
  ACTIVITYWATCH fails every poll by design (no server in the Docker stack).

## What it records about people

- Logs: method, path, status, duration, request id, and the application's own messages. No query
  string, no body, no header, no email, no note text, no money amount. The path can contain a numeric
  row id (for example `/api/tasks/57`), which is not personal on its own.
- Metrics: JVM and HTTP counters and timings, tagged by route template, never by user.
- The owner claim and owner role log lines name tables and counts, never an address.
- The operations page's data (`/api/watchtower`) is owner-only. So far it holds service names and statuses, the
  metrics the stack already records (with the same labels), and recent log lines. Those lines are the scrubbed
  ones already in Loki, reduced to time, service, level, message, request id and trace id.
- The intelligence service follows the same rules; it receives note text only on `POST /reading-notes/assess`, whose body is never logged.
- Later parts must keep to this list; each part's report states what it added.

## Turning it off

- The observability containers: `docker compose --profile observability down` (add `-v` to delete their
  stored data). Nothing in the application depends on them; the services keep serving their metrics ports.

- Metrics port: set `MANAGEMENT_PORT` to a different port, or `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health`
  to drop the Prometheus endpoint.
- JSON logs: set `LOGGING_STRUCTURED_FORMAT_CONSOLE=` (empty) to return to plain text lines.
- The request id and the completion line have no switch; removing `RequestIdFilter` removes both.
- The cost of the whole stack is stated when part 4 is finished. So far (4a and 4b, measured on this machine with the services idle): Prometheus about 50 to 97 MB, Alertmanager 13 to 38 MB, Loki 97 MB, Alloy 110 MB and the socket proxy 28 MB of memory, 1 to 3% CPU each; images about 335 + 114 + 191 + 928 + 67 MB = 1.6 GB on disk; data a fraction of a MB after a few minutes, growing for the 7 days kept.

## Decisions log

| Date | Decision | Reason |
|------|----------|--------|
| 2026-10-21 | Spring Boot's built-in structured logging (`logstash` format) instead of `logstash-logback-encoder` | The latest encoder (9.0) needs Jackson 3, and Spring Boot 3.5 uses Jackson 2; Boot 3.4 and later emit the same Logstash JSON with the MDC included and no extra dependency. Differs from the staged prompt, which named the library |
| 2026-10-21 | Metrics on a separate management port (8081), not published to the host, instead of exposing `/actuator/**` on the API port and relying on routing | The staged prompt requires it never share the port the browser talks to. Following the reference's lesson: an endpoint that is never served on the public port cannot be reached through an auth mistake there |
| 2026-10-21 | Jaeger v2 (`jaegertracing/jaeger:2.22.0`), not the reference's v1 all-in-one | v2 images are published now (the reference had to use 1.x because 2.x tags were missing) and v1 is end-of-life. Differs from the reference |
| 2026-10-21 | Tracing is opt-in with `TRACING_ENABLED`, not always on | The agent adds about 120 MB to the backend and exports to a collector that only exists under the profile; a plain `docker compose up` stays light and quiet |
| 2026-10-21 | The OpenTelemetry Java agent is pinned and checksum-verified in the Dockerfile | It runs inside the backend's JVM with full access; a downloaded jar must be exactly the one published |
| 2026-10-21 | Grafana Alloy instead of Promtail for log shipping | Promtail is deprecated and its last release was May 2026; Alloy (v1.21.0, October 2026) is Grafana's maintained replacement. Differs from the reference and the staged prompt, which named Promtail |
| 2026-10-21 | The log shipper reads Docker through a read-only socket proxy, not the raw socket | Anything holding `/var/run/docker.sock` can control the host; the proxy allows only reading containers and networks |
| 2026-10-21 | Only `backend` and `intelligence-service` logs are shipped; Postgres and the frontend are not | Postgres error lines can contain row values (emails); the frontend's server logs are not covered by the no-personal-data rule. An allow-list can only grow on purpose |
| 2026-10-21 | Alert rules are unit-tested with `promtool test rules` before they were written | An alert that has never been shown to fire (and to stay quiet when it should) is a guess. The tests cover firing, the `for` delay, recovery and the deliberate exceptions |
| 2026-10-21 | `SyncSourceFailing` skips ActivityWatch and alerts hub at 9, not 3 | ActivityWatch has no server in the Docker stack and fails every poll by design; hub counts three listing calls per run, so 3 is one failed run, not three |
| 2026-10-21 | Prometheus and Alertmanager UIs bound to 127.0.0.1, behind a compose profile | Operations data is for the owner on this machine; a plain `docker compose up` stays light |
| 2026-10-25 | Grafana's admin password is checked by the container's entrypoint, not by `${VAR:?}` in the compose file | `:?` is evaluated for every service, so it made every `docker compose` command fail without the variable. An empty value would otherwise fall back to Grafana's default `admin/admin` login |
| 2026-10-25 | No Jaeger datasource in Grafana; the log-to-trace link is a plain URL to the Jaeger UI | Found by running `verify-grafana.sh`: the datasource's health check gets `404` because Jaeger 2.22 dropped the v1 listing and search endpoints that Grafana's plugin calls (`/api/v3/services` answers 200, `/api/services` answers 404; fetching one trace by id still works on v1). Keeps Jaeger 2.22 (decided earlier) instead of pinning an older v2 |
| 2026-10-25 | The owner-only page reads Prometheus and Loki through the backend, instead of embedding Grafana | Resolves the open question. One place (the backend) holds the owner check and the 404 rule, with Java tests; the frontend only renders. Embedding Grafana would need its own login, a second access rule, and framing headers. Grafana stays for exploring. Pieces 2 and 3 build it; you can overrule before then |
| 2026-10-25 | The logs endpoint builds LogQL from a fixed service list plus a checked level and a checked id, matched as plain text | The request id box is the one place free text reaches Loki. A character set with no quote, brace, backslash, space or pipe cannot change the query, and `|=` is a substring match, not a regex. Rejecting is safer than escaping |
| 2026-10-25 | The page's queries are a fixed list in Java, mirrored from the Grafana dashboard, with a test that they match | A free-form query would let a caller run anything against Prometheus. Two copies of one expression drift, so `WatchtowerQueriesMatchDashboardTest` compares them. Cost: a query change is made in two files |
| 2026-10-25 | `/api/watchtower` is on the optional-token chain and listed as open on purpose in the route sweep | Prompt 3 says non-owners get a plain 404, not a login prompt. On the token-required chain a signed-out visitor gets 401, which shows the page exists. The sweep lists it with that reason so the exception is visible |
| 2026-10-25 | `opentelemetry-api` added to the backend, version managed by Spring Boot's BOM (1.49.0) | Exemplars need the current trace id inside the JVM. Spring Boot's own source for it needs Micrometer Tracing; this service is traced by the OpenTelemetry Java agent, which backs the OpenTelemetry API. The API alone is inert without the agent. Approved by Miguel |
| 2026-10-25 | Dashboard and datasources are provisioned from files and read-only in the UI | The dashboard is code: reviewed, versioned and restorable, and a click in the UI cannot change what the repo says |
| 2026-10-21 | Only `health` and `prometheus` exposed | Everything else Actuator offers (env, beans, heap dump, loggers) can leak configuration or data |
| 2026-10-21 | Incoming `X-Request-Id` accepted only if short and plain | An unchecked header written into logs can forge log lines |
| 2026-10-21 | Intelligence service metrics with `prometheus_client` directly, not `prometheus-fastapi-instrumentator` | The instrumentator's support for Starlette 1.x (this service runs 1.6) is unverified; a counter and a histogram in a small ASGI middleware are about 30 lines, fully tested, and add no compatibility risk. Differs from the staged prompt's wording only in the library; the endpoint and port are as asked |
| 2026-10-21 | Intelligence service metrics on port 9000 (own server), not a `/metrics` route on the API app | The staged prompt requires metrics never share the port other services call; a route on the API app would |
| 2026-10-21 | Log records can carry only `method`, `path`, `status`, `durationMs` into the JSON line | An allow-list is safer than a block-list for keeping note text and amounts out of logs |
| 2026-10-21 | Request completion line logs method and path only | Query strings and bodies can carry note text or amounts, which must never reach logs |

## Open questions

- Alert delivery: like the reference, no pager or email integration is planned for a personal tool;
  alerts will be visible in Alertmanager's own page. Say if you want one.
