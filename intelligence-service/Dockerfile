FROM python:3.13-slim
RUN addgroup --system app && adduser --system --ingroup app app
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY main.py observability.py suggestion.py patterns.py tally.py assess_note.py analogy.py ./
USER app
# Request metrics are served on their own port (never published to the host by docker-compose), and
# uvicorn's access log is off because it prints query strings; observability.py writes the one request
# line instead (docs/observability.md).
ENV METRICS_PORT=9000
EXPOSE 8000
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000", "--no-access-log"]
