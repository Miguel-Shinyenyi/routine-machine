package com.routinemachine.core.api;

import com.routinemachine.core.security.SecurityConfig;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MapSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;
import java.util.Map;

// The error codes and the title of the API documentation (staged Prompt 4). The documentation is visible to the owner
// only (OwnerOnlyDocsFilter). Two kinds of error are documented:
//  - 401 and the owner-only 404, exactly where the security chains produce them, read from the same patterns
//    SecurityConfig uses, so a route cannot be protected and documented as open or the other way round;
//  - 400, 403 and 404 per route, written on each controller method from reading what that route can throw (and from what
//    it takes as input: a body, a query value, a path value or an upload can each be missing or unreadable, which Spring
//    answers with 400). ApiDocsErrorsTest holds the structural part of it to the code. Routes that cannot fail those ways
//    claim nothing. The routes whose own 401 differs (login, github-login, measurements) say so on the method;
//  - one shared "default" response on every operation for anything not listed (a deleted account behind a valid token,
//    a server failure).
@Configuration
public class ApiDocsConfig {

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    static final String SHARED_ERROR = "Any error not listed above, with a body of {\"error\": \"text\"}. For example the "
            + "account behind a valid token no longer exists (400 or 404), or the server fails (500).";

    @Bean
    public OpenAPI routineMachineApi() {
        // One relative server, so springdoc does not fill "servers" from the address the request came in on: behind the
        // frontend that is the internal host name, which the documentation page would then show.
        return new OpenAPI().servers(List.of(new Server().url("/"))).info(new Info()
                .title("Routine Machine API")
                .version("1")
                .description("The backend's HTTP API. Visible to the owner only. Routes under /api/users, /api/granary, "
                        + "/api/goals, /api/routine-items, /api/learning-topics, /api/daily-logs, /api/tasks, "
                        + "/api/schedule-templates, /api/books, /api/reading-notes, /api/current-reading, "
                        + "/api/watering-holes, /api/evidence-events and /api/organisations need a valid bearer token "
                        + "(401 otherwise). /api/hillside and /api/watchtower take an optional one. Every operation lists "
                        + "a shared error response."));
    }

    @Bean
    public OperationCustomizer errorResponses() {
        return (operation, handlerMethod) -> {
            RequestMapping base = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), RequestMapping.class);
            String path = base == null || base.value().length == 0 ? "" : base.value()[0];
            if (matches(path, SecurityConfig.TOKEN_REQUIRED_PATTERNS)) {
                operation.getResponses().addApiResponse("401", new ApiResponse()
                        .description("The bearer token is missing, expired or not valid."));
            } else if (matches(path, SecurityConfig.OPTIONAL_TOKEN_PATTERNS)) {
                operation.getResponses().addApiResponse("401", new ApiResponse()
                        .description("A token was sent but is malformed or expired. Sending no token is allowed."));
            }
            if (path.startsWith("/api/watchtower")) {
                operation.getResponses().addApiResponse("404", new ApiResponse()
                        .description("You are not the owner. Everyone else, signed in or not, gets a plain 404."));
            }
            operation.getResponses().addApiResponse("default", new ApiResponse()
                    .description(SHARED_ERROR)
                    .content(new Content().addMediaType("application/json", new MediaType()
                            .schema(new MapSchema().additionalProperties(new StringSchema()))
                            .example(Map.of("error", "text")))));
            return operation;
        };
    }

    private static boolean matches(String path, String[] patterns) {
        for (String pattern : patterns) {
            if (MATCHER.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }
}
