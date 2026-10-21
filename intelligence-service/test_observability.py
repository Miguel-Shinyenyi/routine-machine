import json
import logging

import httpx
import respx
from fastapi.testclient import TestClient
from prometheus_client import generate_latest

from main import app, BACKEND_BASE_URL
from observability import JsonFormatter, request_id_var

client = TestClient(app)


# --- Observability, part 2 (docs/observability.md): the same request id, JSON logs and metrics as the
# backend. The id comes in as X-Request-Id (or is made here), goes back out on the response, rides
# every log line, and is sent on to the backend so one request can be followed across both services.

def test_every_response_carries_a_request_id():
    response = client.get("/health")

    assert response.headers["x-request-id"]


def test_an_incoming_request_id_is_reused():
    response = client.get("/health", headers={"X-Request-Id": "from-the-frontend-42"})

    assert response.headers["x-request-id"] == "from-the-frontend-42"


def test_an_incoming_id_that_could_forge_a_log_line_is_replaced():
    for hostile in ("id with spaces", "<script>", "x" * 65, "a;b"):
        response = client.get("/health", headers={"X-Request-Id": hostile})

        sent = response.headers["x-request-id"]
        assert sent != hostile
        assert 1 <= len(sent) <= 64 and all(c.isalnum() or c in "._-" for c in sent)


@respx.mock
def test_the_request_id_is_sent_on_to_the_backend():
    routes = [
        respx.get(f"{BACKEND_BASE_URL}{path}").mock(return_value=httpx.Response(200, json=[]))
        for path in ("/api/routine-items", "/api/daily-logs")
    ]

    client.get("/patterns", headers={"X-Request-Id": "trace-me-7", "Authorization": "Bearer t"})

    for route in routes:
        assert route.calls[0].request.headers["x-request-id"] == "trace-me-7"


def test_the_json_formatter_writes_one_object_with_the_request_id_and_only_known_fields():
    record = logging.LogRecord("some.logger", logging.INFO, __file__, 1, "hello %s", ("world",), None)
    record.method = "GET"
    record.secret = "must-not-appear"
    token = request_id_var.set("fmt-1")
    try:
        line = JsonFormatter().format(record)
    finally:
        request_id_var.reset(token)

    data = json.loads(line)
    assert data["message"] == "hello world"
    assert data["level"] == "INFO"
    assert data["requestId"] == "fmt-1"
    assert data["method"] == "GET"
    assert "secret" not in data and "must-not-appear" not in line


def test_one_line_is_logged_per_request_without_the_query_string(caplog):
    with caplog.at_level(logging.INFO, logger="request"):
        client.get("/health?note=secret-words&amount=5000", headers={"X-Request-Id": "log-check-1"})

    lines = [r for r in caplog.records if r.name == "request"]
    assert len(lines) == 1
    record = lines[0]
    assert record.getMessage() == "request completed"
    assert (record.method, record.path, record.status) == ("GET", "/health", 200)
    assert record.durationMs >= 0
    assert record.requestId == "log-check-1"
    assert "secret" not in str(record.__dict__) and "5000" not in str(record.__dict__)


def test_the_metrics_port_text_counts_requests_by_route_template_and_status():
    client.get("/health")

    text = generate_latest().decode()

    assert 'http_requests_total{method="GET",route="/health",status="200"}' in text
    assert "http_request_duration_seconds_bucket" in text


def test_an_unknown_path_is_one_label_not_a_new_series_per_path():
    client.get("/no/such/path/1")
    client.get("/no/such/path/2")

    text = generate_latest().decode()

    assert 'route="unmatched"' in text
    assert "/no/such/path" not in text


def test_metrics_are_not_served_on_the_api_port():
    assert client.get("/metrics").status_code == 404


def test_uvicorns_access_log_stays_off_because_it_prints_query_strings():
    # --no-access-log works only while the access logger has no handler anywhere up the chain.
    assert not logging.getLogger("uvicorn.access").hasHandlers()


def test_httpx_does_not_log_the_urls_it_calls():
    assert logging.getLogger("httpx").getEffectiveLevel() >= logging.WARNING


# --- Observability, part 3: the reading-note assessment (the one paid model call) reports how often it
# runs and how it ends, how long it takes, and what it costs, never what the note said.

from prometheus_client import REGISTRY  # noqa: E402

from main import ANTHROPIC_BASE_URL  # noqa: E402

_NOTE = {"noteText": "A private thought about my mum and 5000 shillings.", "bookTitle": "Some Book"}
_GOOD = {
    "chapterPlaced": "Chapter 3", "ownWordsPercentage": 80, "ideasConnected": [], "quotedPhrases": [],
    "questionBack": "Why?",
}


