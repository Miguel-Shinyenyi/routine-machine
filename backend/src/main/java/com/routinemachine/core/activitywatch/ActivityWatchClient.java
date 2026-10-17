package com.routinemachine.core.activitywatch;

import java.time.LocalDate;
import java.util.List;

public interface ActivityWatchClient {

    // Inclusive of both start and end. One entry per day that had any not-afk time; a day
    // with none may be omitted rather than returned as a zero total.
    List<ActivityWatchDailyTotal> fetchDailyActiveTotals(LocalDate start, LocalDate end);
}
