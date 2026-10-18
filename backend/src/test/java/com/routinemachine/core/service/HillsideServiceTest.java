package com.routinemachine.core.service;

import com.routinemachine.core.AbstractIntegrationTest;
import com.routinemachine.core.domain.Book;
import com.routinemachine.core.domain.DailyLog;
import com.routinemachine.core.domain.HillsideField;
import com.routinemachine.core.domain.ReadingSource;
import com.routinemachine.core.domain.RoutineItem;
import com.routinemachine.core.domain.RoutineItemSource;
import com.routinemachine.core.domain.User;
import com.routinemachine.core.repository.DailyLogRepository;
import com.routinemachine.core.repository.RoutineItemRepository;
import com.routinemachine.core.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class HillsideServiceTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoutineItemRepository routineItemRepository;

    @Autowired
    private DailyLogRepository dailyLogRepository;

    private HillsideService service() {
        return new HillsideService(dailyLogRepository);
    }

    private User saveUser(String email) {
        User user = new User(email, "irrelevant-hash", null, null);
        user.optIntoHillside();
        return userRepository.save(user);
    }

    private User saveUserNotOptedIn(String email) {
        return userRepository.save(new User(email, "irrelevant-hash", null, null));
    }

    private RoutineItem savePublicReadingItem(String name) {
        return routineItemRepository.save(new RoutineItem(
                name, null, null, null, RoutineItemSource.MANUAL, HillsideField.READING, false, null));
    }

    private void log(LocalDate date, RoutineItem item, User user) {
        DailyLog entry = DailyLog.forRoutineItem(date, item);
        entry.assignUser(user);
        dailyLogRepository.save(entry);
    }

    @Test
    void ranksParticipantsByDaysShownUpOnTheChosenField() {
        LocalDate today = LocalDate.of(2026, 10, 17);
        User you = saveUser("you@example.com");
        User rival = saveUser("rival@example.com");
        RoutineItem item = savePublicReadingItem("Reading");

        // You: 1 day shown up. Rival: 3 days shown up, so rival outranks you.
        log(today, item, you);
        log(today, item, rival);
        log(today.minusDays(1), item, rival);
        log(today.minusDays(2), item, rival);

        HillsideResult result = service().compute(HillsideField.READING, 7, you.getId(), today);

        assertThat(result.totalParticipants()).isEqualTo(2);
        assertThat(result.yourDaysShownUp()).isEqualTo(1);
        assertThat(result.yourRank()).isEqualTo(2);
        assertThat(result.band()).isEqualTo("Walking at the back, and still walking.");
        assertThat(result.neighbors()).extracting(NeighborRow::pseudonym)
                .contains(HillsidePseudonym.forUserId(you.getId()), HillsidePseudonym.forUserId(rival.getId()));
        // Rival tended more days, so ranks 1st; you rank 2nd — rank is the row's 1-indexed
        // position in the full ranked list, not its position within this neighbours slice.
        assertThat(result.neighbors()).extracting(NeighborRow::rank)
                .contains(1, 2);
    }

    @Test
    void withNoAsUserIdReturnsTheDistributionButNoPersonalBand() {
        LocalDate today = LocalDate.of(2026, 10, 17);
        User person = saveUser("person@example.com");
        RoutineItem item = savePublicReadingItem("Reading");
        log(today, item, person);

        HillsideResult result = service().compute(HillsideField.READING, 7, null, today);

        assertThat(result.totalParticipants()).isEqualTo(1);
        assertThat(result.yourDaysShownUp()).isNull();
        assertThat(result.yourRank()).isNull();
        assertThat(result.neighbors()).isEmpty();
    }

    @Test
    void aPrivateRoutineItemNeverReachesTheHillside() {
        LocalDate today = LocalDate.of(2026, 10, 17);
        User person = saveUser("private-person@example.com");
        RoutineItem privateItem = routineItemRepository.save(new RoutineItem(
                "Private reading", null, null, null, RoutineItemSource.MANUAL, HillsideField.READING, true, null));
        log(today, privateItem, person);

        HillsideResult result = service().compute(HillsideField.READING, 7, person.getId(), today);

        assertThat(result.totalParticipants()).isEqualTo(0);
        assertThat(result.yourDaysShownUp()).isNull();
    }

    @Test
    void generalFieldCountsADayOnlyWhenAtLeastThreeDistinctRoutinesWereTendedThatDay() {
        LocalDate today = LocalDate.of(2026, 10, 17);
        User you = saveUser("general@example.com");
        RoutineItem a = savePublicReadingItem("A");
        RoutineItem b = savePublicReadingItem("B");
        RoutineItem c = savePublicReadingItem("C");

        // Today: only 2 distinct routines tended, below the threshold.
        log(today, a, you);
        log(today, b, you);
        // Yesterday: 3 distinct routines tended, clears the threshold.
        log(today.minusDays(1), a, you);
        log(today.minusDays(1), b, you);
        log(today.minusDays(1), c, you);

        HillsideResult result = service().compute(null, 7, you.getId(), today);

        assertThat(result.yourDaysShownUp()).isEqualTo(1);
    }

    @Test
    void aUserWhoHasNotOptedIntoTheHillsideNeverReachesIt() {
        LocalDate today = LocalDate.of(2026, 10, 17);
        User notOptedIn = saveUserNotOptedIn("not-opted-in@example.com");
        RoutineItem item = savePublicReadingItem("Reading");
        log(today, item, notOptedIn);

        HillsideResult result = service().compute(HillsideField.READING, 7, notOptedIn.getId(), today);

        assertThat(result.totalParticipants()).isEqualTo(0);
        assertThat(result.yourDaysShownUp()).isNull();
    }
}
