package com.routinemachine.core.service;

import com.routinemachine.core.AbstractIntegrationTest;
import com.routinemachine.core.domain.Book;
import com.routinemachine.core.domain.DailyLogSource;
import com.routinemachine.core.domain.ReadingSource;
import com.routinemachine.core.domain.RoutineItem;
import com.routinemachine.core.domain.RoutineItemSource;
import com.routinemachine.core.repository.BookRepository;
import com.routinemachine.core.repository.DailyLogRepository;
import com.routinemachine.core.repository.ReadingNoteRepository;
import com.routinemachine.core.repository.RoutineItemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ReadingNoteServiceTest extends AbstractIntegrationTest {

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private ReadingNoteRepository readingNoteRepository;

    @Autowired
    private RoutineItemRepository routineItemRepository;

    @Autowired
    private DailyLogRepository dailyLogRepository;

    private ReadingNoteService service() {
        return new ReadingNoteService(readingNoteRepository, bookRepository, routineItemRepository, dailyLogRepository);
    }

    private Book saveBook() {
        return bookRepository.save(new Book("Atomic Habits", ReadingSource.MANUAL));
    }

    @Test
    void aNoteAtOrAboveTheThresholdCreatesADailyLogForTheReaderNotesRoutineItem() {
        RoutineItem routineItem = routineItemRepository.save(
                new RoutineItem("Reading", null, null, null, RoutineItemSource.READER_NOTES, null, true, null));
        Book book = saveBook();

        service().createNote(
                book.getId(), "A real reflection in my own words.", null,
                ReadingNoteService.OWN_WORDS_THRESHOLD, List.of(), List.of(), null);

        LocalDate today = LocalDate.now();
        assertThat(dailyLogRepository.existsByRoutineItemIdAndLogDate(routineItem.getId(), today)).isTrue();
        assertThat(dailyLogRepository.findByLogDate(today).get(0).getSource()).isEqualTo(DailyLogSource.READER_NOTES);
    }

    @Test
    void aNoteBelowTheThresholdDoesNotCreateADailyLog() {
        routineItemRepository.save(
                new RoutineItem("Reading", null, null, null, RoutineItemSource.READER_NOTES, null, true, null));
        Book book = saveBook();

        service().createNote(
                book.getId(), "Mostly copied text.", null,
                ReadingNoteService.OWN_WORDS_THRESHOLD - 1, List.of(), List.of(), null);

        assertThat(dailyLogRepository.findByLogDate(LocalDate.now())).isEmpty();
    }

    @Test
    void aNoteWithNoAssessmentAtAllDoesNotCreateADailyLog() {
        routineItemRepository.save(
                new RoutineItem("Reading", null, null, null, RoutineItemSource.READER_NOTES, null, true, null));
        Book book = saveBook();

        service().createNote(book.getId(), "Unassessed note.", null, null, null, null, null);

        assertThat(dailyLogRepository.findByLogDate(LocalDate.now())).isEmpty();
    }

    @Test
    void postingTwoQualifyingNotesTheSameDayDoesNotCreateASecondDailyLog() {
        routineItemRepository.save(
                new RoutineItem("Reading", null, null, null, RoutineItemSource.READER_NOTES, null, true, null));
        Book book = saveBook();
        var service = service();

        service.createNote(book.getId(), "First note.", null, ReadingNoteService.OWN_WORDS_THRESHOLD, List.of(), List.of(), null);
        service.createNote(book.getId(), "Second note, same day.", null, ReadingNoteService.OWN_WORDS_THRESHOLD, List.of(), List.of(), null);

        assertThat(dailyLogRepository.findByLogDate(LocalDate.now())).hasSize(1);
        assertThat(readingNoteRepository.findAll()).hasSize(2);
    }

    @Test
    void noReaderNotesRoutineItemMeansNoDailyLogButTheNoteStillSaves() {
        Book book = saveBook();

        service().createNote(book.getId(), "A real reflection.", null, ReadingNoteService.OWN_WORDS_THRESHOLD, List.of(), List.of(), null);

        assertThat(readingNoteRepository.findAll()).hasSize(1);
        assertThat(dailyLogRepository.findByLogDate(LocalDate.now())).isEmpty();
    }
}
