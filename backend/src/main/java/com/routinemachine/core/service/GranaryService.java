package com.routinemachine.core.service;

import com.routinemachine.core.domain.PauseDecision;
import com.routinemachine.core.domain.PauseOutcome;
import com.routinemachine.core.domain.Saving;
import com.routinemachine.core.domain.User;
import com.routinemachine.core.repository.PauseDecisionRepository;
import com.routinemachine.core.repository.SavingRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Phase 11: the granary (docs/product-direction.md). Never holds or moves money — every
// method here only records that a saving happened or a decision was made/reflected on.
@Service
public class GranaryService {

    private final SavingRepository savingRepository;
    private final PauseDecisionRepository pauseDecisionRepository;

    public GranaryService(SavingRepository savingRepository, PauseDecisionRepository pauseDecisionRepository) {
        this.savingRepository = savingRepository;
        this.pauseDecisionRepository = pauseDecisionRepository;
    }

    // Idempotent: tapping "store" twice the same day is one saving, not two — matches
    // "counted in days," not in taps.
    public Saving storeSaving(User user, LocalDate today) {
        return savingRepository.findByUserIdAndLocalDate(user.getId(), today)
                .orElseGet(() -> savingRepository.save(
                        new Saving(user, requireTargetAmount(user), today)));
    }

    private int requireTargetAmount(User user) {
        Integer amount = user.getSavingTargetAmount();
        if (amount == null) {
            throw new IllegalStateException("Set a daily saving amount before storing one");
        }
        return amount;
    }

    // "Stored this month" against "KSh 50 a day, every day you can" — the target is the daily
    // amount times days actually elapsed so far this month, not the whole month, so a target
    // on day 3 of October isn't already "31 days behind."
    public GranaryMonthSummary monthSummary(User user, YearMonth month, LocalDate today) {
        int elapsedDays = month.equals(YearMonth.from(today)) ? today.getDayOfMonth() : month.lengthOfMonth();
        LocalDate monthStart = month.atDay(1);
        LocalDate monthEnd = month.atEndOfMonth();

        List<Saving> thisMonth = savingRepository.findByUserIdAndLocalDateBetween(user.getId(), monthStart, monthEnd);
        Map<LocalDate, Integer> byDate = thisMonth.stream()
                .collect(Collectors.toMap(Saving::getLocalDate, Saving::getAmount));

        int storedThisMonth = byDate.values().stream().mapToInt(Integer::intValue).sum();
        Integer target = user.getSavingTargetAmount();
        int targetThisMonth = target == null ? 0 : target * elapsedDays;

        List<Boolean> jar = new ArrayList<>();
        for (int day = 1; day <= elapsedDays; day++) {
            jar.add(byDate.containsKey(monthStart.withDayOfMonth(day)));
        }

        int totalStoredEver = savingRepository.findByUserId(user.getId())
                .stream().mapToInt(Saving::getAmount).sum();

        return new GranaryMonthSummary(storedThisMonth, targetThisMonth, byDate.size(), elapsedDays, totalStoredEver, jar);
    }

    public PauseDecision createPause(User user, String what, String amount, String why, String heardFrom, Instant now) {
        return pauseDecisionRepository.save(new PauseDecision(user, what, amount, why, heardFrom, now));
    }

    public void decidePause(PauseDecision pause, PauseOutcome outcome, Instant now) {
        pause.decide(outcome, now);
        pauseDecisionRepository.save(pause);
    }

    public void reflectPause(PauseDecision pause, String reflection) {
        pause.reflect(reflection);
        pauseDecisionRepository.save(pause);
    }

    // What Today prompts the user about: any of their pauses due for either follow-up right
    // now — see docs/product-direction.md's "show them on Today."
    public List<PauseDecision> pausesDueForFollowUp(User user, Instant now) {
        return pauseDecisionRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .filter(p -> p.isDueForTwentyFourHourFollowUp(now) || p.isDueForThirtyDayFollowUp(now))
                .toList();
    }
}
