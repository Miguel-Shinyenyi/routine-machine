package com.routinemachine.core.activitywatch;

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

// Watering hole: ActivityWatch. Same shape as WakaTimeSyncService: a trailing window of daily
// active-time totals is upserted into measurements, and every routine item sourced from
// ACTIVITYWATCH gets one daily_log per day, written once and never updated. See
// docs/backend.md's decisions log for why ActivityWatchClientImpl assumes the backend and
// aw-server run on the same machine, unlike WakaTime and GitHub's cloud APIs.
@Service
public class ActivityWatchSyncService {

    private static final int SYNC_WINDOW_DAYS = 7;
    private static final String ACTIVE_MINUTES_METRIC = "active_minutes";

    private final ActivityWatchClient activityWatchClient;
    private final RoutineItemRepository routineItemRepository;
    private final DailyLogRepository dailyLogRepository;
    private final MeasurementService measurementService;
    private final boolean enabled;

    @Autowired
    public ActivityWatchSyncService(
            ActivityWatchClient activityWatchClient,
            RoutineItemRepository routineItemRepository,
            DailyLogRepository dailyLogRepository,
            MeasurementService measurementService,
            @Value("${activitywatch-sync.enabled:true}") boolean enabled) {
        this.activityWatchClient = activityWatchClient;
        this.routineItemRepository = routineItemRepository;
        this.dailyLogRepository = dailyLogRepository;
        this.measurementService = measurementService;
        this.enabled = enabled;
    }

    // Package-visible, enabled defaulted true: tests call syncOnce() directly, same pattern
    // as HubSyncService/WakaTimeSyncService's test constructors.
    ActivityWatchSyncService(
            ActivityWatchClient activityWatchClient,
            RoutineItemRepository routineItemRepository,
            DailyLogRepository dailyLogRepository,
            MeasurementService measurementService) {
        this(activityWatchClient, routineItemRepository, dailyLogRepository, measurementService, true);
    }

    @Scheduled(fixedDelayString = "${activitywatch-sync.poll-interval-ms:900000}")
    public void scheduledSync() {
        if (!enabled) {
            return;
        }
        syncOnce();
    }

    public void syncOnce() {
        LocalDate today = LocalDate.now();
        List<ActivityWatchDailyTotal> totals =
                activityWatchClient.fetchDailyActiveTotals(today.minusDays(SYNC_WINDOW_DAYS), today);
        List<RoutineItem> activityWatchRoutineItems = routineItemRepository.findBySource(RoutineItemSource.ACTIVITYWATCH);

        for (ActivityWatchDailyTotal total : totals) {
            if (total.activeSeconds() <= 0) {
                continue;
            }
            BigDecimal minutes = BigDecimal.valueOf(total.activeSeconds())
                    .divide(BigDecimal.valueOf(60), 0, RoundingMode.HALF_UP);
            measurementService.recordMeasurement(MeasurementSource.ACTIVITYWATCH, ACTIVE_MINUTES_METRIC, total.date(), minutes);

            for (RoutineItem routineItem : activityWatchRoutineItems) {
                if (!dailyLogRepository.existsByRoutineItemIdAndLogDate(routineItem.getId(), total.date())) {
                    dailyLogRepository.save(DailyLog.forRoutineItem(
                            total.date(), routineItem, DailyLogSource.ACTIVITYWATCH_SYNC, minutes.intValueExact()));
                }
            }
        }
    }
}
