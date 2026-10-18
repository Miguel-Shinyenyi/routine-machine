package com.routinemachine.core.service;

import com.routinemachine.core.domain.DailyCredit;
import com.routinemachine.core.domain.User;
import com.routinemachine.core.repository.DailyCreditRepository;
import com.routinemachine.core.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Phase 9: season-score aggregation and ranking over daily_credits — the "aggregate" and
// "publish" steps of docs/product-direction.md's evidence-to-credit pipeline. A season score
// is always recomputed from daily_credits, never cached as the record of truth. "Cohort" and
// "open" both resolve to the same opted-in population until Phase 12 builds real cohorts —
// see docs/backend.md's decisions log.
@Service
public class HillsideV2Service {

    // "One credit per qualifying goal per local calendar day, with a daily cap (3 in the
    // design)." The cap applies across goals for one day, not per goal — a goal can only ever
    // contribute its own single credit (daily_credits' UNIQUE constraint), but a day with 4
    // qualifying goals still only contributes 3 to the season score.
    static final int DAILY_CREDIT_CAP = 3;

    private final DailyCreditRepository dailyCreditRepository;
    private final UserRepository userRepository;

    public HillsideV2Service(DailyCreditRepository dailyCreditRepository, UserRepository userRepository) {
        this.dailyCreditRepository = dailyCreditRepository;
        this.userRepository = userRepository;
    }

    public int seasonScore(Long userId, LocalDate seasonStart, LocalDate seasonEnd) {
        return seasonScore(userId, seasonStart, seasonEnd, null);
    }

    public int seasonScore(Long userId, LocalDate seasonStart, LocalDate seasonEnd, Long goalId) {
        int total = 0;
        for (int dayTotal : perDayTotals(userId, seasonStart, seasonEnd, goalId).values()) {
            total += Math.min(dayTotal, DAILY_CREDIT_CAP);
        }
        return total;
    }

    public int daysShownUp(Long userId, LocalDate seasonStart, LocalDate seasonEnd) {
        return perDayTotals(userId, seasonStart, seasonEnd, null).size();
    }

    // A "return": a day in this season with a credit, whose immediately preceding calendar
    // day had none, excluding a user's very first-ever credited day (a start, not a comeback).
    // Looks past the season's own boundary for the "day before" check and the first-ever-day
    // check, since a break that started before this season still counts once the user comes
    // back inside it.
    public int countReturns(Long userId, LocalDate seasonStart, LocalDate seasonEnd) {
        List<LocalDate> creditedDaysInSeason = perDayTotals(userId, seasonStart, seasonEnd, null).keySet()
                .stream().sorted().toList();

        int returns = 0;
        for (LocalDate day : creditedDaysInSeason) {
            boolean previousDayHadCredit = dailyCreditRepository.existsByUserIdAndLocalDate(userId, day.minusDays(1));
            if (previousDayHadCredit) {
                continue;
            }
            boolean isFirstEverCreditedDay = !dailyCreditRepository.existsByUserIdAndLocalDateBefore(userId, day);
            if (!isFirstEverCreditedDay) {
                returns++;
            }
        }
        return returns;
    }

    // The hillside v2 ranking: every opted-in user with a nonzero season score, ranked by
    // score (ties broken by user id for determinism, same as HillsideService's v1 ranking).
    public HillsideV2Result compute(Long goalId, LocalDate seasonStart, LocalDate seasonEnd, Long asUserId) {
        List<User> optedIn = userRepository.findByHillsideOptInTrue();

        Map<Long, Integer> scoreByUser = new HashMap<>();
        Map<Long, Integer> daysByUser = new HashMap<>();
        Map<Long, Integer> goalCountByUser = new HashMap<>();
        int cohortReturns = 0;
        for (User user : optedIn) {
            int score = seasonScore(user.getId(), seasonStart, seasonEnd, goalId);
            if (score > 0) {
                scoreByUser.put(user.getId(), score);
                daysByUser.put(user.getId(), perDayTotals(user.getId(), seasonStart, seasonEnd, goalId).size());
                goalCountByUser.put(user.getId(), distinctGoalCount(user.getId(), seasonStart, seasonEnd));
            }
            if (countReturns(user.getId(), seasonStart, seasonEnd) > 0) {
                cohortReturns++;
            }
        }

        List<Map.Entry<Long, Integer>> ranked = new ArrayList<>(scoreByUser.entrySet());
        ranked.sort(Comparator
                .<Map.Entry<Long, Integer>>comparingInt(e -> -e.getValue())
                .thenComparing(Map.Entry::getKey));

        int totalParticipants = ranked.size();

        if (asUserId == null || !scoreByUser.containsKey(asUserId)) {
            return new HillsideV2Result(totalParticipants, null, null, null, null, null, cohortReturns, List.of());
        }

        int yourIndex = -1;
        for (int i = 0; i < ranked.size(); i++) {
            if (ranked.get(i).getKey().equals(asUserId)) {
                yourIndex = i;
                break;
            }
        }
        int yourRank = yourIndex + 1;
        int yourScore = scoreByUser.get(asUserId);
        double percentAhead = totalParticipants <= 1 ? 100.0 : (1.0 - (double) yourRank / totalParticipants) * 100.0;
        String band = percentAhead >= 66
                ? "Near the front of the cohort."
                : percentAhead >= 33 ? "In the middle of the cohort." : "Walking at the back, and still walking.";

        List<NeighborCreditRow> neighbors = new ArrayList<>();
        int from = Math.max(0, yourIndex - 3);
        int to = Math.min(ranked.size() - 1, yourIndex + 3);
        for (int i = from; i <= to; i++) {
            Map.Entry<Long, Integer> entry = ranked.get(i);
            neighbors.add(new NeighborCreditRow(
                    i + 1, HillsidePseudonym.forUserId(entry.getKey(), seasonStart), entry.getValue(),
                    daysByUser.get(entry.getKey()), entry.getKey().equals(asUserId)));
        }

        return new HillsideV2Result(
                totalParticipants, yourScore, daysByUser.get(asUserId), yourRank, goalCountByUser.get(asUserId),
                band, cohortReturns, neighbors);
    }

    private int distinctGoalCount(Long userId, LocalDate seasonStart, LocalDate seasonEnd) {
        Set<Long> goalIds = new HashSet<>();
        for (DailyCredit credit : dailyCreditRepository.findByUserIdAndLocalDateBetween(userId, seasonStart, seasonEnd)) {
            goalIds.add(credit.getGoal().getId());
        }
        return goalIds.size();
    }

    private Map<LocalDate, Integer> perDayTotals(Long userId, LocalDate seasonStart, LocalDate seasonEnd, Long goalId) {
        List<DailyCredit> rows = goalId == null
                ? dailyCreditRepository.findByUserIdAndLocalDateBetween(userId, seasonStart, seasonEnd)
                : dailyCreditRepository.findByUserIdAndGoalIdAndLocalDateBetween(userId, goalId, seasonStart, seasonEnd);
        Map<LocalDate, Integer> perDay = new HashMap<>();
        for (DailyCredit credit : rows) {
            perDay.merge(credit.getLocalDate(), (int) credit.getCredits(), Integer::sum);
        }
        return perDay;
    }
}
