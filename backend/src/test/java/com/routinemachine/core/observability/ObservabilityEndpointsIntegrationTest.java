package com.routinemachine.core.observability;

import com.routinemachine.core.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.test.context.TestPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

// Observability, part 1: metrics are served on their own port, never on the one the browser-facing
// API uses, and only the two endpoints that are needed are exposed.
// Spring Boot turns metric export off inside tests unless asked.
@AutoConfigureObservability
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "management.server.port=0")
class ObservabilityEndpointsIntegrationTest extends AbstractIntegrationTest {

    @Value("${local.server.port}")
    private int apiPort;
    @LocalManagementPort
    private int managementPort;

    private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    private HttpResponse<String> get(int port, String path, String... headers) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        for (int i = 0; i < headers.length; i += 2) {
            request.header(headers[i], headers[i + 1]);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void metricsAreServedOnTheManagementPortInPrometheusFormat() throws Exception {
        get(apiPort, "/api/books"); // one request, so the request-latency metric exists
        HttpResponse<String> response = get(managementPort, "/actuator/prometheus");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("jvm_memory_used_bytes").contains("http_server_requests");
    }

    @Test
    void metricsAreNotServedOnTheApiPort() throws Exception {
        HttpResponse<String> response = get(apiPort, "/actuator/prometheus");

        assertThat(response.statusCode()).isNotEqualTo(200);
        assertThat(response.body()).doesNotContain("jvm_memory_used_bytes");
    }

    @Test
    void healthIsOnTheManagementPortAndNothingElseIsExposed() throws Exception {
        assertThat(get(managementPort, "/actuator/health").statusCode()).isEqualTo(200);
        for (String path : new String[] {"/actuator/env", "/actuator/beans", "/actuator/heapdump", "/actuator/loggers"}) {
            assertThat(get(managementPort, path).statusCode()).as(path).isEqualTo(404);
        }
    }

    @Test
    void everyApiResponseCarriesARequestIdEvenWhenRefused() throws Exception {
        HttpResponse<String> refused = get(apiPort, "/api/books");

        assertThat(refused.statusCode()).isEqualTo(401);
        assertThat(refused.headers().firstValue("X-Request-Id")).isPresent();
    }

    @Test
    void anIncomingRequestIdIsEchoedBack() throws Exception {
        HttpResponse<String> response = get(apiPort, "/api/books", "X-Request-Id", "from-the-frontend-42");

        assertThat(response.headers().firstValue("X-Request-Id")).hasValue("from-the-frontend-42");
    }
}
