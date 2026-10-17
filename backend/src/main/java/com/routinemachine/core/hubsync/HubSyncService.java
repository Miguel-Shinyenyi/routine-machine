package com.routinemachine.core.hubsync;

import com.routinemachine.core.domain.Book;
import com.routinemachine.core.domain.DailyLog;
import com.routinemachine.core.domain.DailyLogSource;
import com.routinemachine.core.domain.HubSyncFileKind;
import com.routinemachine.core.domain.HubSyncProcessedFile;
import com.routinemachine.core.domain.ReadingSource;
import com.routinemachine.core.domain.RoutineItem;
import com.routinemachine.core.repository.BookRepository;
import com.routinemachine.core.repository.DailyLogRepository;
import com.routinemachine.core.repository.HubSyncProcessedFileRepository;
import com.routinemachine.core.repository.RoutineItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * hub-sync Phase 1: lists UMWAYI's journal entries and essays over the public GitHub API and
 * marks the matching routine item done for each new file's date, plus keeps {@code books}
 * (the "current book") in step with {@link HubJournalReader}'s "Current book:" line. Never
 * parses or stores file content beyond that one line (see {@code docs/hub-sync.md}).
 */
@Service
public class HubSyncService {

    private static final Logger log = LoggerFactory.getLogger(HubSyncService.class);

    static final String JOURNAL_ROUTINE_ITEM_NAME = "journaling";
    static final String WRITING_ROUTINE_ITEM_NAME = "writing";
    private static final String JOURNAL_DIRECTORY = "journal/entries";
    // The real UMWAYI repo has no top-level articles/ directory docs/hub-sync.md originally
    // assumed; these two are the closest real equivalent (essay-style writing), confirmed by
    // inspecting the repo directly. See the decisions log in docs/hub-sync.md.
    private static final List<String> ARTICLE_DIRECTORIES = List.of("site/philosophy", "site/tech");
    private static final Pattern JOURNAL_FILENAME_DATE = Pattern.compile(".*/(\\d{4}-\\d{2}-\\d{2})\\.md$");

    private final GitHubContentClient gitHubContentClient;
    private final HubJournalReader hubJournalReader;
    private final RoutineItemRepository routineItemRepository;
    private final DailyLogRepository dailyLogRepository;
    private final HubSyncProcessedFileRepository processedFileRepository;
    private final BookRepository bookRepository;
    private final boolean enabled;

    @Autowired
    public HubSyncService(
            GitHubContentClient gitHubContentClient,
            HubJournalReader hubJournalReader,
            RoutineItemRepository routineItemRepository,
            DailyLogRepository dailyLogRepository,
            HubSyncProcessedFileRepository processedFileRepository,
            BookRepository bookRepository,
            @Value("${hub-sync.enabled:true}") boolean enabled) {
        this.gitHubContentClient = gitHubContentClient;
        this.hubJournalReader = hubJournalReader;
        this.routineItemRepository = routineItemRepository;
        this.dailyLogRepository = dailyLogRepository;
        this.processedFileRepository = processedFileRepository;
        this.bookRepository = bookRepository;
        this.enabled = enabled;
    }

    // Package-visible, enabled defaulted true: lets tests call syncOnce() directly without
    // caring about the scheduler flag at all.
    HubSyncService(
            GitHubContentClient gitHubContentClient,
            HubJournalReader hubJournalReader,
            RoutineItemRepository routineItemRepository,
            DailyLogRepository dailyLogRepository,
            HubSyncProcessedFileRepository processedFileRepository,
            BookRepository bookRepository) {
        this(gitHubContentClient, hubJournalReader, routineItemRepository, dailyLogRepository,
                processedFileRepository, bookRepository, true);
    }

    // Default 15 minutes; override with HUB_SYNC_POLL_INTERVAL_MS. Polling (not a manual
    // trigger) answers docs/hub-sync.md's open question: this is "automatic counting" per
    // PROJECT.md's Phase 6 framing, and a personal single-user tool has no reason to make the
    // person remember to ask for a sync.
    @Scheduled(fixedDelayString = "${hub-sync.poll-interval-ms:900000}")
    public void scheduledSync() {
        if (!enabled) {
            return;
        }
        syncOnce();
    }

    public void syncOnce() {
        syncJournalEntries();
        syncArticles();
        syncCurrentBook();
    }

    private void syncJournalEntries() {
        for (GitHubFile file : gitHubContentClient.listDirectory(JOURNAL_DIRECTORY)) {
            if (processedFileRepository.existsByContentSha(file.sha())) {
                continue;
            }
            Matcher matcher = JOURNAL_FILENAME_DATE.matcher(file.path());
            if (!matcher.matches()) {
                log.info("hub-sync: skipping a journal file with an unrecognised name");
                continue;
            }
            recordAndMaybeLog(file, HubSyncFileKind.JOURNAL, LocalDate.parse(matcher.group(1)), JOURNAL_ROUTINE_ITEM_NAME);
        }
    }

    private void syncArticles() {
        for (String directory : ARTICLE_DIRECTORIES) {
            for (GitHubFile file : gitHubContentClient.listDirectory(directory)) {
                if (processedFileRepository.existsByContentSha(file.sha())) {
                    continue;
                }
                Optional<LocalDate> date = gitHubContentClient.firstCommitDate(file.path());
                if (date.isEmpty()) {
                    log.info("hub-sync: skipping an article with no discoverable commit date");
                    continue;
                }
                recordAndMaybeLog(file, HubSyncFileKind.ARTICLE, date.get(), WRITING_ROUTINE_ITEM_NAME);
            }
        }
    }

    private void recordAndMaybeLog(GitHubFile file, HubSyncFileKind kind, LocalDate date, String routineItemName) {
        Optional<RoutineItem> routineItem = routineItemRepository.findFirstByNameIgnoreCase(routineItemName);
        if (routineItem.isPresent()) {
            dailyLogRepository.save(DailyLog.forRoutineItem(date, routineItem.get(), DailyLogSource.HUB_SYNC));
        } else {
            log.info("hub-sync: no routine item named '{}', recording the file as seen but logging nothing",
                    routineItemName);
        }
        // Recorded either way: a routine item added later shouldn't cause every file hub-sync
        // already looked at to suddenly get logged at once.
        processedFileRepository.save(new HubSyncProcessedFile(file.path(), file.sha(), kind, date));
    }

    private void syncCurrentBook() {
        hubJournalReader.readCurrentBook().ifPresent(title -> {
            Optional<Book> current = bookRepository.findCurrent();
            if (current.isEmpty() || !current.get().getTitle().equals(title)) {
                bookRepository.save(new Book(title, ReadingSource.HUB_SYNC));
            }
        });
    }
}
