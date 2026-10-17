package com.routinemachine.core.activitywatch;

import com.routinemachine.core.AbstractIntegrationTest;
import com.routinemachine.core.domain.DailyLogSource;
import com.routinemachine.core.domain.Measurement;
import com.routinemachine.core.domain.MeasurementSource;
import com.routinemachine.core.domain.RoutineItem;
import com.routinemachine.core.domain.RoutineItemSource;
import com.routinemachine.core.repository.DailyLogRepository;
import com.routinemachine.core.repository.MeasurementRepository;
import com.routinemachine.core.repository.RoutineItemRepository;
import com.routinemachine.core.service.MeasurementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ActivityWatchSyncServiceTest extends AbstractIntegrationTest {

    @Autowired
    private RoutineItemRepository routineItemRepository;

    @Autowired
    private DailyLogRepository dailyLogRepository;

    @Autowired
    private MeasurementRepository measurementRepository;

    private ActivityWatchSyncService service(ActivityWatchClient client) {
        return new ActivityWatchSyncService(
                client, routineItemRepository, dailyLogRepository, new MeasurementService(measurementRepository));
    }

    private static class FakeActivityWatchClient implements ActivityWatchClient {
        private final List<ActivityWatchDailyTotal> totals;

        FakeActivityWatchClient(List<ActivityWatchDailyTotal> totals) {
            this.totals = totals;
        }

        @Override
        public List<ActivityWatchDailyTotal> fetchDailyActiveTotals(LocalDate start, LocalDate end) {
            return totals;
        }
    }

    @Test
    void aDaysActiveTotalIsRecordedAsAMeasurementInMinutes() {
        LocalDate date = LocalDate.of(2026, 10, 17);
        service(new FakeActivityWatchClient(List.of(new ActivityWatchDailyTotal(date, 5400)))).syncOnce();

        List<Measurement> all = measurementRepository.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getSource()).isEqualTo(MeasurementSource.ACTIVITYWATCH);
        assertThat(all.get(0).getMetric()).isEqualTo("active_minutes");
        assertThat(all.get(0).getMeasurementDate()).isEqualTo(date);
        assertThat(all.get(0).getValue()).isEqualByComparingTo("90");
    }

    @Test
    void syncingTheSameDayTwiceUpdatesTheMeasurementInsteadOfDuplicatingIt() {
        LocalDate date = LocalDate.of(2026, 10, 17);
        service(new FakeActivityWatchClient(List.of(new ActivityWatchDailyTotal(date, 1800)))).syncOnce();
        service(new FakeActivityWatchClient(List.of(new ActivityWatchDailyTotal(date, 5400)))).syncOnce();

        List<Measurement> all = measurementRepository.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getValue()).isEqualByComparingTo("90");
    }

    @Test
    void aRoutineItemSourcedFromActivitywatchGetsADailyLogForTheSummarisedDay() {
        RoutineItem movementItem = routineItemRepository.save(
                new RoutineItem("Movement", null, null, null, RoutineItemSource.ACTIVITYWATCH, null, true, null));
        LocalDate date = LocalDate.of(2026, 10, 17);

        service(new FakeActivityWatchClient(List.of(new ActivityWatchDailyTotal(date, 5400)))).syncOnce();

        assertThat(dailyLogRepository.existsByRoutineItemIdAndLogDate(movementItem.getId(), date)).isTrue();
        assertThat(dailyLogRepository.findByLogDate(date)).hasSize(1);
        assertThat(dailyLogRepository.findByLogDate(date).get(0).getSource())
                .isEqualTo(DailyLogSource.ACTIVITYWATCH_SYNC);
        assertThat(dailyLogRepository.findByLogDate(date).get(0).getMeasuredMinutes()).isEqualTo(90);
    }

    @Test
    void syncingTheSameDayTwiceDoesNotCreateASecondDailyLogForTheRoutineItem() {
        routineItemRepository.save(
                new RoutineItem("Movement", null, null, null, RoutineItemSource.ACTIVITYWATCH, null, true, null));
        LocalDate date = LocalDate.of(2026, 10, 17);

        service(new FakeActivityWatchClient(List.of(new ActivityWatchDailyTotal(date, 1800)))).syncOnce();
        service(new FakeActivityWatchClient(List.of(new ActivityWatchDailyTotal(date, 5400)))).syncOnce();

        assertThat(dailyLogRepository.findByLogDate(date)).hasSize(1);
    }

    @Test
    void noRoutineItemSourcedFromActivitywatchStillRecordsTheMeasurementButLogsNothing() {
        LocalDate date = LocalDate.of(2026, 10, 17);
        service(new FakeActivityWatchClient(List.of(new ActivityWatchDailyTotal(date, 5400)))).syncOnce();

        assertThat(measurementRepository.findAll()).hasSize(1);
        assertThat(dailyLogRepository.findByLogDate(date)).isEmpty();
    }

    @Test
    void aDayWithZeroActiveSecondsIsSkipped() {
        LocalDate date = LocalDate.of(2026, 10, 17);
        service(new FakeActivityWatchClient(List.of(new ActivityWatchDailyTotal(date, 0)))).syncOnce();

        assertThat(measurementRepository.findAll()).isEmpty();
    }
}
