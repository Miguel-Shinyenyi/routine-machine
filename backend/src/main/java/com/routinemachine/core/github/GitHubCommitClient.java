package com.routinemachine.core.github;

import java.time.LocalDate;
import java.util.List;

public interface GitHubCommitClient {

    // Inclusive of both start and end. A day with no commits may be omitted rather than
    // returned as a zero count. This is a backup signal, not a complete commit history: see
    // GitHubCommitClientImpl for its real source's own limits (recent public events only).
    List<GitHubCommitDailyTotal> fetchDailyCommitCounts(LocalDate start, LocalDate end);
}
