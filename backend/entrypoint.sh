#!/bin/sh
# Tracing is off unless TRACING_ENABLED=true, so a plain `docker compose up` loads no agent and exports
# nothing. With it on, the OpenTelemetry agent traces HTTP, JDBC and outbound calls (docs/observability.md).
if [ "$TRACING_ENABLED" = "true" ]; then
  export JAVA_TOOL_OPTIONS="-javaagent:/app/opentelemetry-javaagent.jar ${JAVA_TOOL_OPTIONS}"
fi
exec java -jar /app/app.jar
