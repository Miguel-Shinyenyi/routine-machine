#!/bin/sh
# Tracing is off unless TRACING_ENABLED=true. With it on, OpenTelemetry's auto-instrumentation wraps the
# server (FastAPI and outbound httpx calls) and the trace id appears on every log line.
if [ "$TRACING_ENABLED" = "true" ]; then
  exec opentelemetry-instrument uvicorn main:app --host 0.0.0.0 --port 8000 --no-access-log
fi
exec uvicorn main:app --host 0.0.0.0 --port 8000 --no-access-log
