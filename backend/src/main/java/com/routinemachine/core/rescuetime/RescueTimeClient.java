package com.routinemachine.core.rescuetime;

import java.time.LocalDate;
import java.util.List;

public interface RescueTimeClient {

    // Inclusive of both start and end. A day with no tracked time may be omitted rather than
    // returned as a zero total.
    List<RescueTimeDailyTotal> fetchDailyActiveTotals(LocalDate start, LocalDate end);
}
