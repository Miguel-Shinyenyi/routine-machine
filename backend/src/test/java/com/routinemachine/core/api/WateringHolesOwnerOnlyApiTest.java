package com.routinemachine.core.api;

import com.routinemachine.core.AbstractIntegrationTest;
import com.routinemachine.core.domain.Measurement;
import com.routinemachine.core.domain.MeasurementSource;
import com.routinemachine.core.domain.User;
import com.routinemachine.core.repository.MeasurementRepository;
import com.routinemachine.core.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Per-user scoping, part 5: the watering holes are the deployment's own integrations, so they
// are connected for the owner account and for nobody else.
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "owner.email=wh-owner@example.com", "hub-sync.enabled=true", "wakatime-sync.enabled=true",
        "activitywatch-sync.enabled=false", "github-sync.enabled=false", "rescuetime-sync.enabled=false"})
class WateringHolesOwnerOnlyApiTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private MeasurementRepository measurementRepository;

    private long saveUser(String email) {
        return userRepository.save(new User(email, "irrelevant-hash", null, null)).getId();
    }

    @Test
    void theOwnerSeesTheRealStatusOfEverySource() throws Exception {
        long owner = saveUser("wh-owner@example.com");
        measurementRepository.save(new Measurement(
                MeasurementSource.WAKATIME, "coding_minutes", LocalDate.of(2026, 10, 17), new BigDecimal("30")));

        mockMvc.perform(get("/api/watering-holes").param("userId", String.valueOf(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.source=='HUB')].enabled").value(true))
                .andExpect(jsonPath("$[?(@.source=='WAKATIME')].enabled").value(true))
                .andExpect(jsonPath("$[?(@.source=='WAKATIME')].lastActivityAt").isNotEmpty());
    }

    @Test
    void anyoneElseSeesEverySourceAsNotConnectedWithNoActivity() throws Exception {
        long someoneElse = saveUser("wh-other@example.com");

        mockMvc.perform(get("/api/watering-holes").param("userId", String.valueOf(someoneElse)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(6)))
                .andExpect(jsonPath("$[*].enabled", everyItem(is(false))))
                .andExpect(jsonPath("$[*].lastActivityAt", everyItem(nullValue())))
                .andExpect(jsonPath("$[*].source", hasItem("WAKATIME")));
    }

    @Test
    void withNoUserIdTheLegacyViewStillShowsTheRealStatus() throws Exception {
        mockMvc.perform(get("/api/watering-holes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.source=='WAKATIME')].enabled").value(true));
    }
}
