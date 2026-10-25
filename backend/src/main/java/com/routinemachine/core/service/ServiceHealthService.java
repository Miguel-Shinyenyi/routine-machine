package com.routinemachine.core.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

// What the owner-only operations page shows for "is it up" (docs/observability.md, part 5). The backend and
// its database report their own health; every other service is probed at an address set in the environment
// (WATCHTOWER_*_URL), never one a caller supplies, so a request cannot make the backend fetch anything else.
// A service with no address is NOT_CONFIGURED; one that does not answer 2xx in time is DOWN. The result holds
// names and statuses only: no address and no error text reach the page.
@Service
public class ServiceHealthService {

    public record ServiceStatus(String name, String status) {
    }

    private static final String UP = "UP";
    private static final String DOWN = "DOWN";
    private static final String NOT_CONFIGURED = "NOT_CONFIGURED";
    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    private final HealthEndpoint healthEndpoint;
    private final Map<String, String> probes = new LinkedHashMap<>();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER).build();

    public ServiceHealthService(
            HealthEndpoint healthEndpoint,
            @Value("${watchtower.intelligence-url:}") String intelligenceUrl,
            @Value("${watchtower.prometheus-url:}") String prometheusUrl,
            @Value("${watchtower.alertmanager-url:}") String alertmanagerUrl,
            @Value("${watchtower.loki-url:}") String lokiUrl,
            @Value("${watchtower.jaeger-url:}") String jaegerUrl,
            @Value("${watchtower.grafana-url:}") String grafanaUrl) {
        this.healthEndpoint = healthEndpoint;
        probes.put("intelligence-service", intelligenceUrl);
        probes.put("prometheus", prometheusUrl);
        probes.put("alertmanager", alertmanagerUrl);
        probes.put("loki", lokiUrl);
        probes.put("jaeger", jaegerUrl);
        probes.put("grafana", grafanaUrl);
    }

    public List<ServiceStatus> statuses() {
        List<ServiceStatus> result = new ArrayList<>();
        result.add(new ServiceStatus("backend", statusOf(healthEndpoint.health())));
        result.add(new ServiceStatus("database", statusOf(healthEndpoint.healthForPath("db"))));
        // Probed in parallel, so one slow service costs one timeout and not one per service.
        Map<String, CompletableFuture<String>> pending = new LinkedHashMap<>();
        probes.forEach((name, url) -> pending.put(name, CompletableFuture.supplyAsync(() -> probe(url))));
        pending.forEach((name, status) -> result.add(new ServiceStatus(name, status.join())));
        return result;
    }

    private static String statusOf(HealthComponent component) {
        return component != null && Status.UP.equals(component.getStatus()) ? UP : DOWN;
    }

    private String probe(String url) {
        if (url == null || url.isBlank()) {
            return NOT_CONFIGURED;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url.trim())).timeout(TIMEOUT).GET().build();
            int code = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            return code >= 200 && code < 300 ? UP : DOWN;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return DOWN;
        } catch (Exception e) {
            return DOWN;
        }
    }
}
