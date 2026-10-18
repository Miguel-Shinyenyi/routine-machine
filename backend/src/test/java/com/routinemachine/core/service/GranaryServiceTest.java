package com.routinemachine.core.service;

import com.routinemachine.core.AbstractIntegrationTest;
import com.routinemachine.core.domain.PauseDecision;
import com.routinemachine.core.domain.PauseOutcome;
import com.routinemachine.core.domain.User;
import com.routinemachine.core.repository.PauseDecisionRepository;
import com.routinemachine.core.repository.SavingRepository;
import com.routinemachine.core.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class GranaryServiceTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SavingRepository savingRepository;
    @Autowired
    private PauseDecisionRepository pauseDecisionRepository;

    private GranaryService service() {
        return new GranaryService(savingRepository, pauseDecisionRepository);
    }

    private User saveUser(String email) {
        User user = new User(email, "irrelevant-hash", null, null);
        user.changeSavingTargetAmount(50);
        return userRepository.save(user);
    }

    @Test
    void storingASavingTwiceTheSameDayDoesNotDuplicateIt() {
        User user = saveUser("saver@example.com");
        LocalDate today = LocalDate.of(2026, 10, 18);

        service().storeSaving(user, today);
        service().storeSaving(user, today);

        assertThat(savingRepository.findByUserIdAndLocalDateBetween(user.getId(), today, today)).hasSize(1);
    }

    @Test
    void aMissedDayIsNotCountedButDoesNotSubtractAnything() {
        User user = saveUser("missed@example.com");
        service().storeSaving(user, LocalDate.of(2026, 10, 1));
        // 10-2 missed
        service().storeSaving(user, LocalDate.of(2026, 10, 3));

        GranaryMonthSummary summary = service().monthSummary(user, YearMonth.of(2026, 10), LocalDate.of(2026, 10, 3));

        assertThat(summary.daysStored()).isEqualTo(2);
        assertThat(summary.storedThisMonth()).isEqualTo(100);
    }

    @Test
    void theMonthTargetIsTheDailyAmountTimesDaysElapsedSoFar() {
        User user = saveUser("target@example.com");

        GranaryMonthSummary summary = service().monthSummary(user, YearMonth.of(2026, 10), LocalDate.of(2026, 10, 18));

        assertThat(summary.targetThisMonth()).isEqualTo(50 * 18);
    }

    @Test
    void theJarHasOneEntryPerElapsedDayMarkingWhetherSomethingWasStored() {
        User user = saveUser("jar@example.com");
        service().storeSaving(user, LocalDate.of(2026, 10, 1));
        service().storeSaving(user, LocalDate.of(2026, 10, 3));

        GranaryMonthSummary summary = service().monthSummary(user, YearMonth.of(2026, 10), LocalDate.of(2026, 10, 3));

        assertThat(summary.jar()).containsExactly(true, false, true);
    }

    @Test
    void totalStoredEverIncludesSavingsFromOtherMonths() {
        User user = saveUser("ever@example.com");
        service().storeSaving(user, LocalDate.of(2026, 9, 15));
        service().storeSaving(user, LocalDate.of(2026, 10, 1));

        GranaryMonthSummary summary = service().monthSummary(user, YearMonth.of(2026, 10), LocalDate.of(2026, 10, 1));

        assertThat(summary.totalStoredEver()).isEqualTo(100);
    }

    @Test
    void creatingAPauseSetsBothFollowUpDates() {
        User user = saveUser("pause@example.com");
        Instant now = Instant.parse("2026-10-18T09:00:00Z");

        PauseDecision pause = service().createPause(user, "A token presale", "KSh 5,000", "Everyone says it will 10x", "WhatsApp group", now);

        assertThat(pause.getFollowUp24hAt()).isEqualTo(now.plus(24, ChronoUnit.HOURS));
        assertThat(pause.getFollowUp30dAt()).isEqualTo(now.plus(30, ChronoUnit.DAYS));
        assertThat(pause.getOutcome()).isNull();
    }

    @Test
    void aPauseIsDueForTheTwentyFourHourFollowUpOnlyOnceItsTimeHasPassedAndItsStillUndecided() {
        User user = saveUser("due-24h@example.com");
        Instant createdAt = Instant.parse("2026-10-17T09:00:00Z");
        PauseDecision pause = service().createPause(user, "Something", null, null, null, createdAt);

        assertThat(pause.isDueForTwentyFourHourFollowUp(Instant.parse("2026-10-17T20:00:00Z"))).isFalse();
        assertThat(pause.isDueForTwentyFourHourFollowUp(Instant.parse("2026-10-18T09:00:01Z"))).isTrue();

        service().decidePause(pause, PauseOutcome.DECLINED, Instant.parse("2026-10-18T09:00:01Z"));

        assertThat(pause.isDueForTwentyFourHourFollowUp(Instant.parse("2026-10-19T00:00:00Z"))).isFalse();
    }

    @Test
    void aPauseIsDueForTheThirtyDayFollowUpIndependentlyOfWhetherItWasDecided() {
        User user = saveUser("due-30d@example.com");
        Instant createdAt = Instant.parse("2026-09-18T09:00:00Z");
        PauseDecision pause = service().createPause(user, "Something else", null, null, null, createdAt);
        service().decidePause(pause, PauseOutcome.DECLINED, createdAt.plusSeconds(3600));

        assertThat(pause.isDueForThirtyDayFollowUp(Instant.parse("2026-10-10T00:00:00Z"))).isFalse();
        assertThat(pause.isDueForThirtyDayFollowUp(Instant.parse("2026-10-18T09:00:01Z"))).isTrue();

        service().reflectPause(pause, "Glad I waited.");

        assertThat(pause.isDueForThirtyDayFollowUp(Instant.parse("2026-11-01T00:00:00Z"))).isFalse();
    }

    @Test
    void pausesDueForFollowUpFindsOnlyThisUsersDueOnes() {
        User user = saveUser("due-list@example.com");
        User other = saveUser("other-due-list@example.com");
        Instant now = Instant.parse("2026-10-18T09:00:00Z");
        Instant longAgo = now.minus(40, ChronoUnit.DAYS);

        PauseDecision duePause = service().createPause(user, "Due", null, null, null, longAgo);
        service().createPause(user, "Not due", null, null, null, now);
        service().createPause(other, "Someone else's", null, null, null, longAgo);

        List<PauseDecision> due = service().pausesDueForFollowUp(user, now);

        assertThat(due).extracting(PauseDecision::getWhat).containsExactly("Due");
        assertThat(due).extracting(PauseDecision::getId).containsExactly(duePause.getId());
    }
}
