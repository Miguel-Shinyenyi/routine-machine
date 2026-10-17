package com.routinemachine.core.github;

import java.time.LocalDate;

public record GitHubCommitDailyTotal(LocalDate date, int commitCount) {
}
