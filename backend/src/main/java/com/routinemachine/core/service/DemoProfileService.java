package com.routinemachine.core.service;

import com.routinemachine.core.domain.DailyLog;
import com.routinemachine.core.domain.Goal;
import com.routinemachine.core.domain.RoutineItem;
import com.routinemachine.core.domain.RoutineItemCategory;
import com.routinemachine.core.domain.RoutineItemSource;
import com.routinemachine.core.domain.ScheduleTemplate;
import com.routinemachine.core.domain.User;
import com.routinemachine.core.repository.DailyLogRepository;
import com.routinemachine.core.repository.GoalRepository;
import com.routinemachine.core.repository.RoutineItemRepository;
import com.routinemachine.core.repository.ScheduleTemplateRepository;
import com.routinemachine.core.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The opt-in demo profile (staged Prompt 6, docs/backend.md "Demo profile"): one clearly flagged account with
 * made-up sample data, for demonstrations only. Only reachable when DEMO_PROFILE_ENABLED is set; see AuthController.
 * The account has no password anyone knows, and its data belongs to it alone like any other account's.
 */
@Service
public class DemoProfileService {

    // ".invalid" is a reserved name that can never receive mail or belong to a real person.
    static final String DEMO_EMAIL = "demo@routine-machine.invalid";
    private static final String DEMO_ZONE = "Africa/Nairobi";
    private static final int DAYS_OF_HISTORY = 21;

    private final UserRepository userRepository;
    private final GoalRepository goalRepository;
    private final RoutineItemRepository routineItemRepository;
    private final ScheduleTemplateRepository scheduleTemplateRepository;
    private final DailyLogRepository dailyLogRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoProfileService(
            UserRepository userRepository, GoalRepository goalRepository, RoutineItemRepository routineItemRepository,
            ScheduleTemplateRepository scheduleTemplateRepository, DailyLogRepository dailyLogRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.goalRepository = goalRepository;
        this.routineItemRepository = routineItemRepository;
        this.scheduleTemplateRepository = scheduleTemplateRepository;
        this.dailyLogRepository = dailyLogRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // Safe to call every time: the account and its goals, routines and schedule are made once, and only the
    // recent days' logs that are missing get added, so the demo always has fresh history without duplicates.
    @Transactional
    public User open() {
        User demo = userRepository.findByEmailIgnoreCase(DEMO_EMAIL).orElseGet(this::createDemoUser);
        List<RoutineItem> items = routineItemRepository.findAllWithGoalByUserId(demo.getId());
        if (items.isEmpty()) {
            items = seedGoalsRoutinesAndSchedule(demo);
        }
        topUpRecentLogs(demo, items);
        return demo;
    }

    private User createDemoUser() {
        // A random password nobody sees, so a password sign-in can never succeed.
        User user = new User(DEMO_EMAIL, passwordEncoder.encode(UUID.randomUUID().toString()), null, "Sample person");
        user.markDemo();
        user.changeTimeZone(DEMO_ZONE);
        return userRepository.save(user);
    }

    private List<RoutineItem> seedGoalsRoutinesAndSchedule(User demo) {
        Goal fitness = goalRepository.save(new Goal("Stay fit (sample)", "Sample goal for the demo", demo));
        Goal learning = goalRepository.save(new Goal("Learn to code (sample)", "Sample goal for the demo", demo));

        List<RoutineItem> items = new ArrayList<>();
        items.add(item(demo, "Morning walk", RoutineItemCategory.BODY, fitness));
        items.add(item(demo, "Read ten pages", RoutineItemCategory.MIND, learning));
        items.add(item(demo, "Practise coding", RoutineItemCategory.WORK, learning));
        items.add(item(demo, "Tidy the kitchen", RoutineItemCategory.HOME, null));

        int[] minutes = {30, 15, 45};
        for (DayOfWeek day : DayOfWeek.values()) {
            for (int i = 0; i < minutes.length; i++) {
                ScheduleTemplate template = ScheduleTemplate.forRoutineItem(
                        day, items.get(i), minutes[i], i + 1, items.get(i).getName());
                template.assignUser(demo);
                scheduleTemplateRepository.save(template);
            }
        }
        return items;
    }

    private RoutineItem item(User demo, String name, RoutineItemCategory category, Goal goal) {
        RoutineItem item = new RoutineItem(
                name, "Sample routine for the demo", category, null, RoutineItemSource.MANUAL, null, true, goal);
        item.assignUser(demo);
        return routineItemRepository.save(item);
    }

    // A fixed, made-up pattern: most days show up, a few are missed, none is every day.
    private void topUpRecentLogs(User demo, List<RoutineItem> items) {
        LocalDate today = LocalDate.now(ZoneId.of(DEMO_ZONE));
        for (int daysAgo = 1; daysAgo <= DAYS_OF_HISTORY; daysAgo++) {
            LocalDate date = today.minusDays(daysAgo);
            for (int i = 0; i < 3; i++) {
                boolean tended = (daysAgo + i * 3) % 4 != 0;
                RoutineItem item = items.get(i);
                if (tended && !dailyLogRepository.existsByRoutineItemIdAndLogDate(item.getId(), date)) {
                    DailyLog log = DailyLog.forRoutineItem(date, item);
                    log.assignUser(demo);
                    dailyLogRepository.save(log);
                }
            }
        }
    }
}
