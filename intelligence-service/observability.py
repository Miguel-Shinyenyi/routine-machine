import json
import logging
import os
import re
import time
import uuid
from contextvars import ContextVar
from datetime import datetime, timezone

from prometheus_client import Counter, Histogram, start_http_server

# Observability, part 2 (docs/observability.md): a request id on every log line and on the response,
# one JSON log object per line, request metrics served on their own port. The same rules as the backend.

HEADER = "X-Request-Id"
_SAFE_ID = re.compile(r"[A-Za-z0-9._-]{1,64}")

request_id_var: ContextVar[str | None] = ContextVar("request_id", default=None)

REQUESTS = Counter("http_requests_total", "Requests served", ["method", "route", "status"])
DURATION = Histogram("http_request_duration_seconds", "Request duration in seconds", ["method", "route"])

# The reading-note assessment is the one paid model call that carries a person's words. These record how
# often it runs and how it ends, how long it takes and what it uses, never the note or the book.
ASSESSMENT_REQUESTS = Counter("assessment_requests_total", "Reading-note assessments by outcome", ["outcome"])
ASSESSMENT_DURATION = Histogram("assessment_duration_seconds", "Reading-note assessment duration in seconds")
ASSESSMENT_TOKENS = Counter("assessment_tokens_total", "Model tokens used by assessments", ["direction"])
ASSESSMENT_COST = Counter("assessment_estimated_cost_usd_total", "Estimated model cost of assessments in US dollars")

_request_log = logging.getLogger("request")

# The only extra fields a log record may carry into the JSON line. Anything else (a note, an amount, a
# name) is dropped, so a careless logger.info(..., extra={...}) cannot leak it.
_ALLOWED_FIELDS = ("method", "path", "status", "durationMs")


def record_assessment_usage(usage: dict | None) -> None:
    # Tokens come from the model's own usage report. Cost is tokens times prices YOU set in the
    # environment (USD per million tokens); with none set it stays 0 rather than guessing a price.
    if not usage:
        return
    input_tokens = int(usage.get("input_tokens") or 0)
    output_tokens = int(usage.get("output_tokens") or 0)
    ASSESSMENT_TOKENS.labels("input").inc(input_tokens)
    ASSESSMENT_TOKENS.labels("output").inc(output_tokens)
    input_price = float(os.environ.get("ANTHROPIC_INPUT_USD_PER_MTOK") or 0)
    output_price = float(os.environ.get("ANTHROPIC_OUTPUT_USD_PER_MTOK") or 0)
    ASSESSMENT_COST.inc(input_tokens / 1_000_000 * input_price + output_tokens / 1_000_000 * output_price)


def new_request_id(incoming: str | None) -> str:
    # An incoming id is reused only if short and plain; anything else could forge or break a log line.
    if incoming and _SAFE_ID.fullmatch(incoming):
        return incoming
    return str(uuid.uuid4())


def _current_trace_id() -> str | None:
    # Set only while a traced request is running (tracing is off unless TRACING_ENABLED=true).
    try:
        from opentelemetry import trace
    except ImportError:
        return None
    context = trace.get_current_span().get_span_context()
    return format(context.trace_id, "032x") if context.is_valid else None


class JsonFormatter(logging.Formatter):
    def format(self, record: logging.LogRecord) -> str:
        data = {
            "@timestamp": datetime.fromtimestamp(record.created, timezone.utc).isoformat(),
            "level": record.levelname,
            "logger": record.name,
            "message": record.getMessage(),
        }
        request_id = getattr(record, "requestId", None) or request_id_var.get()
        if request_id:
            data["requestId"] = request_id
        trace_id = _current_trace_id()
        if trace_id:
            data["traceId"] = trace_id
        for field in _ALLOWED_FIELDS:
            if hasattr(record, field):
                data[field] = getattr(record, field)
        if record.exc_info:
            data["exception"] = self.formatException(record.exc_info)
        return json.dumps(data)


def _install_record_factory() -> None:
    # Stamp the current request id on every record when it is created, so it stays on the record after
    # the request has ended (and for any handler, not just ours).
    previous = logging.getLogRecordFactory()

    def factory(*args, **kwargs):
        record = previous(*args, **kwargs)
        record.requestId = request_id_var.get()
        return record

    logging.setLogRecordFactory(factory)


def configure_logging() -> None:
    _install_record_factory()
    handler = logging.StreamHandler()
    handler.setFormatter(JsonFormatter())
    root = logging.getLogger()
    root.handlers = [handler]
    root.setLevel(logging.INFO)
    # uvicorn installs its own plain-text handlers; route its lines through ours instead. Its access log
    # (which prints the query string) is switched off with --no-access-log in the Dockerfile.
    for name in ("uvicorn", "uvicorn.error"):
        logger = logging.getLogger(name)
        logger.handlers = []
        logger.propagate = True
    # The access logger must have no handler anywhere up the chain, or uvicorn turns it back on even
    # with --no-access-log: so it neither keeps its own handlers nor propagates to the root.
    access = logging.getLogger("uvicorn.access")
    access.handlers = []
    access.propagate = False
    # httpx logs every call's full URL at INFO; the request line above already says what happened.
    logging.getLogger("httpx").setLevel(logging.WARNING)


def start_metrics_server() -> None:
    # Own port, never the API port. METRICS_PORT unset or 0 means off (tests, local runs).
    port = int(os.environ.get("METRICS_PORT", "0") or 0)
    if port:
        start_http_server(port)


class RequestObservabilityMiddleware:
    """Pure ASGI middleware: request id in, request id out, one log line, request metrics."""

    def __init__(self, app):
        self.app = app

    async def __call__(self, scope, receive, send):
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return

        incoming = next((v.decode("latin-1") for k, v in scope["headers"] if k == b"x-request-id"), None)
        request_id = new_request_id(incoming)
        token = request_id_var.set(request_id)
        started = time.perf_counter()
        status = {"code": 500}

        async def send_with_id(message):
            if message["type"] == "http.response.start":
                status["code"] = message["status"]
                message.setdefault("headers", []).append((HEADER.lower().encode(), request_id.encode()))
            await send(message)

        try:
            await self.app(scope, receive, send_with_id)
        finally:
            elapsed = time.perf_counter() - started
            route = scope.get("route")
            # The route template, never the raw path: one series per route, not one per id.
            label = getattr(route, "path", None) or "unmatched"
            REQUESTS.labels(scope["method"], label, str(status["code"])).inc()
            # Inside a trace the observation carries its trace id as an exemplar (shown by Prometheus and Grafana
            # only when scraped as OpenMetrics); outside one it is a plain observation.
            trace_id = _current_trace_id()
            DURATION.labels(scope["method"], label).observe(
                elapsed, exemplar={"trace_id": trace_id} if trace_id else None)
            _request_log.info(
                "request completed",
                extra={"method": scope["method"], "path": scope["path"], "status": status["code"],
                       "durationMs": int(elapsed * 1000)},
            )
            request_id_var.reset(token)
