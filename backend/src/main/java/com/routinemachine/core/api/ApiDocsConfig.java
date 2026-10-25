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
//  - one shared "default" error response on every operation, which lists what GlobalExceptionHandler can answer.
//    That list is the shared set for the whole API, not a promise about each route: a route that takes no input will
//    never answer 400. The routes whose own 401 differs (login, github-login, measurements) say so on the method.
@Configuration
public class ApiDocsConfig {

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    static final String SHARED_ERROR = "Any other error, with a body of {\"error\": \"text\"} (or, for an invalid field, "
            + "a map of field name to message). 400: a field is invalid or a precondition is not met. 403: not an "
            + "organisation admin or a member of the cohort. 404: not found, which is also what another person's item "
            + "looks like. A given route answers only some of these.";

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
