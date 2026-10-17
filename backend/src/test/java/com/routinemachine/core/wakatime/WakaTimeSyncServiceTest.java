package com.routinemachine.core.wakatime;

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
class WakaTimeSyncServiceTest extends AbstractIntegrationTest {

    @Autowired
    private RoutineItemRepository routineItemRepository;

    @Autowired
    private DailyLogRepository dailyLogRepository;

    @Autowired
    private MeasurementRepository measurementRepository;

    private WakaTimeSyncService service(WakaTimeClient client) {
        return new WakaTimeSyncService(
                client, routineItemRepository, dailyLogRepository, new MeasurementService(measurementRepository));
    }

    private static class FakeWakaTimeClient implements WakaTimeClient {
        private final List<WakaTimeDailyTotal> totals;

        FakeWakaTimeClient(List<WakaTimeDailyTotal> totals) {
            this.totals = totals;
        }

        @Override
        public List<WakaTimeDailyTotal> fetchDailyTotals(LocalDate start, LocalDate end) {
            return totals;
        }
    }

    @Test
    void aDaysCodingTotalIsRecordedAsAMeasurementInMinutes() {
        LocalDate date = LocalDate.of(2026, 10, 17);
        service(new FakeWakaTimeClient(List.of(new WakaTimeDailyTotal(date, 7200)))).syncOnce();

        List<Measurement> all = measurementRepository.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getSource()).isEqualTo(MeasurementSource.WAKATIME);
        assertThat(all.get(0).getMetric()).isEqualTo("coding_minutes");
        assertThat(all.get(0).getMeasurementDate()).isEqualTo(date);
        assertThat(all.get(0).getValue()).isEqualByComparingTo("120");
    }

    @Test
    void syncingTheSameDayTwiceUpdatesTheMeasurementInsteadOfDuplicatingIt() {
        LocalDate date = LocalDate.of(2026, 10, 17);
        service(new FakeWakaTimeClient(List.of(new WakaTimeDailyTotal(date, 3600)))).syncOnce();
        service(new FakeWakaTimeClient(List.of(new WakaTimeDailyTotal(date, 7200)))).syncOnce();

        List<Measurement> all = measurementRepository.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getValue()).isEqualByComparingTo("120");
    }

    @Test
    void aRoutineItemSourcedFromWakatimeGetsADailyLogForTheSummarisedDay() {
        RoutineItem codingItem = routineItemRepository.save(
                new RoutineItem("Deep work", null, null, null, RoutineItemSource.WAKATIME, null, true, null));
        LocalDate date = LocalDate.of(2026, 10, 17);

        service(new FakeWakaTimeClient(List.of(new WakaTimeDailyTotal(date, 7200)))).syncOnce();

        assertThat(dailyLogRepository.existsByRoutineItemIdAndLogDate(codingItem.getId(), date)).isTrue();
        assertThat(dailyLogRepository.findByLogDate(date)).hasSize(1);
        assertThat(dailyLogRepository.findByLogDate(date).get(0).getSource()).isEqualTo(DailyLogSource.WAKATIME_SYNC);
        assertThat(dailyLogRepository.findByLogDate(date).get(0).getMeasuredMinutes()).isEqualTo(120);
    }

    @Test
    void syncingTheSameDayTwiceDoesNotCreateASecondDailyLogForTheRoutineItem() {
        routineItemRepository.save(
                new RoutineItem("Deep work", null, null, null, RoutineItemSource.WAKATIME, null, true, null));
        LocalDate date = LocalDate.of(2026, 10, 17);

        service(new FakeWakaTimeClient(List.of(new WakaTimeDailyTotal(date, 3600)))).syncOnce();
        service(new FakeWakaTimeClient(List.of(new WakaTimeDailyTotal(date, 7200)))).syncOnce();

        assertThat(dailyLogRepository.findByLogDate(date)).hasSize(1);
    }

    @Test
    void noRoutineItemSourcedFromWakatimeStillRecordsTheMeasurementButLogsNothing() {
        LocalDate date = LocalDate.of(2026, 10, 17);
        service(new FakeWakaTimeClient(List.of(new WakaTimeDailyTotal(date, 7200)))).syncOnce();

        assertThat(measurementRepository.findAll()).hasSize(1);
        assertThat(dailyLogRepository.findByLogDate(date)).isEmpty();
    }

    @Test
    void aDayWithZeroSecondsCodedIsSkipped() {
        LocalDate date = LocalDate.of(2026, 10, 17);
        service(new FakeWakaTimeClient(List.of(new WakaTimeDailyTotal(date, 0)))).syncOnce();

        assertThat(measurementRepository.findAll()).isEmpty();
    }
}
