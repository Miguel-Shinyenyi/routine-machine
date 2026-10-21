package com.routinemachine.core.rescuetime;

import com.routinemachine.core.AbstractIntegrationTest;
import com.routinemachine.core.domain.DailyLogSource;
import com.routinemachine.core.domain.Measurement;
import com.routinemachine.core.domain.MeasurementSource;
import com.routinemachine.core.domain.RoutineItem;
import com.routinemachine.core.domain.RoutineItemSource;
import com.routinemachine.core.repository.DailyLogRepository;
import com.routinemachine.core.repository.MeasurementRepository;
import com.routinemachine.core.repository.RoutineItemRepository;
import com.routinemachine.core.repository.UserRepository;
import com.routinemachine.core.service.OwnerAccount;
import com.routinemachine.core.service.MeasurementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class RescueTimeSyncServiceTest extends AbstractIntegrationTest {

    @Autowired
    private RoutineItemRepository routineItemRepository;

    @Autowired
    private DailyLogRepository dailyLogRepository;

    @Autowired
    private MeasurementRepository measurementRepository;
    @Autowired
    private UserRepository userRepository;

    private RescueTimeSyncService service(RescueTimeClient client) {
        return new RescueTimeSyncService(
                client, routineItemRepository, dailyLogRepository, new MeasurementService(measurementRepository), new OwnerAccount(userRepository, ""), true);
    }

    private static class FakeRescueTimeClient implements RescueTimeClient {
        private final List<RescueTimeDailyTotal> totals;

        FakeRescueTimeClient(List<RescueTimeDailyTotal> totals) {
            this.totals = totals;
        }

        @Override
        public List<RescueTimeDailyTotal> fetchDailyActiveTotals(LocalDate start, LocalDate end) {
            return totals;
        }
    }

    @Test
    void aDaysActiveTotalIsRecordedAsAMeasurementInMinutes() {
        LocalDate date = LocalDate.of(2026, 10, 17);
        service(new FakeRescueTimeClient(List.of(new RescueTimeDailyTotal(date, 5400)))).syncOnce();

        List<Measurement> all = measurementRepository.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getSource()).isEqualTo(MeasurementSource.RESCUETIME);
        assertThat(all.get(0).getMetric()).isEqualTo("active_minutes");
        assertThat(all.get(0).getMeasurementDate()).isEqualTo(date);
        assertThat(all.get(0).getValue()).isEqualByComparingTo("90");
    }

    @Test
    void syncingTheSameDayTwiceUpdatesTheMeasurementInsteadOfDuplicatingIt() {
        LocalDate date = LocalDate.of(2026, 10, 17);
        service(new FakeRescueTimeClient(List.of(new RescueTimeDailyTotal(date, 1800)))).syncOnce();
        service(new FakeRescueTimeClient(List.of(new RescueTimeDailyTotal(date, 5400)))).syncOnce();

        List<Measurement> all = measurementRepository.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getValue()).isEqualByComparingTo("90");
    }

    @Test
    void aRoutineItemSourcedFromRescuetimeGetsADailyLogForTheSummarisedDay() {
        RoutineItem item = routineItemRepository.save(
                new RoutineItem("Focused work", null, null, null, RoutineItemSource.RESCUETIME, null, true, null));
        LocalDate date = LocalDate.of(2026, 10, 17);

        service(new FakeRescueTimeClient(List.of(new RescueTimeDailyTotal(date, 5400)))).syncOnce();

        assertThat(dailyLogRepository.existsByRoutineItemIdAndLogDate(item.getId(), date)).isTrue();
        assertThat(dailyLogRepository.findByLogDate(date)).hasSize(1);
        assertThat(dailyLogRepository.findByLogDate(date).get(0).getSource())
                .isEqualTo(DailyLogSource.RESCUETIME_SYNC);
        assertThat(dailyLogRepository.findByLogDate(date).get(0).getMeasuredMinutes()).isEqualTo(90);
    }

    @Test
    void syncingTheSameDayTwiceDoesNotCreateASecondDailyLogForTheRoutineItem() {
        routineItemRepository.save(
                new RoutineItem("Focused work", null, null, null, RoutineItemSource.RESCUETIME, null, true, null));
        LocalDate date = LocalDate.of(2026, 10, 17);

        service(new FakeRescueTimeClient(List.of(new RescueTimeDailyTotal(date, 1800)))).syncOnce();
        service(new FakeRescueTimeClient(List.of(new RescueTimeDailyTotal(date, 5400)))).syncOnce();

        assertThat(dailyLogRepository.findByLogDate(date)).hasSize(1);
    }

    @Test
    void noRoutineItemSourcedFromRescuetimeStillRecordsTheMeasurementButLogsNothing() {
        LocalDate date = LocalDate.of(2026, 10, 17);
        service(new FakeRescueTimeClient(List.of(new RescueTimeDailyTotal(date, 5400)))).syncOnce();

        assertThat(measurementRepository.findAll()).hasSize(1);
        assertThat(dailyLogRepository.findByLogDate(date)).isEmpty();
    }

    @Test
    void aDayWithZeroActiveSecondsIsSkipped() {
        LocalDate date = LocalDate.of(2026, 10, 17);
        service(new FakeRescueTimeClient(List.of(new RescueTimeDailyTotal(date, 0)))).syncOnce();

        assertThat(measurementRepository.findAll()).isEmpty();
    }

    @Test
    void theFeatureFlagDefaultsToDisabledSoSyncOnceIsNeverCalledByTheScheduler() {
        RescueTimeSyncService defaultService = new RescueTimeSyncService(
                new FakeRescueTimeClient(List.of(new RescueTimeDailyTotal(LocalDate.of(2026, 10, 17), 5400))),
                routineItemRepository, dailyLogRepository, new MeasurementService(measurementRepository), new OwnerAccount(userRepository, ""));

        defaultService.scheduledSync();

        assertThat(measurementRepository.findAll()).isEmpty();
    }
}
