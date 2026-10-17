package com.routinemachine.core.hubsync;

import com.routinemachine.core.AbstractIntegrationTest;
import com.routinemachine.core.domain.RoutineItem;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class HubSyncServiceTest extends AbstractIntegrationTest {

    @Autowired
    private com.routinemachine.core.repository.RoutineItemRepository routineItemRepository;

    @Autowired
    private com.routinemachine.core.repository.DailyLogRepository dailyLogRepository;

    @Autowired
    private com.routinemachine.core.repository.HubSyncProcessedFileRepository processedFileRepository;

    @Autowired
    private com.routinemachine.core.repository.CurrentReadingLogRepository currentReadingLogRepository;

    private HubSyncService serviceWith(GitHubContentClient client, HubJournalReader journalReader) {
        return new HubSyncService(
                client, journalReader, routineItemRepository, dailyLogRepository,
                processedFileRepository, currentReadingLogRepository);
    }

    private HubSyncService serviceWith(GitHubContentClient client) {
        // Blank key means readCurrentBook() always returns empty, exercising the sync's
        // other two concerns (journal entries, articles) without touching git-crypt at all.
        return serviceWith(client, new HubJournalReader("unused", () -> null));
    }

    private static final class FakeGitHubContentClient implements GitHubContentClient {
        private final Map<String, List<GitHubFile>> filesByDirectory;
        private final Map<String, LocalDate> commitDatesByPath;

        FakeGitHubContentClient(Map<String, List<GitHubFile>> filesByDirectory, Map<String, LocalDate> commitDatesByPath) {
            this.filesByDirectory = filesByDirectory;
            this.commitDatesByPath = commitDatesByPath;
        }

        @Override
        public List<GitHubFile> listDirectory(String path) {
            return filesByDirectory.getOrDefault(path, List.of());
        }

        @Override
        public Optional<LocalDate> firstCommitDate(String path) {
            return Optional.ofNullable(commitDatesByPath.get(path));
        }
    }

    @Test
    void aNewJournalEntryLogsTheJournalingRoutineItemOnTheFilesDate() {
        routineItemRepository.save(new RoutineItem("journaling", null));
        var client = new FakeGitHubContentClient(
                Map.of("journal/entries", List.of(new GitHubFile("journal/entries/2026-10-10.md", "sha-a"))),
                Map.of());

        serviceWith(client).syncOnce();

        assertThat(dailyLogRepository.findByLogDate(LocalDate.of(2026, 10, 10)))
                .hasSize(1)
                .allSatisfy(log -> assertThat(log.getSource().getValue()).isEqualTo("hub-sync"));
    }

    @Test
    void syncingTheSameFileTwiceDoesNotCreateASecondLog() {
        routineItemRepository.save(new RoutineItem("journaling", null));
        var client = new FakeGitHubContentClient(
                Map.of("journal/entries", List.of(new GitHubFile("journal/entries/2026-10-10.md", "sha-a"))),
                Map.of());
        var service = serviceWith(client);

        service.syncOnce();
        service.syncOnce();

        assertThat(dailyLogRepository.findByLogDate(LocalDate.of(2026, 10, 10))).hasSize(1);
    }

    @Test
    void aFileRenamedButWithTheSameContentShaIsNotCountedTwice() {
        routineItemRepository.save(new RoutineItem("journaling", null));
        var originalPathClient = new FakeGitHubContentClient(
                Map.of("journal/entries", List.of(new GitHubFile("journal/entries/2026-10-10.md", "sha-a"))),
                Map.of());
        serviceWith(originalPathClient).syncOnce();

        // Same content (same sha), moved to a different path -- the exact case the task asked
        // to check for, not assume away.
        var renamedPathClient = new FakeGitHubContentClient(
                Map.of("journal/entries", List.of(new GitHubFile("journal/entries/renamed-2026-10-10.md", "sha-a"))),
                Map.of());

        serviceWith(renamedPathClient).syncOnce();

        assertThat(dailyLogRepository.findByLogDate(LocalDate.of(2026, 10, 10))).hasSize(1);
        assertThat(processedFileRepository.existsByContentSha("sha-a")).isTrue();
    }

    @Test
    void aJournalFileWithAnUnrecognisedNameIsSkippedWithoutError() {
        routineItemRepository.save(new RoutineItem("journaling", null));
        var client = new FakeGitHubContentClient(
                Map.of("journal/entries", List.of(new GitHubFile("journal/entries/README.md", "sha-b"))),
                Map.of());

        serviceWith(client).syncOnce();

        assertThat(dailyLogRepository.findAll()).isEmpty();
    }

    @Test
    void anArticleLogsTheWritingRoutineItemOnItsFirstCommitDate() {
        routineItemRepository.save(new RoutineItem("writing", null));
        var client = new FakeGitHubContentClient(
                Map.of("site/philosophy", List.of(new GitHubFile("site/philosophy/an-essay.md", "sha-c"))),
                Map.of("site/philosophy/an-essay.md", LocalDate.of(2026, 9, 1)));

        serviceWith(client).syncOnce();

        assertThat(dailyLogRepository.findByLogDate(LocalDate.of(2026, 9, 1)))
                .hasSize(1)
                .allSatisfy(log -> assertThat(log.getSource().getValue()).isEqualTo("hub-sync"));
    }

    @Test
    void anArticleWithNoDiscoverableCommitDateIsSkippedWithoutError() {
        routineItemRepository.save(new RoutineItem("writing", null));
        var client = new FakeGitHubContentClient(
                Map.of("site/philosophy", List.of(new GitHubFile("site/philosophy/an-essay.md", "sha-d"))),
                Map.of());

        serviceWith(client).syncOnce();

        assertThat(dailyLogRepository.findAll()).isEmpty();
    }

    @Test
    void aFileWithNoMatchingRoutineItemIsRecordedAsProcessedButLogsNothing() {
        // No "journaling" routine item exists at all.
        var client = new FakeGitHubContentClient(
                Map.of("journal/entries", List.of(new GitHubFile("journal/entries/2026-10-10.md", "sha-e"))),
                Map.of());

        serviceWith(client).syncOnce();

        assertThat(dailyLogRepository.findAll()).isEmpty();
        assertThat(processedFileRepository.existsByContentSha("sha-e")).isTrue();
    }

    @Test
    void aNewCurrentBookTitleIsWrittenToTheReadingLog(@TempDir Path tempDir) throws Exception {
        Assumptions.assumeTrue(GitCryptTestFixture.isGitCryptAvailable(), "git-crypt not on PATH, skipping");
        Path repoDir = tempDir.resolve("umwayi");
        String key = GitCryptTestFixture.createEncryptedJournalRepo(repoDir, "2026-10-16.md", "- Current book: Sapiens\n");
        var client = new FakeGitHubContentClient(Map.of(), Map.of());
        var journalReader = new HubJournalReader(repoDir.toString(), () -> key);

        serviceWith(client, journalReader).syncOnce();

        assertThat(currentReadingLogRepository.findLatest()).isPresent();
        assertThat(currentReadingLogRepository.findLatest().orElseThrow().getTitle()).isEqualTo("Sapiens");
        assertThat(currentReadingLogRepository.findLatest().orElseThrow().getSource().getValue()).isEqualTo("hub-sync");
    }

    @Test
    void syncingTwiceWithTheSameCurrentBookDoesNotWriteADuplicateRow(@TempDir Path tempDir) throws Exception {
        Assumptions.assumeTrue(GitCryptTestFixture.isGitCryptAvailable(), "git-crypt not on PATH, skipping");
        Path repoDir = tempDir.resolve("umwayi");
        String key = GitCryptTestFixture.createEncryptedJournalRepo(repoDir, "2026-10-16.md", "- Current book: Sapiens\n");
        var client = new FakeGitHubContentClient(Map.of(), Map.of());
        var journalReader = new HubJournalReader(repoDir.toString(), () -> key);
        var service = serviceWith(client, journalReader);

        service.syncOnce();
        service.syncOnce();

        assertThat(currentReadingLogRepository.findAll()).hasSize(1);
    }
}
