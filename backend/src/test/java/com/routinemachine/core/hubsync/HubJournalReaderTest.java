package com.routinemachine.core.hubsync;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class HubJournalReaderTest {

    private static final String CURRENT_BOOK_LINE = "- Current book: Sapiens\n";

    private ListAppender<ILoggingEvent> logAppender;
    private Logger logger;

    @BeforeEach
    void captureLogs() {
        logger = (Logger) LoggerFactory.getLogger(HubJournalReader.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
    }

    @AfterEach
    void stopCapturingLogs() {
        logger.detachAppender(logAppender);
    }

    private List<String> loggedMessages() {
        return logAppender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    // A1 (reader half; app-starting half is the full `./mvnw test` run, see testing.md)
    @Test
    void readCurrentBookReportsDisabledWhenKeyVariableIsUnset(@TempDir Path tempDir) {
        assumeTrue(System.getenv("UMWAYI_JOURNAL_KEY_B64") == null,
                "UMWAYI_JOURNAL_KEY_B64 is set in this environment; this test assumes it is not");

        HubJournalReader reader = new HubJournalReader(tempDir.resolve("unused-repo").toString());

        Optional<String> result = reader.readCurrentBook();

        assertThat(result).isEmpty();
        assertThat(loggedMessages()).anyMatch(m -> m.contains("UMWAYI_JOURNAL_KEY_B64") && m.contains("disabled"));
    }

    // A2
    @Test
    void readCurrentBookReturnsTheTitleWithTheCorrectKey(@TempDir Path tempDir) throws Exception {
        assumeTrue(GitCryptTestFixture.isGitCryptAvailable(), "git-crypt not found on PATH");

        Path repo = tempDir.resolve("journal-repo");
        String key = GitCryptTestFixture.createEncryptedJournalRepo(repo, "2026-01-01.md", CURRENT_BOOK_LINE);
        HubJournalReader reader = withKey(key, repo.toString());

        Optional<String> result = reader.readCurrentBook();

        assertThat(result).contains("Sapiens");
    }

    // A3
    @Test
    void readCurrentBookFindsNothingWithTheWrongKey(@TempDir Path tempDir) throws Exception {
        assumeTrue(GitCryptTestFixture.isGitCryptAvailable(), "git-crypt not found on PATH");

        Path repo = tempDir.resolve("journal-repo");
        GitCryptTestFixture.createEncryptedJournalRepo(repo, "2026-01-01.md", CURRENT_BOOK_LINE);
        String wrongKey = GitCryptTestFixture.createUnrelatedKey(tempDir);
        HubJournalReader reader = withKey(wrongKey, repo.toString());

        Optional<String> result = reader.readCurrentBook();

        assertThat(result).isEmpty();
        assertThat(loggedMessages()).anyMatch(m -> m.toLowerCase().contains("locked"));
    }

    // A4
    @Test
    void anEncryptedFileNeverUnlockedStartsWithTheGitCryptHeaderAndIsNeverReturnedAsContent(@TempDir Path tempDir) throws Exception {
        assumeTrue(GitCryptTestFixture.isGitCryptAvailable(), "git-crypt not found on PATH");

        Path repo = tempDir.resolve("journal-repo");
        GitCryptTestFixture.createEncryptedJournalRepo(repo, "2026-01-01.md", CURRENT_BOOK_LINE);

        Path plainClone = tempDir.resolve("plain-clone");
        new ProcessBuilder("git", "clone", "--depth", "1", repo.toString(), plainClone.toString())
                .redirectErrorStream(true).start().waitFor();

        byte[] rawBytes = Files.readAllBytes(plainClone.resolve("journal/entries/2026-01-01.md"));
        assertThat(rawBytes).startsWith(0x00, 'G', 'I', 'T', 'C', 'R', 'Y', 'P', 'T');

        String wrongKey = GitCryptTestFixture.createUnrelatedKey(tempDir);
        HubJournalReader reader = withKey(wrongKey, repo.toString());
        Optional<String> result = reader.readCurrentBook();

        assertThat(result).isEmpty();
        assertThat(loggedMessages()).noneMatch(m -> m.contains("GITCRYPT"));
    }

    // A5
    @Test
    void theTempDirectoryAndKeyFileAreGoneAfterEveryCase(@TempDir Path tempDir) throws Exception {
        assumeTrue(GitCryptTestFixture.isGitCryptAvailable(), "git-crypt not found on PATH");

        Path repo = tempDir.resolve("journal-repo");
        String key = GitCryptTestFixture.createEncryptedJournalRepo(repo, "2026-01-01.md", CURRENT_BOOK_LINE);
        String wrongKey = GitCryptTestFixture.createUnrelatedKey(tempDir);

        Path tempRoot = Path.of(System.getProperty("java.io.tmpdir"));
        List<Path> before = listHubJournalTempDirs(tempRoot);

        withKey(key, repo.toString()).readCurrentBook();
        withKey(wrongKey, repo.toString()).readCurrentBook();
        new HubJournalReader(repo.toString()).readCurrentBook(); // unset-key case

        List<Path> after = listHubJournalTempDirs(tempRoot);
        assertThat(after).isEqualTo(before);
    }

    private List<Path> listHubJournalTempDirs(Path tempRoot) throws IOException {
        try (Stream<Path> stream = Files.list(tempRoot)) {
            return stream.filter(p -> p.getFileName().toString().startsWith("hub-journal-")).toList();
        }
    }

    // A6
    @Test
    void logsNeverContainTheKeyOrEntryTextForTheCorrectOrWrongKeyCase(@TempDir Path tempDir) throws Exception {
        assumeTrue(GitCryptTestFixture.isGitCryptAvailable(), "git-crypt not found on PATH");

        Path repo = tempDir.resolve("journal-repo");
        String correctKey = GitCryptTestFixture.createEncryptedJournalRepo(repo, "2026-01-01.md", CURRENT_BOOK_LINE);
        String wrongKey = GitCryptTestFixture.createUnrelatedKey(tempDir);

        withKey(correctKey, repo.toString()).readCurrentBook();
        withKey(wrongKey, repo.toString()).readCurrentBook();

        // Log messages are Java Strings built from fixed status text only (see
        // HubJournalReader); decoded key bytes can never appear inside one, so checking the
        // base64 form covers "the key" for both encodings.
        String allLogs = String.join("\n", loggedMessages());
        assertThat(allLogs).doesNotContain(correctKey);
        assertThat(allLogs).doesNotContain(wrongKey);
        assertThat(allLogs).doesNotContain("Sapiens");
    }

    private HubJournalReader withKey(String base64Key, String repoUrl) {
        // The reader reads the key straight from the environment (design point 3a); this test
        // process has no portable way to set a single env var for only the reader's lookup, so
        // it uses the package-visible constructor that also accepts an injected key supplier.
        return new HubJournalReader(repoUrl, () -> base64Key);
    }
}
