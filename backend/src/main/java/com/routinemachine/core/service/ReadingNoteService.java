package com.routinemachine.core.service;

import com.routinemachine.core.domain.Book;
import com.routinemachine.core.domain.DailyLog;
import com.routinemachine.core.domain.DailyLogSource;
import com.routinemachine.core.domain.ReadingNote;
import com.routinemachine.core.domain.RoutineItem;
import com.routinemachine.core.domain.RoutineItemSource;
import com.routinemachine.core.domain.User;
import com.routinemachine.core.repository.BookRepository;
import com.routinemachine.core.repository.DailyLogRepository;
import com.routinemachine.core.repository.ReadingNoteRepository;
import com.routinemachine.core.repository.RoutineItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

// Coordinates ReadingNoteRepository and DailyLogRepository the same way TaskService
// coordinates TaskRepository and DailyLogRepository: saving a note is real business logic,
// not a data-layer passthrough, once it can also mark the day's reading done.
@Service
public class ReadingNoteService {

    // Named per PROJECT.md's Phase 7 description ("counted toward the reading goal under a
    // named ratio threshold"): a note whose own-words percentage is at least this counts as
    // real reflection, not mostly-copied text, and marks the day's reading routine item done.
    public static final int OWN_WORDS_THRESHOLD = 70;

    private final ReadingNoteRepository readingNoteRepository;
    private final BookRepository bookRepository;
    private final RoutineItemRepository routineItemRepository;
    private final DailyLogRepository dailyLogRepository;

    public ReadingNoteService(
            ReadingNoteRepository readingNoteRepository,
            BookRepository bookRepository,
            RoutineItemRepository routineItemRepository,
            DailyLogRepository dailyLogRepository) {
        this.readingNoteRepository = readingNoteRepository;
        this.bookRepository = bookRepository;
        this.routineItemRepository = routineItemRepository;
        this.dailyLogRepository = dailyLogRepository;
    }

    @Transactional
    public ReadingNote createNote(
            Long bookId, String noteText, String chapterPlaced, Integer ownWordsPercentage,
            List<String> ideasConnected, List<String> quotedPhrases, String questionBack) {
        return createNote(bookId, null, noteText, chapterPlaced, ownWordsPercentage, ideasConnected, quotedPhrases,
                questionBack);
    }

    // userId is who is writing (null = the legacy, userId-less view: unowned books only). Someone
    // else's book answers exactly like one that does not exist.
    @Transactional
    public ReadingNote createNote(
            Long bookId, Long userId, String noteText, String chapterPlaced, Integer ownWordsPercentage,
            List<String> ideasConnected, List<String> quotedPhrases, String questionBack) {
        Book book = bookRepository.findById(bookId)
                .filter(found -> Ownership.ownedBy(found.getUser(), userId))
                .orElseThrow(() -> new IllegalArgumentException("No book with id " + bookId));

        ReadingNote saved = readingNoteRepository.save(new ReadingNote(
                book, noteText, chapterPlaced, ownWordsPercentage, ideasConnected, quotedPhrases, questionBack));

        if (ownWordsPercentage != null && ownWordsPercentage >= OWN_WORDS_THRESHOLD) {
            markReadingDoneToday(userId, book.getUser());
        }

        return saved;
    }

    // Only the author's own reading routine counts as done, and the day is logged for the author:
    // this used to mark every account's reader-notes routine item.
    private void markReadingDoneToday(Long userId, User author) {
        LocalDate today = LocalDate.now();
        for (RoutineItem routineItem : routineItemRepository.findBySource(RoutineItemSource.READER_NOTES)) {
            if (!Ownership.ownedBy(routineItem.getUser(), userId)) {
                continue;
            }
            if (!dailyLogRepository.existsByRoutineItemIdAndLogDate(routineItem.getId(), today)) {
                DailyLog log = DailyLog.forRoutineItem(today, routineItem, DailyLogSource.READER_NOTES);
                log.assignUser(author);
                dailyLogRepository.save(log);
            }
        }
    }
}