def _sample(name, **labels):
    return REGISTRY.get_sample_value(name, labels) or 0.0


def _anthropic_ok(usage=None):
    body = {"content": [{"type": "text", "text": json.dumps(_GOOD)}]}
    if usage is not None:
        body["usage"] = usage
    respx.post(f"{ANTHROPIC_BASE_URL}/v1/messages").mock(return_value=httpx.Response(200, json=body))


@respx.mock
def test_a_successful_assessment_is_counted_timed_and_its_tokens_recorded(monkeypatch):
    monkeypatch.setenv("ANTHROPIC_API_KEY", "test-key")
    _anthropic_ok(usage={"input_tokens": 1200, "output_tokens": 300})
    before = (
        _sample("assessment_requests_total", outcome="success"),
        _sample("assessment_tokens_total", direction="input"),
        _sample("assessment_tokens_total", direction="output"),
        _sample("assessment_duration_seconds_count"),
    )

    assert client.post("/reading-notes/assess", json=_NOTE).status_code == 200

    assert _sample("assessment_requests_total", outcome="success") == before[0] + 1
    assert _sample("assessment_tokens_total", direction="input") == before[1] + 1200
    assert _sample("assessment_tokens_total", direction="output") == before[2] + 300
    assert _sample("assessment_duration_seconds_count") == before[3] + 1


@respx.mock
def test_estimated_cost_uses_prices_you_configure_and_is_zero_until_you_do(monkeypatch):
    monkeypatch.setenv("ANTHROPIC_API_KEY", "test-key")
    _anthropic_ok(usage={"input_tokens": 1_000_000, "output_tokens": 500_000})
    monkeypatch.delenv("ANTHROPIC_INPUT_USD_PER_MTOK", raising=False)
    monkeypatch.delenv("ANTHROPIC_OUTPUT_USD_PER_MTOK", raising=False)
    before = _sample("assessment_estimated_cost_usd_total")
    client.post("/reading-notes/assess", json=_NOTE)
    assert _sample("assessment_estimated_cost_usd_total") == before

    monkeypatch.setenv("ANTHROPIC_INPUT_USD_PER_MTOK", "2")
    monkeypatch.setenv("ANTHROPIC_OUTPUT_USD_PER_MTOK", "10")
    client.post("/reading-notes/assess", json=_NOTE)
    assert _sample("assessment_estimated_cost_usd_total") == before + 2 * 1 + 10 * 0.5


def test_a_missing_key_is_counted_as_unavailable(monkeypatch):
    monkeypatch.delenv("ANTHROPIC_API_KEY", raising=False)
    before = _sample("assessment_requests_total", outcome="unavailable")

    assert client.post("/reading-notes/assess", json=_NOTE).status_code == 503

    assert _sample("assessment_requests_total", outcome="unavailable") == before + 1


@respx.mock
def test_an_upstream_error_and_an_unparseable_answer_are_counted_as_failures(monkeypatch):
    monkeypatch.setenv("ANTHROPIC_API_KEY", "test-key")
    upstream_before = _sample("assessment_requests_total", outcome="upstream_error")
    parse_before = _sample("assessment_requests_total", outcome="unparseable")

    respx.post(f"{ANTHROPIC_BASE_URL}/v1/messages").mock(return_value=httpx.Response(500, json={}))
    assert client.post("/reading-notes/assess", json=_NOTE).status_code == 502
    respx.post(f"{ANTHROPIC_BASE_URL}/v1/messages").mock(return_value=httpx.Response(
        200, json={"content": [{"type": "text", "text": "I cannot do that."}]}))
    assert client.post("/reading-notes/assess", json=_NOTE).status_code == 502

    assert _sample("assessment_requests_total", outcome="upstream_error") == upstream_before + 1
    assert _sample("assessment_requests_total", outcome="unparseable") == parse_before + 1


@respx.mock
def test_no_metric_or_log_line_carries_the_note_text(monkeypatch, caplog):
    monkeypatch.setenv("ANTHROPIC_API_KEY", "test-key")
    _anthropic_ok(usage={"input_tokens": 10, "output_tokens": 5})

    with caplog.at_level(logging.INFO):
        client.post("/reading-notes/assess", json=_NOTE)

    exposed = generate_latest().decode() + " ".join(str(r.__dict__) + r.getMessage() for r in caplog.records)
    assert "mum" not in exposed and "5000 shillings" not in exposed and "Some Book" not in exposed
