package com.routinemachine.core.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

// Staged Prompt 4: the intelligence service's API document, for the owner. The service does not serve it on its open port;
// it serves it on an internal docs port that is never published to the host (docs/intelligence-service.md). This reads it
// from there. The address is configuration (watchtower.intelligence-docs-url), never request input. The caller has already
// been checked as the owner (WatchtowerController). Anything that goes wrong is a plain 503 or 502, not a hang or a trace.
@Service
public class WatchtowerIntelligenceDocsService {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    // The spec is a few kilobytes; a megabyte is a bug somewhere else, not a document.
    private static final int MAX_CHARACTERS = 1_000_000;

    private final String url;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER).build();

    public WatchtowerIntelligenceDocsService(@Value("${watchtower.intelligence-docs-url:}") String url) {
        this.url = url == null ? "" : url.trim();
    }

    public JsonNode spec() {
        if (url.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "the intelligence service's documentation address is not configured");
        }
        HttpResponse<String> response;
        try {
            response = client.send(HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "the intelligence service did not answer");
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "the intelligence service did not answer");
        }
        if (response.statusCode() != 200) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "the intelligence service did not answer");
        }
        if (response.body().length() > MAX_CHARACTERS) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "the intelligence service's document is too large");
        }
        try {
            JsonNode spec = mapper.readTree(response.body());
            if (spec == null || !spec.isObject() || !spec.has("openapi")) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "the intelligence service's document is not an OpenAPI document");
            }
            return spec;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "the intelligence service's document is not valid JSON");
        }
    }
}
