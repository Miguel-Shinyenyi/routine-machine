package com.routinemachine.core.service;

import com.routinemachine.core.domain.Cohort;
import com.routinemachine.core.domain.CohortMembership;
import com.routinemachine.core.domain.CohortSurveyAnswer;
import com.routinemachine.core.domain.CohortSurveyResponse;
import com.routinemachine.core.domain.DailyLog;
import com.routinemachine.core.domain.Goal;
import com.routinemachine.core.repository.CohortMembershipRepository;
import com.routinemachine.core.repository.CohortRepository;
import com.routinemachine.core.repository.CohortSurveyResponseRepository;
import com.routinemachine.core.repository.DailyLogRepository;
import com.routinemachine.core.repository.GoalRepository;
import com.routinemachine.core.repository.OrganisationAdminRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.NoSuchElementException;

// Phase 12 part 3 (docs/product-direction.md's cohort dashboard): the four decided pilot
// measures, each with its own denominator — never a bare percentage. "Checked in" is any
// DailyLog, same thing Today already tracks for every user; it deliberately does not require
// hillside opt-in, since cohort participation and the hillside are separate concerns (see
// docs/backend.md's decisions log for why). Study groups, the weekly check-in chart, and the
// frontend are later parts.
@Service
public class CohortDashboardService {

    private final CohortRepository cohortRepository;
    private final CohortMembershipRepository cohortMembershipRepository;
    private final OrganisationAdminRepository organisationAdminRepository;
    private final GoalRepository goalRepository;
    private final DailyLogRepository dailyLogRepository;
    private final CohortSurveyResponseRepository cohortSurveyResponseRepository;

    public CohortDashboardService(
            CohortRepository cohortRepository,
            CohortMembershipRepository cohortMembershipRepository,
            OrganisationAdminRepository organisationAdminRepository,
            GoalRepository goalRepository,
            DailyLogRepository dailyLogRepository,
            CohortSurveyResponseRepository cohortSurveyResponseRepository) {
        this.cohortRepository = cohortRepository;
        this.cohortMembershipRepository = cohortMembershipRepository;
        this.organisationAdminRepository = organisationAdminRepository;
        this.goalRepository = goalRepository;
        this.dailyLogRepository = dailyLogRepository;
        this.cohortSurveyResponseRepository = cohortSurveyResponseRepository;
    }

    public CohortDashboardSummary summarize(Long cohortId, Long requestingUserId, LocalDate today) {
        Cohort cohort = findCohort(cohortId);
        requireParticipant(cohort, requestingUserId);

        List<CohortMembership> members = cohortMembershipRepository.findByCohortId(cohortId);
        List<CohortMembership> activated = members.stream().filter(m -> isActivated(m, today)).toList();

        int checkedInThisWeek = (int) activated.stream().filter(m -> checkedInCurrentWeek(cohort, m, today)).count();

        int missedADay = 0;
        int returnedWithinSevenDays = 0;
        for (CohortMembership member : activated) {
            ReturnClassification classification = classifyReturn(member, today);
            if (classification.missedADay()) {
                missedADay++;
                if (classification.returnedWithinSevenDays()) {
                    returnedWithinSevenDays++;
                }
            }
        }

        List<CohortSurveyResponse> responses = cohortSurveyResponseRepository.findByCohortId(cohortId);
        int supportive = (int) responses.stream().filter(r -> r.getAnswer() == CohortSurveyAnswer.SUPPORTIVE).count();
        int neutral = (int) responses.stream().filter(r -> r.getAnswer() == CohortSurveyAnswer.NEUTRAL).count();
        int pressuring = (int) responses.stream().filter(r -> r.getAnswer() == CohortSurveyAnswer.PRESSURING).count();

        return new CohortDashboardSummary(
                new CohortMeasure(activated.size(), members.size()),
                new CohortMeasure(checkedInThisWeek, activated.size()),
                new CohortMeasure(returnedWithinSevenDays, missedADay),
                new CohortMeasure(supportive, responses.size()),
                neutral,
                pressuring);
    }

