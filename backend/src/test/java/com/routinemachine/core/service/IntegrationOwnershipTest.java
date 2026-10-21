package com.routinemachine.core.service;

import com.routinemachine.core.AbstractIntegrationTest;
import com.routinemachine.core.activitywatch.ActivityWatchDailyTotal;
import com.routinemachine.core.activitywatch.ActivityWatchSyncService;
import com.routinemachine.core.domain.DailyLog;
import com.routinemachine.core.domain.RoutineItem;
import com.routinemachine.core.domain.RoutineItemSource;
import com.routinemachine.core.domain.User;
import com.routinemachine.core.github.GitHubCommitDailyTotal;
import com.routinemachine.core.github.GitHubSyncService;
import com.routinemachine.core.repository.DailyLogRepository;
import com.routinemachine.core.repository.MeasurementRepository;
import com.routinemachine.core.repository.RoutineItemRepository;
import com.routinemachine.core.repository.UserRepository;
import com.routinemachine.core.rescuetime.RescueTimeDailyTotal;
import com.routinemachine.core.rescuetime.RescueTimeSyncService;
import com.routinemachine.core.wakatime.WakaTimeDailyTotal;
import com.routinemachine.core.wakatime.WakaTimeSyncService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Per-user scoping, part 5. An integration (WakaTime, ActivityWatch, GitHub, RescueTime) is the
// deployment's own: it pulls the owner's data using the owner's credentials. It used to log into
// EVERY routine item with its source, whoever owned it, and the log it created belonged to nobody.
// Now it logs only into the owner's (or unowned, pre-account) items, and the log belongs to the
// item's owner.
@DataJpaTest
class IntegrationOwnershipTest extends AbstractIntegrationTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 17);

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoutineItemRepository routineItemRepository;
    @Autowired
    private DailyLogRepository dailyLogRepository;
    @Autowired
    private MeasurementRepository measurementRepository;

    private MeasurementService measurementService() {
        return new MeasurementService(measurementRepository);
    }

    private OwnerAccount ownerIs(String email) {
        return new OwnerAccount(userRepository, email);
    }

    private User saveUser(String email) {
        return userRepository.save(new User(email, "irrelevant-hash", null, null));
    }

    private RoutineItem item(String name, RoutineItemSource source, User owner) {
        RoutineItem item = new RoutineItem(name, null, null, null, source, null, true, null);
        item.assignUser(owner);
        return routineItemRepository.save(item);
    }

    private List<DailyLog> logsOf(RoutineItem item) {
        return dailyLogRepository.findAll().stream()
                .filter(log -> log.getRoutineItem() != null && log.getRoutineItem().getId().equals(item.getId()))
                .toList();
    }

    private void syncWakaTime(OwnerAccount owner) {
        new WakaTimeSyncService(
                (start, end) -> List.of(new WakaTimeDailyTotal(DAY, 3600)), routineItemRepository, dailyLogRepository,
                measurementService(), owner, true).syncOnce();
    }

    private void syncActivityWatch(OwnerAccount owner) {
        new ActivityWatchSyncService(
                (start, end) -> List.of(new ActivityWatchDailyTotal(DAY, 3600)), routineItemRepository,
                dailyLogRepository, measurementService(), owner, true).syncOnce();
    }

    private void syncGitHub(OwnerAccount owner) {
        new GitHubSyncService(
                (start, end) -> List.of(new GitHubCommitDailyTotal(DAY, 4)), routineItemRepository, dailyLogRepository,
                measurementService(), owner, true).syncOnce();
    }

    private void syncRescueTime(OwnerAccount owner) {
        new RescueTimeSyncService(
                (start, end) -> List.of(new RescueTimeDailyTotal(DAY, 3600)), routineItemRepository,
                dailyLogRepository, measurementService(), owner, true).syncOnce();
    }

    private interface Sync {
        void run(OwnerAccount owner);
    }

    private void assertSyncRespectsOwnership(RoutineItemSource source, Sync sync) {
        User owner = saveUser("owner-" + source + "@example.com");
        User other = saveUser("other-" + source + "@example.com");
        RoutineItem ownersItem = item("owners " + source, source, owner);
        RoutineItem othersItem = item("others " + source, source, other);
        RoutineItem unownedItem = item("unowned " + source, source, null);

        sync.run(ownerIs("owner-" + source + "@example.com"));

        assertThat(logsOf(ownersItem)).as("the owner's item").hasSize(1);
        assertThat(logsOf(ownersItem).get(0).getUser()).isNotNull();
        assertThat(logsOf(ownersItem).get(0).getUser().getId()).isEqualTo(owner.getId());
        assertThat(logsOf(othersItem)).as("another account's item with the same source").isEmpty();
        assertThat(logsOf(unownedItem)).as("an unowned, pre-account item").hasSize(1);
        assertThat(logsOf(unownedItem).get(0).getUser()).as("the log stays unowned").isNull();
    }

    @Test
    void wakaTimeLogsOnlyIntoTheOwnersAndUnownedItemsAndTheLogBelongsToTheItemsOwner() {
        assertSyncRespectsOwnership(RoutineItemSource.WAKATIME, this::syncWakaTime);
    }

    @Test
    void activityWatchLogsOnlyIntoTheOwnersAndUnownedItemsAndTheLogBelongsToTheItemsOwner() {
        assertSyncRespectsOwnership(RoutineItemSource.ACTIVITYWATCH, this::syncActivityWatch);
    }

    @Test
    void gitHubLogsOnlyIntoTheOwnersAndUnownedItemsAndTheLogBelongsToTheItemsOwner() {
        assertSyncRespectsOwnership(RoutineItemSource.GITHUB, this::syncGitHub);
    }

    @Test
    void rescueTimeLogsOnlyIntoTheOwnersAndUnownedItemsAndTheLogBelongsToTheItemsOwner() {
        assertSyncRespectsOwnership(RoutineItemSource.RESCUETIME, this::syncRescueTime);
    }

    @Test
    void withNoOwnerConfiguredANewUsersItemStillGetsNothingFromAnIntegration() {
        User newcomer = saveUser("newcomer@example.com");
        RoutineItem newcomersItem = item("newcomers coding", RoutineItemSource.WAKATIME, newcomer);

        syncWakaTime(ownerIs(""));

        assertThat(logsOf(newcomersItem)).isEmpty();
    }

    @Test
    void theMeasurementIsStillRecordedWhoeverOwnsTheItems() {
        saveUser("owner-m@example.com");

        syncWakaTime(ownerIs("owner-m@example.com"));

        assertThat(measurementRepository.findAll()).hasSize(1);
    }
}
