package com.routinemachine.core.api;

import com.routinemachine.core.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

// Access control, part 5: the sweep. Every route the application serves must refuse a request with
// no token (401), except the ones listed here with the reason each is open. A route added later
// without a token, or left out of the protected filter chain, fails this test.
@SpringBootTest
@AutoConfigureMockMvc
class EveryRouteNeedsATokenTest extends AbstractIntegrationTest {

    // Path prefix -> why it is allowed to answer without a token.
    private static final Map<String, String> OPEN_ON_PURPOSE = Map.of(
            "/api/auth/", "sign-in and sign-up; github-login checks its own internal token",
            "/api/measurements", "health data push, protected by its own bearer token",
            "/api/hillside", "the public ranking; a valid token only adds 'you' to it (see HillsideTokenApiIntegrationTest)");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    private static boolean isOpen(String path) {
        return OPEN_ON_PURPOSE.keySet().stream().anyMatch(path::startsWith);
    }

    @Test
    void everyRouteRefusesARequestWithNoTokenUnlessItIsOpenOnPurpose() throws Exception {
        List<String> failures = new ArrayList<>();
        int checked = 0;
        for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
            Set<String> patterns = info.getPathPatternsCondition() == null
                    ? Set.of() : info.getPathPatternsCondition().getPatternValues();
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            for (String pattern : patterns) {
                if (!pattern.startsWith("/api/") || isOpen(pattern)) {
                    continue;
                }
                String path = pattern.replaceAll("\\{[^}]+}", "1");
                for (RequestMethod method : methods.isEmpty() ? Set.of(RequestMethod.GET) : methods) {
                    int status = mockMvc.perform(request(HttpMethod.valueOf(method.name()), path)
                                    .contentType(MediaType.APPLICATION_JSON).content("{}"))
                            .andReturn().getResponse().getStatus();
                    checked++;
                    if (status != 401) {
                        failures.add(method + " " + pattern + " answered " + status + " with no token");
                    }
                }
            }
        }
        assertThat(checked).as("routes checked").isGreaterThan(40);
        assertThat(failures).as("routes open without a token").isEmpty();
    }

    @Test
    void everyOpenRouteListedHereStillExists() {
        for (String prefix : OPEN_ON_PURPOSE.keySet()) {
            boolean exists = handlerMapping.getHandlerMethods().keySet().stream()
                    .flatMap(info -> info.getPathPatternsCondition().getPatternValues().stream())
                    .anyMatch(pattern -> pattern.startsWith(prefix));
            assertThat(exists).as("an open-route entry with no route behind it: " + prefix).isTrue();
        }
    }
}
