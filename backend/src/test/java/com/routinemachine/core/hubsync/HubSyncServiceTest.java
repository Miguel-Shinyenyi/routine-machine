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
    private com.routinemachine.core.repository.BookRepository bookRepository;

    @Autowired
    private com.routinemachine.core.repository.UserRepository userRepository;

    private HubSyncService serviceWith(GitHubContentClient client, HubJournalReader journalReader) {
        return serviceWith(client, journalReader, "");
    }

    // ownerEmail "" = no owner configured, so only unowned (pre-account) rows are the sync's targets.
    private HubSyncService serviceWith(GitHubContentClient client, HubJournalReader journalReader, String ownerEmail) {
        return new HubSyncService(
                client, journalReader, routineItemRepository, dailyLogRepository,
                processedFileRepository, bookRepository,
                new com.routinemachine.core.service.OwnerAccount(userRepository, ownerEmail));
    }

    private com.routinemachine.core.domain.User saveUser(String email) {
        return userRepository.save(new com.routinemachine.core.domain.User(email, "irrelevant-hash", null, null));
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
    void aNewCurrentBookTitleIsWrittenToBooks(@TempDir Path tempDir) throws Exception {
        Assumptions.assumeTrue(GitCryptTestFixture.isGitCryptAvailable(), "git-crypt not on PATH, skipping");
        Path repoDir = tempDir.resolve("umwayi");
        String key = GitCryptTestFixture.createEncryptedJournalRepo(repoDir, "2026-10-16.md", "- Current book: Sapiens\n");
        var client = new FakeGitHubContentClient(Map.of(), Map.of());
        var journalReader = new HubJournalReader(repoDir.toString(), () -> key);

        serviceWith(client, journalReader).syncOnce();

        assertThat(bookRepository.findCurrent()).isPresent();
        assertThat(bookRepository.findCurrent().orElseThrow().getTitle()).isEqualTo("Sapiens");
        assertThat(bookRepository.findCurrent().orElseThrow().getSource().getValue()).isEqualTo("hub-sync");
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

        assertThat(bookRepository.findAll()).hasSize(1);
    }

    // --- Per-user scoping, part 5: hub-sync is the owner's own integration. It found the
    // "journaling"/"writing" routine item by name across every account, and wrote an unowned
    // book as the current one for everybody.

    @Test
    void aJournalEntryLogsTheOwnersJournalingItemNotAnotherUsersItemWithTheSameName() {
        var owner = saveUser("hub-owner@example.com");
        var other = saveUser("hub-other@example.com");
        // The other user's item is saved first, so a lookup by name alone would pick it.
        var othersItem = new RoutineItem("journaling", null);
        othersItem.assignUser(other);
        othersItem = routineItemRepository.save(othersItem);
        var ownersItem = new RoutineItem("journaling", null);
        ownersItem.assignUser(owner);
        ownersItem = routineItemRepository.save(ownersItem);
        var client = new FakeGitHubContentClient(
                Map.of("journal/entries", List.of(new GitHubFile("journal/entries/2026-10-12.md", "sha-own"))),
                Map.of());

        serviceWith(client, new HubJournalReader("unused", () -> null), "hub-owner@example.com").syncOnce();

        Long ownersId = ownersItem.getId();
        Long othersId = othersItem.getId();
        var logs = dailyLogRepository.findByLogDate(LocalDate.of(2026, 10, 12));
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getRoutineItem().getId()).isEqualTo(ownersId).isNotEqualTo(othersId);
        assertThat(logs.get(0).getUser().getId()).isEqualTo(owner.getId());
    }

    @Test
    void aNewUsersJournalingItemNeverReceivesTheOwnersJournalEntries() {
        saveUser("hub-owner2@example.com");
        var newcomer = saveUser("hub-newcomer@example.com");
        var newcomersItem = new RoutineItem("journaling", null);
        newcomersItem.assignUser(newcomer);
        routineItemRepository.save(newcomersItem);
        var client = new FakeGitHubContentClient(
                Map.of("journal/entries", List.of(new GitHubFile("journal/entries/2026-10-13.md", "sha-new"))),
                Map.of());

        serviceWith(client, new HubJournalReader("unused", () -> null), "hub-owner2@example.com").syncOnce();

        assertThat(dailyLogRepository.findByLogDate(LocalDate.of(2026, 10, 13))).isEmpty();
    }

    @Test
    void theSyncedCurrentBookBelongsToTheOwner(@TempDir Path tempDir) throws Exception {
        Assumptions.assumeTrue(GitCryptTestFixture.isGitCryptAvailable(), "git-crypt not on PATH, skipping");
        var owner = saveUser("hub-owner3@example.com");
        Path repoDir = tempDir.resolve("umwayi");
        String key = GitCryptTestFixture.createEncryptedJournalRepo(repoDir, "2026-10-16.md", "- Current book: Sapiens\n");

        serviceWith(new FakeGitHubContentClient(Map.of(), Map.of()), new HubJournalReader(repoDir.toString(), () -> key),
                "hub-owner3@example.com").syncOnce();

        var books = bookRepository.findByUserIdOrderByCreatedAtDesc(owner.getId());
        assertThat(books).hasSize(1);
        assertThat(books.get(0).getTitle()).isEqualTo("Sapiens");
        assertThat(bookRepository.findByUserIsNullOrderByCreatedAtDesc()).extracting("title").doesNotContain("Sapiens");
    }

    @Test
    void anotherUsersNewerBookDoesNotMakeHubSyncWriteADuplicateOfTheOwnersBook(@TempDir Path tempDir) throws Exception {
        Assumptions.assumeTrue(GitCryptTestFixture.isGitCryptAvailable(), "git-crypt not on PATH, skipping");
        var owner = saveUser("hub-owner4@example.com");
        var other = saveUser("hub-other4@example.com");
        Path repoDir = tempDir.resolve("umwayi");
        String key = GitCryptTestFixture.createEncryptedJournalRepo(repoDir, "2026-10-16.md", "- Current book: Sapiens\n");
        var service = serviceWith(new FakeGitHubContentClient(Map.of(), Map.of()),
                new HubJournalReader(repoDir.toString(), () -> key), "hub-owner4@example.com");
        service.syncOnce();
        var othersBook = new com.routinemachine.core.domain.Book("Someone elses book", com.routinemachine.core.domain.ReadingSource.MANUAL);
        othersBook.assignUser(other);
        bookRepository.save(othersBook);

        service.syncOnce();

        assertThat(bookRepository.findByUserIdOrderByCreatedAtDesc(owner.getId())).hasSize(1);
    }
}
