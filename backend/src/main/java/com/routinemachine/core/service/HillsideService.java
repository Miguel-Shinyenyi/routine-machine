package com.routinemachine.core.service;

import com.routinemachine.core.domain.DailyLog;
import com.routinemachine.core.domain.HillsideField;
import com.routinemachine.core.repository.DailyLogRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Phase 8: the hillside's aggregation, computed fresh on every call from daily_logs/routine_items
// directly, not a stored leaderboard. Deliberately simplified groundwork — see
// docs/backend.md's decisions log for what this does and doesn't do yet (no real opt-in
// concept, no auth-derived "current user," asUserId is a demo-mode parameter).
@Service
public class HillsideService {

    // Matches intelligence-service/tally.py's TALLY_STICK_THRESHOLD exactly; duplicated, not
    // shared, since the two live in different languages/services. If one changes, the other
    // must change with it — see docs/backend.md's decisions log.
    static final int GENERAL_THRESHOLD = 3;

    private final DailyLogRepository dailyLogRepository;

    public HillsideService(DailyLogRepository dailyLogRepository) {
        this.dailyLogRepository = dailyLogRepository;
    }

    public HillsideResult compute(HillsideField field, int windowDays, Long asUserId, LocalDate today) {
        LocalDate start = today.minusDays(windowDays - 1L);
        List<DailyLog> logs = dailyLogRepository.findForHillside(start, today);

        // userId -> set of qualifying dates. "General" (field == null) needs every log that
        // day to decide whether 3+ distinct routines were tended; a specific field only needs
        // logs whose routine item carries that field.
        Map<Long, Map<LocalDate, Set<Long>>> routinesPerUserPerDay = new HashMap<>();
        for (DailyLog log : logs) {
            Long userId = log.getUser().getId();
            if (field != null && log.getRoutineItem().getHillsideField() != field) {
                continue;
            }
            routinesPerUserPerDay
                    .computeIfAbsent(userId, k -> new HashMap<>())
                    .computeIfAbsent(log.getLogDate(), k -> new HashSet<>())
                    .add(log.getRoutineItem().getId());
        }

        Map<Long, Integer> daysShownUpByUser = new HashMap<>();
        for (Map.Entry<Long, Map<LocalDate, Set<Long>>> userEntry : routinesPerUserPerDay.entrySet()) {
            int days = 0;
            for (Set<Long> routinesThatDay : userEntry.getValue().values()) {
                if (field == null) {
                    if (routinesThatDay.size() >= GENERAL_THRESHOLD) {
                        days++;
                    }
                } else {
                    days++;
                }
            }
            if (days > 0) {
                daysShownUpByUser.put(userEntry.getKey(), days);
            }
        }

        List<Map.Entry<Long, Integer>> ranked = new ArrayList<>(daysShownUpByUser.entrySet());
        ranked.sort(Comparator
                .<Map.Entry<Long, Integer>>comparingInt(e -> -e.getValue())
                .thenComparing(Map.Entry::getKey));

        int totalParticipants = ranked.size();
        int[] histogramArray = new int[windowDays + 1];
        for (int days : daysShownUpByUser.values()) {
            histogramArray[Math.min(days, windowDays)]++;
        }
        List<Integer> histogram = new ArrayList<>();
        for (int count : histogramArray) {
            histogram.add(count);
        }

        if (asUserId == null || !daysShownUpByUser.containsKey(asUserId)) {
            return new HillsideResult(totalParticipants, histogram, null, null, null, List.of());
        }

        int yourIndex = -1;
        for (int i = 0; i < ranked.size(); i++) {
            if (ranked.get(i).getKey().equals(asUserId)) {
                yourIndex = i;
                break;
            }
        }
        int yourRank = yourIndex + 1;
        int yourDaysShownUp = daysShownUpByUser.get(asUserId);
        double percentAhead = totalParticipants <= 1 ? 100.0 : (1.0 - (double) yourRank / totalParticipants) * 100.0;
        String band = percentAhead >= 66
                ? "Near the front of the flock."
                : percentAhead >= 33 ? "In the middle of the flock." : "Walking at the back, and still walking.";

        List<NeighborRow> neighbors = new ArrayList<>();
        int from = Math.max(0, yourIndex - 3);
        int to = Math.min(ranked.size() - 1, yourIndex + 3);
        for (int i = from; i <= to; i++) {
            Map.Entry<Long, Integer> entry = ranked.get(i);
            neighbors.add(new NeighborRow(
                    HillsidePseudonym.forUserId(entry.getKey()), entry.getValue(), entry.getKey().equals(asUserId)));
        }

        return new HillsideResult(totalParticipants, histogram, yourDaysShownUp, yourRank, band, neighbors);
    }
}
