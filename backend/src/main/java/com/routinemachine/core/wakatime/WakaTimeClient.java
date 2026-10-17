package com.routinemachine.core.wakatime;

import java.time.LocalDate;
import java.util.List;

public interface WakaTimeClient {

    // Inclusive of both start and end. Days with no coding activity may be omitted rather
    // than returned as a zero total; callers should not assume one entry per calendar day.
    List<WakaTimeDailyTotal> fetchDailyTotals(LocalDate start, LocalDate end);
}
