import json
import urllib.error
import urllib.request

from fastapi.routing import APIRoute
from fastapi.testclient import TestClient

from main import app

client = TestClient(app)

# Staged Prompt 4: the intelligence service's API documentation is NOT on its open port (8000). The spec is served on a
# separate internal port that docker-compose never publishes, and the backend hands it to the owner (docs/intelligence-service.md).


def test_the_open_port_serves_no_documentation():
    for path in ["/docs", "/redoc", "/openapi.json", "/docs/oauth2-redirect"]:
        assert client.get(path).status_code == 404, path


def test_every_route_has_a_summary_written_for_the_documentation():
    routes = [r for r in app.routes if isinstance(r, APIRoute)]
    assert len(routes) >= 6
    # route.summary is None unless it was set on purpose; FastAPI's own default is just the function name.
    assert [r.path for r in routes if not r.summary] == []


def _get(url):
    try:
        with urllib.request.urlopen(url, timeout=5) as response:
            return response.status, response.read()
    except urllib.error.HTTPError as error:
        return error.code, error.read()


def test_the_docs_server_serves_the_spec_with_every_route_and_nothing_else():
    from api_docs import start_docs_server

    server = start_docs_server(app, 0, host="127.0.0.1")
    try:
        base = f"http://127.0.0.1:{server.server_address[1]}"
        status, body = _get(base + "/openapi.json")
        assert status == 200
        paths = json.loads(body)["paths"]
        for route in ["/health", "/suggestion", "/patterns", "/tally", "/reading-notes/assess", "/analogy"]:
            assert route in paths, route
        assert _get(base + "/docs")[0] == 404
        assert _get(base + "/anything-else")[0] == 404
        request = urllib.request.Request(base + "/openapi.json", data=b"x", method="POST")
        try:
            urllib.request.urlopen(request, timeout=5)
            raise AssertionError("a POST should be refused")
        except urllib.error.HTTPError as error:
            assert error.code in (404, 405, 501)
    finally:
        server.shutdown()
        server.server_close()


def test_a_port_of_zero_or_unset_starts_no_server():
    from api_docs import start_docs_server_from_env

    assert start_docs_server_from_env(app, {}) is None
    assert start_docs_server_from_env(app, {"DOCS_PORT": "0"}) is None
    assert start_docs_server_from_env(app, {"DOCS_PORT": ""}) is None
