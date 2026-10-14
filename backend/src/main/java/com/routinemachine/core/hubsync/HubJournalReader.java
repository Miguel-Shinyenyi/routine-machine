package com.routinemachine.core.hubsync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stage 1 of hub-sync's journal reading: given UMWAYI_JOURNAL_KEY_B64, unlocks a short-lived
 * shallow clone of the UMWAYI journal with git-crypt and extracts the "Current book:" line, if
 * any. Not wired to any scheduler yet; later hub-sync stages call {@link #readCurrentBook()}.
 *
 * <p>Entry content is never persisted and never logged. The key never appears in a log line.
 */
@Service
public class HubJournalReader {

    private static final Logger log = LoggerFactory.getLogger(HubJournalReader.class);

    static final String KEY_ENV_VAR = "UMWAYI_JOURNAL_KEY_B64";
    private static final String UMWAYI_REPO_URL = "https://github.com/Miguel-Shinyenyi/UMWAYI.git";

    private static final byte[] GIT_CRYPT_HEADER =
            {0x00, 'G', 'I', 'T', 'C', 'R', 'Y', 'P', 'T'};

    private static final Pattern CURRENT_BOOK_PATTERN =
            Pattern.compile("(?i)^\\s*-?\\s*Current book:\\s*(.+?)\\s*$", Pattern.MULTILINE);

    private static final java.time.Duration PROCESS_TIMEOUT = java.time.Duration.ofSeconds(60);

    private final String repoUrl;
    private final Supplier<String> keySupplier;

    public HubJournalReader() {
        this(UMWAYI_REPO_URL);
    }

    // Package-visible: lets tests point the clone at a throwaway local repo instead of the
    // real UMWAYI remote.
    HubJournalReader(String repoUrl) {
        this(repoUrl, () -> System.getenv(KEY_ENV_VAR));
    }

    // Package-visible: lets tests supply a throwaway key without touching the real
    // environment variable.
    HubJournalReader(String repoUrl, Supplier<String> keySupplier) {
        this.repoUrl = repoUrl;
        this.keySupplier = keySupplier;
    }

    /**
     * Returns the title from the most recent "Current book:" line found across the journal's
     * entries, or empty if the key isn't configured, the clone or unlock fails, or no entry
     * declares one.
     */
    public Optional<String> readCurrentBook() {
        String keyBase64 = keySupplier.get();
        if (keyBase64 == null || keyBase64.isBlank()) {
            log.info("hub-sync journal reading disabled: {} not set", KEY_ENV_VAR);
            return Optional.empty();
        }

        Path tempDir = null;
        Path keyFile = null;
        try {
            tempDir = createOwnerOnlyTempDir();
            keyFile = writeKeyFile(tempDir, keyBase64);

            Path cloneDir = tempDir.resolve("clone");
            if (!cloneRepo(cloneDir)) {
                log.info("hub-sync journal reading failed: clone failed");
                return Optional.empty();
            }
            if (!unlock(cloneDir, keyFile)) {
                log.info("hub-sync journal reading: journal locked, unlock failed");
                return Optional.empty();
            }

            return extractCurrentBook(cloneDir);
        } catch (IOException e) {
            log.info("hub-sync journal reading failed: {}", e.getClass().getSimpleName());
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("hub-sync journal reading interrupted");
            return Optional.empty();
        } finally {
            deleteKeyFile(keyFile);
            deleteTempDir(tempDir);
        }
    }

    private Path createOwnerOnlyTempDir() throws IOException {
        FileAttribute<Set<PosixFilePermission>> attr =
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"));
        return Files.createTempDirectory("hub-journal-", attr);
    }

    private Path writeKeyFile(Path tempDir, String keyBase64) throws IOException {
        byte[] keyBytes = Base64.getDecoder().decode(keyBase64.strip());
        Path keyFile = tempDir.resolve("umwayi.key");
        Files.write(keyFile, keyBytes);
        Files.setPosixFilePermissions(keyFile, PosixFilePermissions.fromString("rw-------"));
        return keyFile;
    }

    private boolean cloneRepo(Path cloneDir) throws IOException, InterruptedException {
        return runProcess(List.of("git", "clone", "--depth", "1", repoUrl, cloneDir.toString()), null);
    }

    private boolean unlock(Path cloneDir, Path keyFile) throws IOException, InterruptedException {
        return runProcess(List.of("git-crypt", "unlock", keyFile.toString()), cloneDir);
    }

    private boolean runProcess(List<String> command, Path workingDir) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        if (workingDir != null) {
            builder.directory(workingDir.toFile());
        }
        builder.redirectErrorStream(true);
        Process process = builder.start();
        // Drain without logging: git/git-crypt output could echo the key file path or clone
        // contents, neither of which belongs in application logs.
        process.getInputStream().readAllBytes();
        boolean finished = process.waitFor(PROCESS_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            return false;
        }
        return process.exitValue() == 0;
    }

    private Optional<String> extractCurrentBook(Path cloneDir) throws IOException {
        Path entriesDir = cloneDir.resolve("journal").resolve("entries");
        if (!Files.isDirectory(entriesDir)) {
            return Optional.empty();
        }

        List<Path> entries;
        try (var stream = Files.list(entriesDir)) {
            entries = stream.filter(p -> p.toString().endsWith(".md")).sorted().toList();
        }

        // Newest entry wins: a later entry's line supersedes an earlier one.
        for (int i = entries.size() - 1; i >= 0; i--) {
            Optional<String> title = readCurrentBookFromFile(entries.get(i));
            if (title.isPresent()) {
                return title;
            }
        }
        return Optional.empty();
    }

    private Optional<String> readCurrentBookFromFile(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        if (looksLikeGitCryptCiphertext(bytes)) {
            // Still locked despite a reported-successful unlock (shouldn't happen, but never
            // treat ciphertext as content either way).
            return Optional.empty();
        }

        String content = new String(bytes, StandardCharsets.UTF_8);
        Matcher matcher = CURRENT_BOOK_PATTERN.matcher(content);
        if (matcher.find()) {
            String title = matcher.group(1).trim();
            if (!title.isEmpty()) {
                log.info("hub-sync: found a Current book line");
                return Optional.of(title);
            }
        }
        return Optional.empty();
    }

    private boolean looksLikeGitCryptCiphertext(byte[] bytes) {
        if (bytes.length < GIT_CRYPT_HEADER.length) {
            return false;
        }
        for (int i = 0; i < GIT_CRYPT_HEADER.length; i++) {
            if (bytes[i] != GIT_CRYPT_HEADER[i]) {
                return false;
            }
        }
        return true;
    }

    private void deleteKeyFile(Path keyFile) {
        if (keyFile == null) {
            return;
        }
        try {
            if (Files.exists(keyFile)) {
                long size = Files.size(keyFile);
                Files.write(keyFile, new byte[(int) size]);
                Files.delete(keyFile);
            }
        } catch (IOException e) {
            log.info("hub-sync: failed to delete key file");
        }
    }

    private void deleteTempDir(Path tempDir) {
        if (tempDir == null) {
            return;
        }
        try (var stream = Files.walk(tempDir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // Best-effort: the directory may already be gone.
                }
            });
        } catch (IOException e) {
            log.info("hub-sync: failed to fully delete temp directory");
        }
    }
}
