FROM python:3.13-slim
RUN addgroup --system app && adduser --system --ingroup app app
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY entrypoint.sh ./
COPY main.py observability.py api_docs.py suggestion.py patterns.py tally.py assess_note.py analogy.py user_today.py ./
USER app
# Request metrics are served on their own port (never published to the host by docker-compose), and
# uvicorn's access log is off because it prints query strings; observability.py writes the one request
# line instead (docs/observability.md).
ENV METRICS_PORT=9000
# The API spec is served on its own port too (api_docs.py), never published to the host.
ENV DOCS_PORT=9001
EXPOSE 8000
CMD ["sh", "entrypoint.sh"]