    public CohortSurveyResponse submitSurveyResponse(Long cohortId, Long requestingUserId, CohortSurveyAnswer answer) {
        findCohort(cohortId);
        if (!cohortMembershipRepository.existsByCohortIdAndUserId(cohortId, requestingUserId)) {
            throw new NotACohortParticipantException(
                    "User " + requestingUserId + " is not a member of cohort " + cohortId);
        }
        return cohortSurveyResponseRepository.findByCohortIdAndUserId(cohortId, requestingUserId)
                .map(existing -> {
                    existing.changeAnswer(answer);
                    return cohortSurveyResponseRepository.save(existing);
                })
                .orElseGet(() -> cohortSurveyResponseRepository.save(new CohortSurveyResponse(cohortId, requestingUserId, answer)));
    }

    // "Set a goal and checked in once" within the first 7 days of joining
    // (docs/product-direction.md). Doesn't require the full 7-day window to have elapsed yet
    // — a member who does both on day one is activated immediately, not made to wait.
    private boolean isActivated(CohortMembership member, LocalDate today) {
        LocalDate joinedDate = member.getJoinedAt().toLocalDate();
        LocalDate windowEnd = joinedDate.plusDays(7);
        boolean hasGoal = goalRepository.findByUserId(member.getUserId()).stream()
                .anyMatch(goal -> withinWindow(goal.getCreatedAt().toLocalDate(), joinedDate, windowEnd));
        boolean hasLog = dailyLogRepository.findByUserId(member.getUserId()).stream()
                .anyMatch(log -> withinWindow(log.getLogDate(), joinedDate, windowEnd));
        return hasGoal && hasLog;
    }

    private boolean checkedInCurrentWeek(Cohort cohort, CohortMembership member, LocalDate today) {
        long daysSinceStart = Math.max(0, ChronoUnit.DAYS.between(cohort.getStartsOn(), today));
        LocalDate weekStart = cohort.getStartsOn().plusDays((daysSinceStart / 7) * 7);
        LocalDate weekEnd = weekStart.plusDays(6);
        return dailyLogRepository.findByUserId(member.getUserId()).stream()
                .anyMatch(log -> withinWindow(log.getLogDate(), weekStart, weekEnd));
    }

    // A first-cut definition, not the same algorithm as HillsideV2Service.countReturns (which
    // counts every return across a season from the hillside credit pipeline): here, "missed a
    // day" means this member's logged dates (since joining, up to today) have at least one gap
    // of a full un-logged day; "returned within seven days" means the next log after that gap
    // came within 7 days of it. Only the first gap is examined, for simplicity.
    private ReturnClassification classifyReturn(CohortMembership member, LocalDate today) {
        LocalDate joinedDate = member.getJoinedAt().toLocalDate();
        List<LocalDate> logDates = dailyLogRepository.findByUserId(member.getUserId()).stream()
                .map(DailyLog::getLogDate)
                .filter(date -> !date.isBefore(joinedDate) && !date.isAfter(today))
                .distinct()
                .sorted()
                .toList();

        for (int i = 0; i < logDates.size() - 1; i++) {
            LocalDate current = logDates.get(i);
            LocalDate next = logDates.get(i + 1);
            long gapDays = ChronoUnit.DAYS.between(current, next);
            if (gapDays > 1) {
                LocalDate missedDate = current.plusDays(1);
                boolean returnedWithinSeven = ChronoUnit.DAYS.between(missedDate, next) <= 7;
                return new ReturnClassification(true, returnedWithinSeven);
            }
        }
        return new ReturnClassification(false, false);
    }

    private record ReturnClassification(boolean missedADay, boolean returnedWithinSevenDays) {
    }

    private boolean withinWindow(LocalDate date, LocalDate start, LocalDate end) {
        return !date.isBefore(start) && !date.isAfter(end);
    }

    private Cohort findCohort(Long cohortId) {
        return cohortRepository.findById(cohortId)
                .orElseThrow(() -> new NoSuchElementException("No cohort with id " + cohortId));
    }

    private void requireParticipant(Cohort cohort, Long userId) {
        boolean isAdmin = organisationAdminRepository.existsByOrganisationIdAndUserId(cohort.getOrganisationId(), userId);
        boolean isMember = cohortMembershipRepository.existsByCohortIdAndUserId(cohort.getId(), userId);
        if (!isAdmin && !isMember) {
            throw new NotACohortParticipantException(
                    "User " + userId + " is neither an admin nor a member of cohort " + cohort.getId());
        }
    }
}
