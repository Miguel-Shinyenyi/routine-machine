package com.routinemachine.core.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

// The owner-only operations page's recent logs (docs/observability.md, part 5, piece 3). The backend reads Loki.
// The only text from a request that reaches the LogQL query is a level taken from a fixed list and an id of plain
// characters (the same pattern the services use for a request id), placed as a plain-text match, never a regex.
// The services read are fixed. Each entry is reduced to named fields, so nothing else in a log line is passed on.
// Loki's address is configuration (watchtower.loki-base-url). If Loki is not running the answer is "available: false".
@Service
public class WatchtowerLogsService {

    public record Entry(String time, String service, String level, String message, String requestId, String traceId,
                        String traceUrl) {
    }

    public record Logs(boolean available, String range, List<Entry> entries) {
    }

    public static final String DEFAULT_RANGE = "1h";
    public static final int MAX_ENTRIES = 100;
    // The levels the services write (Spring's WARN, Python's WARNING and CRITICAL): the Grafana dashboard's list.
    public static final List<String> LEVELS = List.of("INFO", "WARN", "WARNING", "ERROR", "CRITICAL", "DEBUG");

    private static final int MAX_MESSAGE = 1000;
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final Pattern TRACE_ID = Pattern.compile("[0-9a-f]{32}");
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final String lokiBaseUrl;
    private final String jaegerUiUrl;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER).build();

    public WatchtowerLogsService(@Value("${watchtower.loki-base-url:}") String lokiBaseUrl,
                                 @Value("${watchtower.jaeger-ui-url:}") String jaegerUiUrl) {
        this.lokiBaseUrl = trimmed(lokiBaseUrl);
        this.jaegerUiUrl = trimmed(jaegerUiUrl);
    }

    private static String trimmed(String url) {
        return url == null ? "" : url.trim().replaceAll("/+$", "");
    }

    public Logs logs(String level, String requestId, String rangeLabel) {
        WatchtowerRange range = WatchtowerRange.of(rangeLabel);
        String query = query(level, requestId);
        if (lokiBaseUrl.isEmpty()) {
            return new Logs(false, range.label, List.of());
        }
        long end = Instant.now().getEpochSecond();
        long start = end - range.seconds;
        try {
            URI uri = URI.create(lokiBaseUrl + "/loki/api/v1/query_range?query=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                    + "&start=" + start + "000000000&end=" + end + "000000000&limit=" + MAX_ENTRIES + "&direction=backward");
            HttpResponse<String> response = client.send(
                    HttpRequest.newBuilder(uri).timeout(TIMEOUT).GET().build(), HttpResponse.BodyHandlers.ofString());
            JsonNode body = mapper.readTree(response.body());
            if (response.statusCode() != 200 || !"success".equals(body.path("status").asText())) {
                return new Logs(false, range.label, List.of());
            }
            return new Logs(true, range.label, entries(body.path("data").path("result")));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Logs(false, range.label, List.of());
        } catch (Exception e) {
            return new Logs(false, range.label, List.of());
        }
    }

    // Built only from the fixed service list and the two values checked here. A value that is not on the list, or has
    // any character outside the plain set, is refused, so nothing a caller sends can change the shape of the query.
    static String query(String level, String requestId) {
        if (level != null && !LEVELS.contains(level)) {
            throw new IllegalArgumentException("level must be one of " + String.join(", ", LEVELS));
        }
        if (requestId != null && !SAFE_ID.matcher(requestId).matches()) {
            throw new IllegalArgumentException("requestId may hold only letters, digits, dot, underscore and hyphen (1 to 64)");
        }
        return "{service=~\"backend|intelligence-service\""
                + (level == null ? "" : ",level=\"" + level + "\"")
                + "}"
                + (requestId == null ? "" : " |= \"" + requestId + "\"");
    }

    private record Raw(long nanos, String service, String streamLevel, String line) {
    }

    // Loki returns one stream per label set; the page wants one list, newest first.
    private List<Entry> entries(JsonNode streams) {
        List<Raw> raw = new ArrayList<>();
        for (JsonNode stream : streams) {
            String service = stream.path("stream").path("service").asText(null);
            String streamLevel = stream.path("stream").path("level").asText(null);
            for (JsonNode value : stream.path("values")) {
                raw.add(new Raw(Long.parseLong(value.get(0).asText()), service, streamLevel, value.get(1).asText()));
            }
        }
        raw.sort(Comparator.comparingLong(Raw::nanos).reversed());
        return raw.stream().limit(MAX_ENTRIES).map(this::entry).toList();
    }

    private Entry entry(Raw raw) {
        String time = Instant.ofEpochSecond(0, raw.nanos()).toString();
        JsonNode json = parse(raw.line());
        if (json == null) {
            return new Entry(time, raw.service(), raw.streamLevel(), cut(raw.line()), null, null, null);
        }
        String level = json.path("level").asText(raw.streamLevel());
        String requestId = json.path("requestId").asText("");
        String traceId = json.path("trace_id").asText(json.path("traceId").asText(""));
        traceId = TRACE_ID.matcher(traceId).matches() ? traceId : null;
        return new Entry(time, raw.service(), level, cut(json.path("message").asText("")),
                SAFE_ID.matcher(requestId).matches() ? requestId : null, traceId,
                traceId == null || jaegerUiUrl.isEmpty() ? null : jaegerUiUrl + "/trace/" + traceId);
    }

    private JsonNode parse(String line) {
        try {
            JsonNode node = mapper.readTree(line);
            return node != null && node.isObject() ? node : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String cut(String text) {
        return text.length() <= MAX_MESSAGE ? text : text.substring(0, MAX_MESSAGE) + "…";
    }
}
