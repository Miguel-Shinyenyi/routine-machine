package com.routinemachine.core.wakatime;

import com.routinemachine.core.domain.DailyLog;
import com.routinemachine.core.domain.DailyLogSource;
import com.routinemachine.core.domain.MeasurementSource;
import com.routinemachine.core.domain.RoutineItem;
import com.routinemachine.core.domain.RoutineItemSource;
import com.routinemachine.core.repository.DailyLogRepository;
import com.routinemachine.core.repository.RoutineItemRepository;
import com.routinemachine.core.service.MeasurementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

// Watering hole: WakaTime. Each run fetches the last SYNC_WINDOW_DAYS of daily coding totals
// (wide enough to catch a day's total still settling, re-synced idempotently rather than
// requiring a separate "is this day final yet" signal) and records each day's total as a
// coding_minutes measurement. Every routine item sourced from WAKATIME then gets a daily_log
// for that day, once, the same "record once, never update the log itself" choice HubSyncService
// makes, with measurements as the table of record for a day's possibly-corrected raw value.
@Service
public class WakaTimeSyncService {

    private static final int SYNC_WINDOW_DAYS = 7;
    private static final String CODING_MINUTES_METRIC = "coding_minutes";

    private final WakaTimeClient wakaTimeClient;
    private final RoutineItemRepository routineItemRepository;
    private final DailyLogRepository dailyLogRepository;
    private final MeasurementService measurementService;
    private final boolean enabled;

    @Autowired
    public WakaTimeSyncService(
            WakaTimeClient wakaTimeClient,
            RoutineItemRepository routineItemRepository,
            DailyLogRepository dailyLogRepository,
            MeasurementService measurementService,
            @Value("${wakatime-sync.enabled:true}") boolean enabled) {
        this.wakaTimeClient = wakaTimeClient;
        this.routineItemRepository = routineItemRepository;
        this.dailyLogRepository = dailyLogRepository;
        this.measurementService = measurementService;
        this.enabled = enabled;
    }

    // Package-visible, enabled defaulted true: tests call syncOnce() directly, same pattern
    // as HubSyncService's test constructor.
    WakaTimeSyncService(
            WakaTimeClient wakaTimeClient,
            RoutineItemRepository routineItemRepository,
            DailyLogRepository dailyLogRepository,
            MeasurementService measurementService) {
        this(wakaTimeClient, routineItemRepository, dailyLogRepository, measurementService, true);
    }

    @Scheduled(fixedDelayString = "${wakatime-sync.poll-interval-ms:900000}")
    public void scheduledSync() {
        if (!enabled) {
            return;
        }
        syncOnce();
    }

    public void syncOnce() {
        LocalDate today = LocalDate.now();
        List<WakaTimeDailyTotal> totals = wakaTimeClient.fetchDailyTotals(today.minusDays(SYNC_WINDOW_DAYS), today);
        List<RoutineItem> wakaTimeRoutineItems = routineItemRepository.findBySource(RoutineItemSource.WAKATIME);

        for (WakaTimeDailyTotal total : totals) {
            if (total.totalSeconds() <= 0) {
                continue;
            }
            BigDecimal minutes = BigDecimal.valueOf(total.totalSeconds())
                    .divide(BigDecimal.valueOf(60), 0, RoundingMode.HALF_UP);
            measurementService.recordMeasurement(MeasurementSource.WAKATIME, CODING_MINUTES_METRIC, total.date(), minutes);

            for (RoutineItem routineItem : wakaTimeRoutineItems) {
                if (!dailyLogRepository.existsByRoutineItemIdAndLogDate(routineItem.getId(), total.date())) {
                    dailyLogRepository.save(DailyLog.forRoutineItem(
                            total.date(), routineItem, DailyLogSource.WAKATIME_SYNC, minutes.intValueExact()));
                }
            }
        }
    }
}
