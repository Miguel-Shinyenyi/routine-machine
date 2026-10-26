package com.routinemachine.core.github;

import com.routinemachine.core.domain.DailyLog;
import com.routinemachine.core.domain.DailyLogSource;
import com.routinemachine.core.domain.MeasurementSource;
import com.routinemachine.core.domain.RoutineItem;
import com.routinemachine.core.domain.RoutineItemSource;
import com.routinemachine.core.repository.DailyLogRepository;
import com.routinemachine.core.repository.RoutineItemRepository;
import com.routinemachine.core.service.MeasurementService;
import com.routinemachine.core.service.OwnerAccount;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// Watering hole: GitHub, as a backup signal (PROJECT.md's own framing), not a primary one.
// Same shape as WakaTimeSyncService/ActivityWatchSyncService, except the measured quantity is
// a commit count, not a duration, so a daily_log written here carries no measuredMinutes.
@Service
public class GitHubSyncService {

    private static final int SYNC_WINDOW_DAYS = 7;
    private static final String COMMIT_COUNT_METRIC = "commit_count";

    private final GitHubCommitClient gitHubCommitClient;
    private final RoutineItemRepository routineItemRepository;
    private final DailyLogRepository dailyLogRepository;
    private final MeasurementService measurementService;
    private final OwnerAccount ownerAccount;
    private final boolean enabled;

    @Autowired
    public GitHubSyncService(
            GitHubCommitClient gitHubCommitClient,
            RoutineItemRepository routineItemRepository,
            DailyLogRepository dailyLogRepository,
            MeasurementService measurementService,
            OwnerAccount ownerAccount,
            @Value("${github-sync.enabled:true}") boolean enabled) {
        this.gitHubCommitClient = gitHubCommitClient;
        this.routineItemRepository = routineItemRepository;
        this.dailyLogRepository = dailyLogRepository;
        this.measurementService = measurementService;
        this.ownerAccount = ownerAccount;
        this.enabled = enabled;
    }

    // Package-visible, enabled defaulted true: tests call syncOnce() directly, same pattern
    // as the other watering-hole syncs' test constructors.
    GitHubSyncService(
            GitHubCommitClient gitHubCommitClient,
            RoutineItemRepository routineItemRepository,
            DailyLogRepository dailyLogRepository,
            MeasurementService measurementService,
            OwnerAccount ownerAccount) {
        this(gitHubCommitClient, routineItemRepository, dailyLogRepository, measurementService, ownerAccount, true);
    }

    @Scheduled(fixedDelayString = "${github-sync.poll-interval-ms:900000}")
    public void scheduledSync() {
        if (!enabled) {
            return;
        }
        syncOnce();
    }

    public void syncOnce() {
        LocalDate today = LocalDate.now();
        List<GitHubCommitDailyTotal> totals =
                gitHubCommitClient.fetchDailyCommitCounts(today.minusDays(SYNC_WINDOW_DAYS), today);
        List<RoutineItem> gitHubRoutineItems = ownerAccount.integrationTargets(routineItemRepository.findBySource(RoutineItemSource.GITHUB));

        for (GitHubCommitDailyTotal total : totals) {
            if (total.commitCount() <= 0) {
                continue;
            }
            measurementService.recordMeasurement(
                    MeasurementSource.GITHUB, COMMIT_COUNT_METRIC, total.date(), BigDecimal.valueOf(total.commitCount()));

            for (RoutineItem routineItem : gitHubRoutineItems) {
                if (!dailyLogRepository.existsByRoutineItemIdAndLogDate(routineItem.getId(), total.date())) {
                    DailyLog syncedLog = DailyLog.forRoutineItem(total.date(), routineItem, DailyLogSource.GITHUB_SYNC);
                    // The day belongs to whoever owns the item, never to nobody.
                    syncedLog.assignUser(routineItem.getUser());
                    dailyLogRepository.save(syncedLog);
                }
            }
        }
    }
}
