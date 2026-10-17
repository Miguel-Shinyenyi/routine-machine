package com.routinemachine.core.rescuetime;

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

// Watering hole: RescueTime, explicitly optional per PROJECT.md's own framing of Phase 6
// ("optionally RescueTime"). Same shape as WakaTimeSyncService/ActivityWatchSyncService, with
// one deliberate difference: rescuetime-sync.enabled defaults to FALSE, not true. Every other
// sync in this phase is on by default and only switched off in tests; this one stays off in
// production too unless someone opts in, since PROJECT.md names it as optional rather than a
// given, unlike the other three.
@Service
public class RescueTimeSyncService {

    private static final int SYNC_WINDOW_DAYS = 7;
    private static final String ACTIVE_MINUTES_METRIC = "active_minutes";

    private final RescueTimeClient rescueTimeClient;
    private final RoutineItemRepository routineItemRepository;
    private final DailyLogRepository dailyLogRepository;
    private final MeasurementService measurementService;
    private final boolean enabled;

    @Autowired
    public RescueTimeSyncService(
            RescueTimeClient rescueTimeClient,
            RoutineItemRepository routineItemRepository,
            DailyLogRepository dailyLogRepository,
            MeasurementService measurementService,
            @Value("${rescuetime-sync.enabled:false}") boolean enabled) {
        this.rescueTimeClient = rescueTimeClient;
        this.routineItemRepository = routineItemRepository;
        this.dailyLogRepository = dailyLogRepository;
        this.measurementService = measurementService;
        this.enabled = enabled;
    }

    // Package-visible, enabled defaulted FALSE (not true, unlike the other syncs' test
    // constructors): this feature is opt-in even in production, so a test using this
    // constructor without saying otherwise should see exactly that default.
    RescueTimeSyncService(
            RescueTimeClient rescueTimeClient,
            RoutineItemRepository routineItemRepository,
            DailyLogRepository dailyLogRepository,
            MeasurementService measurementService) {
        this(rescueTimeClient, routineItemRepository, dailyLogRepository, measurementService, false);
    }

    @Scheduled(fixedDelayString = "${rescuetime-sync.poll-interval-ms:900000}")
    public void scheduledSync() {
        if (!enabled) {
            return;
        }
        syncOnce();
    }

    public void syncOnce() {
        LocalDate today = LocalDate.now();
        List<RescueTimeDailyTotal> totals =
                rescueTimeClient.fetchDailyActiveTotals(today.minusDays(SYNC_WINDOW_DAYS), today);
        List<RoutineItem> rescueTimeRoutineItems = routineItemRepository.findBySource(RoutineItemSource.RESCUETIME);

        for (RescueTimeDailyTotal total : totals) {
            if (total.activeSeconds() <= 0) {
                continue;
            }
            BigDecimal minutes = BigDecimal.valueOf(total.activeSeconds())
                    .divide(BigDecimal.valueOf(60), 0, RoundingMode.HALF_UP);
            measurementService.recordMeasurement(MeasurementSource.RESCUETIME, ACTIVE_MINUTES_METRIC, total.date(), minutes);

            for (RoutineItem routineItem : rescueTimeRoutineItems) {
                if (!dailyLogRepository.existsByRoutineItemIdAndLogDate(routineItem.getId(), total.date())) {
                    dailyLogRepository.save(DailyLog.forRoutineItem(
                            total.date(), routineItem, DailyLogSource.RESCUETIME_SYNC, minutes.intValueExact()));
                }
            }
        }
    }
}
