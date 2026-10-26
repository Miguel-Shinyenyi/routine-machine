package com.routinemachine.core.service;

import com.routinemachine.core.domain.FailureMode;
import com.routinemachine.core.domain.User;
import com.routinemachine.core.domain.WeeklyFailureMode;
import com.routinemachine.core.repository.UserRepository;
import com.routinemachine.core.repository.WeeklyFailureModeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.NoSuchElementException;
import java.util.Optional;

/** The weekly "Which failure mode did you meet this week?" answer (staged Prompt 7). A week starts on Monday, in the person's own zone. */
@Service
public class WeeklyFailureModeService {

    public record Current(LocalDate weekStart, FailureMode failureMode) {
        public boolean answered() {
            return failureMode != null;
        }
    }

    private final WeeklyFailureModeRepository repository;
    private final UserRepository userRepository;

    public WeeklyFailureModeService(WeeklyFailureModeRepository repository, UserRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
    }

    public static LocalDate weekStart(LocalDate day) {
        return day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    @Transactional(readOnly = true)
    public Current current(Long userId) {
        LocalDate week = thisWeek(findUser(userId));
        Optional<WeeklyFailureMode> answer = repository.findByUserIdAndWeekStart(userId, week);
        return new Current(week, answer.map(WeeklyFailureMode::getFailureMode).orElse(null));
    }

    // Saves the answer for this week, or changes it if there already is one: one row per person per week.
    @Transactional
    public Current answer(Long userId, FailureMode failureMode) {
        User user = findUser(userId);
        LocalDate week = thisWeek(user);
        repository.findByUserIdAndWeekStart(userId, week).ifPresentOrElse(
                existing -> existing.changeTo(failureMode),
                () -> repository.save(new WeeklyFailureMode(user, week, failureMode)));
        return new Current(week, failureMode);
    }

    private LocalDate thisWeek(User user) {
        return weekStart(LocalDate.now(ZoneId.of(user.getTimeZone())));
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new NoSuchElementException("No user with id " + userId));
    }
}
