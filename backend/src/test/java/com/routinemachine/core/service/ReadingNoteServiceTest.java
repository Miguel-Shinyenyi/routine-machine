package com.routinemachine.core.service;

import com.routinemachine.core.AbstractIntegrationTest;
import com.routinemachine.core.domain.Book;
import com.routinemachine.core.domain.DailyLogSource;
import com.routinemachine.core.domain.ReadingSource;
import com.routinemachine.core.domain.RoutineItem;
import com.routinemachine.core.domain.RoutineItemSource;
import com.routinemachine.core.domain.User;
import com.routinemachine.core.repository.BookRepository;
import com.routinemachine.core.repository.DailyLogRepository;
import com.routinemachine.core.repository.ReadingNoteRepository;
import com.routinemachine.core.repository.RoutineItemRepository;
import com.routinemachine.core.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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

    @Autowired
    private UserRepository userRepository;

    // Noon UTC on Sunday 25 October 2026: the same calendar day in every zone from UTC-11 to UTC+11. Tests that need a moment where the
    // zones disagree use at(...) below. No test here reads the real clock, so none can fail because of the hour it runs at.
    private static final LocalDate NOON_DAY = LocalDate.of(2026, 10, 25);

    private ReadingNoteService service() {
        return service(Instant.parse("2026-10-25T12:00:00Z"));
    }

    private ReadingNoteService service(Instant now) {
        return new ReadingNoteService(
                readingNoteRepository, bookRepository, routineItemRepository, dailyLogRepository,
                Clock.fixed(now, ZoneOffset.UTC));
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

        LocalDate today = NOON_DAY;
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

        assertThat(dailyLogRepository.findByLogDate(NOON_DAY)).isEmpty();
    }

    @Test
    void aNoteWithNoAssessmentAtAllDoesNotCreateADailyLog() {
        routineItemRepository.save(
                new RoutineItem("Reading", null, null, null, RoutineItemSource.READER_NOTES, null, true, null));
        Book book = saveBook();

        service().createNote(book.getId(), "Unassessed note.", null, null, null, null, null);

        assertThat(dailyLogRepository.findByLogDate(NOON_DAY)).isEmpty();
    }

    @Test
    void postingTwoQualifyingNotesTheSameDayDoesNotCreateASecondDailyLog() {
        routineItemRepository.save(
                new RoutineItem("Reading", null, null, null, RoutineItemSource.READER_NOTES, null, true, null));
        Book book = saveBook();
        var service = service();

        service.createNote(book.getId(), "First note.", null, ReadingNoteService.OWN_WORDS_THRESHOLD, List.of(), List.of(), null);
        service.createNote(book.getId(), "Second note, same day.", null, ReadingNoteService.OWN_WORDS_THRESHOLD, List.of(), List.of(), null);

        assertThat(dailyLogRepository.findByLogDate(NOON_DAY)).hasSize(1);
        assertThat(readingNoteRepository.findAll()).hasSize(2);
    }

    @Test
    void noReaderNotesRoutineItemMeansNoDailyLogButTheNoteStillSaves() {
        Book book = saveBook();

        service().createNote(book.getId(), "A real reflection.", null, ReadingNoteService.OWN_WORDS_THRESHOLD, List.of(), List.of(), null);

        assertThat(readingNoteRepository.findAll()).hasSize(1);
        assertThat(dailyLogRepository.findByLogDate(NOON_DAY)).isEmpty();
    }

    // Backlog P (b): the day a note counts for is the AUTHOR's day, in the author's saved time zone, not the server's. At 22:30 UTC on
    // Sunday 25 October it is already Monday 26 October in Nairobi, and still Sunday in UTC and in Los Angeles.
    private static final Instant LATE_SUNDAY_UTC = Instant.parse("2026-10-25T22:30:00Z");

    private LocalDate dayLoggedFor(String zone) {
        User author = new User(zone.replace('/', '-') + "@example.com", "irrelevant-hash", null, null);
        author.changeTimeZone(zone);
        author = userRepository.save(author);
        RoutineItem routineItem = new RoutineItem("Reading", null, null, null, RoutineItemSource.READER_NOTES, null, true, null);
        routineItem.assignUser(author);
        routineItemRepository.save(routineItem);
        Book book = new Book("A book", ReadingSource.MANUAL);
        book.assignUser(author);
        book = bookRepository.save(book);

        service(LATE_SUNDAY_UTC).createNote(
                book.getId(), author.getId(), "A real reflection in my own words.", null,
                ReadingNoteService.OWN_WORDS_THRESHOLD, List.of(), List.of(), null);

        var logs = dailyLogRepository.findByUserId(author.getId());
        assertThat(logs).hasSize(1);
        return logs.get(0).getLogDate();
    }

    @Test
    void aNoteWrittenLateSundayInUtcCountsForMondayInNairobi() {
        assertThat(dayLoggedFor("Africa/Nairobi")).isEqualTo(LocalDate.of(2026, 10, 26));
    }

    @Test
    void theSameNoteCountsForSundayForAUtcAuthorAndForALosAngelesAuthor() {
        assertThat(dayLoggedFor("UTC")).isEqualTo(LocalDate.of(2026, 10, 25));
        assertThat(dayLoggedFor("America/Los_Angeles")).isEqualTo(LocalDate.of(2026, 10, 25));
    }

    @Test
    void aBookWithNoOwnerFallsBackToTheUtcDay() {
        routineItemRepository.save(
                new RoutineItem("Reading", null, null, null, RoutineItemSource.READER_NOTES, null, true, null));
        Book book = saveBook();

        service(LATE_SUNDAY_UTC).createNote(
                book.getId(), "A real reflection.", null, ReadingNoteService.OWN_WORDS_THRESHOLD, List.of(), List.of(), null);

        assertThat(dailyLogRepository.findByLogDate(LocalDate.of(2026, 10, 25))).hasSize(1);
    }

    @Test
    void twoNotesInTheSamePersonalDayStillMakeOneLog_evenWhenTheUtcDayChangesBetweenThem() {
        User author = new User("nairobi-twice@example.com", "irrelevant-hash", null, null);
        author.changeTimeZone("Africa/Nairobi");
        author = userRepository.save(author);
        RoutineItem routineItem = new RoutineItem("Reading", null, null, null, RoutineItemSource.READER_NOTES, null, true, null);
        routineItem.assignUser(author);
        routineItemRepository.save(routineItem);
        Book book = new Book("A book", ReadingSource.MANUAL);
        book.assignUser(author);
        book = bookRepository.save(book);

        // 21:30 UTC and 23:30 UTC on Sunday are both Monday 00:30 and Monday 02:30 in Nairobi: one personal day, two UTC hours apart.
        service(Instant.parse("2026-10-25T21:30:00Z")).createNote(
                book.getId(), author.getId(), "First.", null, ReadingNoteService.OWN_WORDS_THRESHOLD, List.of(), List.of(), null);
        service(Instant.parse("2026-10-25T23:30:00Z")).createNote(
                book.getId(), author.getId(), "Second.", null, ReadingNoteService.OWN_WORDS_THRESHOLD, List.of(), List.of(), null);

        assertThat(dailyLogRepository.findByUserId(author.getId())).hasSize(1);
    }
}
