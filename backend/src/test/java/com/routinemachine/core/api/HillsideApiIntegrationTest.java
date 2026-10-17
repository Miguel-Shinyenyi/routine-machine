package com.routinemachine.core.api;

import com.routinemachine.core.AbstractIntegrationTest;
import com.routinemachine.core.repository.DailyLogRepository;
import com.routinemachine.core.repository.RoutineItemRepository;
import com.routinemachine.core.repository.UserRepository;
import com.routinemachine.core.domain.DailyLog;
import com.routinemachine.core.domain.HillsideField;
import com.routinemachine.core.domain.RoutineItem;
import com.routinemachine.core.domain.RoutineItemSource;
import com.routinemachine.core.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class HillsideApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoutineItemRepository routineItemRepository;

    @Autowired
    private DailyLogRepository dailyLogRepository;

    @Test
    void returnsYourBandWhenAsUserIdIsGiven() throws Exception {
        User user = userRepository.save(new User("hillside-api@example.com", "hash", null, null));
        RoutineItem item = routineItemRepository.save(new RoutineItem(
                "Reading for hillside api test", null, null, null, RoutineItemSource.MANUAL,
                HillsideField.READING, false, null));
        DailyLog log = DailyLog.forRoutineItem(LocalDate.now(), item);
        log.assignUser(user);
        dailyLogRepository.save(log);

        mockMvc.perform(get("/api/hillside")
                        .param("field", "READING")
                        .param("windowDays", "7")
                        .param("asUserId", user.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.yourDaysShownUp", is(1)))
                .andExpect(jsonPath("$.yourRank", is(1)))
                .andExpect(jsonPath("$.band").exists());
    }

    @Test
    void withNoFieldReturnsOkUsingTheGeneralThreshold() throws Exception {
        mockMvc.perform(get("/api/hillside").param("windowDays", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.yourDaysShownUp").value(nullValue()));
    }
}
