import json
import os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from threading import Thread

# Staged Prompt 4: the API documentation. FastAPI's own /docs, /redoc and /openapi.json are switched off on the open port
# (main.py), because that port is published to the host. The spec is served here instead, on a separate internal port
# (DOCS_PORT) that docker-compose never publishes, so only other containers on the Compose network can reach it. The backend
# reads it for the owner (docs/intelligence-service.md). Same idea as the metrics port in observability.py.


def start_docs_server(app, port: int, host: str = "0.0.0.0") -> ThreadingHTTPServer:
    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            if self.path != "/openapi.json":
                self.send_error(404)
                return
            body = json.dumps(app.openapi()).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        # The default writes every request line, query string included, to stderr; the service's own log has one line
        # per request on the API port and nothing is learned from this one.
        def log_message(self, format, *args):
            pass

    server = ThreadingHTTPServer((host, port), Handler)
    Thread(target=server.serve_forever, daemon=True).start()
    return server


def start_docs_server_from_env(app, env=None):
    # DOCS_PORT unset, empty or 0 means off (tests, local runs), the same convention as METRICS_PORT.
    env = os.environ if env is None else env
    port = int(env.get("DOCS_PORT", "0") or 0)
    return start_docs_server(app, port) if port else None
