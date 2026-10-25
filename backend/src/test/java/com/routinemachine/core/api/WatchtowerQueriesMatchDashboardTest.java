package com.routinemachine.core.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.routinemachine.core.service.WatchtowerLogsService;
import com.routinemachine.core.service.WatchtowerMetricsService;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// The page's queries and the Grafana dashboard's are the same expressions written in two places. This fails if
// one changes without the other (docs/observability.md: point one at the other, do not let them drift).
class WatchtowerQueriesMatchDashboardTest {

    private static final Path DASHBOARD = Path.of("../infra/observability/grafana/dashboards/routine-machine.json");

    @Test
    void everyQueryOnTheOwnerPageIsAlsoOnTheGrafanaDashboard() throws Exception {
        assertThat(DASHBOARD).as("the dashboard file, relative to backend/").exists();
        Set<String> onDashboard = new HashSet<>();
        for (JsonNode panel : new ObjectMapper().readTree(Files.readString(DASHBOARD)).get("panels")) {
            for (JsonNode target : panel.path("targets")) {
                onDashboard.add(target.get("expr").asText());
            }
        }

        for (WatchtowerMetricsService.Query query : WatchtowerMetricsService.QUERIES) {
            assertThat(onDashboard).as("query %s", query.id()).contains(query.promql());
        }
    }

    @Test
    void theLevelsTheLogsPageAcceptsAreTheOnesTheGrafanaDashboardOffers() throws Exception {
        String offered = null;
        for (JsonNode variable : new ObjectMapper().readTree(Files.readString(DASHBOARD)).path("templating").path("list")) {
            if ("level".equals(variable.path("name").asText())) {
                offered = variable.path("query").asText();
            }
        }

        assertThat(offered).as("the dashboard's level variable").isNotNull();
        assertThat(WatchtowerLogsService.LEVELS).containsExactlyInAnyOrder(offered.split(","));
    }
}
