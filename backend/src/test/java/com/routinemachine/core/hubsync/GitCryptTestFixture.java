package com.routinemachine.core.hubsync;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Builds a throwaway git-crypt repository and key for tests. Never touches the real UMWAYI
 * repo or the real key. Every repo and key this creates lives under a JUnit {@code @TempDir}
 * and is deleted when the test finishes.
 */
final class GitCryptTestFixture {

    private GitCryptTestFixture() {
    }

    /**
     * Creates a git repo at {@code repoDir}, initializes git-crypt on it (internal key, no
     * GPG), commits one encrypted journal entry with the given content, and returns the
     * base64-encoded exported key for it.
     */
    static String createEncryptedJournalRepo(Path repoDir, String entryFileName, String entryContent) throws IOException, InterruptedException {
        Files.createDirectories(repoDir);
        run(repoDir, "git", "init", "-q", "-b", "main");
        run(repoDir, "git", "config", "user.email", "test@example.com");
        run(repoDir, "git", "config", "user.name", "Test");

        Files.writeString(repoDir.resolve(".gitattributes"),
                "journal/entries/** filter=git-crypt diff=git-crypt\n");
        run(repoDir, "git", "add", ".gitattributes");
        run(repoDir, "git", "commit", "-q", "-m", "init");

        run(repoDir, "git-crypt", "init");

        Path entriesDir = repoDir.resolve("journal").resolve("entries");
        Files.createDirectories(entriesDir);
        Files.writeString(entriesDir.resolve(entryFileName), entryContent);
        run(repoDir, "git", "add", "journal");
        run(repoDir, "git", "commit", "-q", "-m", "entry");

        Path keyFile = Files.createTempFile(repoDir.getParent(), "fixture-key-", ".key");
        run(repoDir, "git-crypt", "export-key", keyFile.toString());
        byte[] keyBytes = Files.readAllBytes(keyFile);
        Files.delete(keyFile);
        return Base64.getEncoder().encodeToString(keyBytes);
    }

    /** A second, unrelated git-crypt key that will not unlock a different repo's journal. */
    static String createUnrelatedKey(Path scratchDir) throws IOException, InterruptedException {
        Path otherRepo = scratchDir.resolve("unrelated-repo");
        return createEncryptedJournalRepo(otherRepo, "placeholder.md", "placeholder\n");
    }

    static boolean isGitCryptAvailable() {
        try {
            Process process = new ProcessBuilder("git-crypt", "--version").start();
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    private static void run(Path cwd, String... command) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(cwd.toFile());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        byte[] output = process.getInputStream().readAllBytes();
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished || process.exitValue() != 0) {
            throw new IllegalStateException(
                    "Command failed: " + List.of(command) + "\n" + new String(output));
        }
    }
}
