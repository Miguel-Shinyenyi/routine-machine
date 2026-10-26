package com.routinemachine.core.github;

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
class GitHubSyncServiceTest extends AbstractIntegrationTest {

    @Autowired
    private RoutineItemRepository routineItemRepository;

    @Autowired
    private DailyLogRepository dailyLogRepository;

    @Autowired
    private MeasurementRepository measurementRepository;
    @Autowired
    private UserRepository userRepository;

    private GitHubSyncService service(GitHubCommitClient client) {
        return new GitHubSyncService(
                client, routineItemRepository, dailyLogRepository, new MeasurementService(measurementRepository), new OwnerAccount(userRepository, ""));
    }

    private static class FakeGitHubCommitClient implements GitHubCommitClient {
        private final List<GitHubCommitDailyTotal> totals;

        FakeGitHubCommitClient(List<GitHubCommitDailyTotal> totals) {
            this.totals = totals;
        }

        @Override
        public List<GitHubCommitDailyTotal> fetchDailyCommitCounts(LocalDate start, LocalDate end) {
            return totals;
        }
    }

    @Test
    void aDaysCommitCountIsRecordedAsAMeasurement() {
        LocalDate date = LocalDate.of(2026, 10, 17);
        service(new FakeGitHubCommitClient(List.of(new GitHubCommitDailyTotal(date, 4)))).syncOnce();

        List<Measurement> all = measurementRepository.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getSource()).isEqualTo(MeasurementSource.GITHUB);
        assertThat(all.get(0).getMetric()).isEqualTo("commit_count");
        assertThat(all.get(0).getMeasurementDate()).isEqualTo(date);
        assertThat(all.get(0).getValue()).isEqualByComparingTo("4");
    }

    @Test
    void syncingTheSameDayTwiceUpdatesTheMeasurementInsteadOfDuplicatingIt() {
        LocalDate date = LocalDate.of(2026, 10, 17);
        service(new FakeGitHubCommitClient(List.of(new GitHubCommitDailyTotal(date, 2)))).syncOnce();
        service(new FakeGitHubCommitClient(List.of(new GitHubCommitDailyTotal(date, 5)))).syncOnce();

        List<Measurement> all = measurementRepository.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getValue()).isEqualByComparingTo("5");
    }

    @Test
    void aRoutineItemSourcedFromGithubGetsADailyLogForTheSummarisedDay() {
        RoutineItem codingItem = routineItemRepository.save(
                new RoutineItem("Coding (backup)", null, null, null, RoutineItemSource.GITHUB, null, true, null));
        LocalDate date = LocalDate.of(2026, 10, 17);

        service(new FakeGitHubCommitClient(List.of(new GitHubCommitDailyTotal(date, 3)))).syncOnce();

        assertThat(dailyLogRepository.existsByRoutineItemIdAndLogDate(codingItem.getId(), date)).isTrue();
        assertThat(dailyLogRepository.findByLogDate(date)).hasSize(1);
        assertThat(dailyLogRepository.findByLogDate(date).get(0).getSource()).isEqualTo(DailyLogSource.GITHUB_SYNC);
        assertThat(dailyLogRepository.findByLogDate(date).get(0).getMeasuredMinutes()).isNull();
    }

    @Test
    void syncingTheSameDayTwiceDoesNotCreateASecondDailyLogForTheRoutineItem() {
        routineItemRepository.save(
                new RoutineItem("Coding (backup)", null, null, null, RoutineItemSource.GITHUB, null, true, null));
        LocalDate date = LocalDate.of(2026, 10, 17);

        service(new FakeGitHubCommitClient(List.of(new GitHubCommitDailyTotal(date, 2)))).syncOnce();
        service(new FakeGitHubCommitClient(List.of(new GitHubCommitDailyTotal(date, 5)))).syncOnce();

        assertThat(dailyLogRepository.findByLogDate(date)).hasSize(1);
    }

    @Test
    void noRoutineItemSourcedFromGithubStillRecordsTheMeasurementButLogsNothing() {
        LocalDate date = LocalDate.of(2026, 10, 17);
        service(new FakeGitHubCommitClient(List.of(new GitHubCommitDailyTotal(date, 3)))).syncOnce();

        assertThat(measurementRepository.findAll()).hasSize(1);
        assertThat(dailyLogRepository.findByLogDate(date)).isEmpty();
    }

    @Test
    void aDayWithZeroCommitsIsSkipped() {
        LocalDate date = LocalDate.of(2026, 10, 17);
        service(new FakeGitHubCommitClient(List.of(new GitHubCommitDailyTotal(date, 0)))).syncOnce();

        assertThat(measurementRepository.findAll()).isEmpty();
    }
}
