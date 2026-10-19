package com.routinemachine.core.api;

import com.routinemachine.core.domain.Cohort;
import com.routinemachine.core.domain.CohortMembership;
import com.routinemachine.core.domain.Organisation;
import com.routinemachine.core.domain.User;
import com.routinemachine.core.repository.UserRepository;
import com.routinemachine.core.service.CohortDashboardService;
import com.routinemachine.core.service.CohortDashboardSummary;
import com.routinemachine.core.service.CohortGroupMeasure;
import com.routinemachine.core.service.CohortWeeklyCheckInChart;
import com.routinemachine.core.service.CsvInviteRowResult;
import com.routinemachine.core.service.OrganisationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

// Phase 12 (docs/product-direction.md's "Organisations and cohorts"): the data model's own
// CRUD, bulk CSV invites, the cohort dashboard's four pilot measures, the "by study group"
// breakdown, and the weekly check-in chart, all checked at the service layer the same way the
// rest of this app scopes by a client-supplied userId (see GranaryController) rather than a
// JWT-protected chain — no table here is read or written by anything hillside- or
// granary-facing. The frontend is the last remaining part.
@RestController
@RequestMapping("/api/organisations")
public class OrganisationController {

    private final OrganisationService organisationService;
    private final CohortDashboardService cohortDashboardService;
    private final UserRepository userRepository;

    public OrganisationController(
            OrganisationService organisationService, CohortDashboardService cohortDashboardService,
            UserRepository userRepository) {
        this.organisationService = organisationService;
        this.cohortDashboardService = cohortDashboardService;
        this.userRepository = userRepository;
    }

    @PostMapping
    public OrganisationResponse createOrganisation(@Valid @RequestBody CreateOrganisationRequest request) {
        Organisation organisation = organisationService.createOrganisation(request.name(), request.foundingAdminUserId());
        return OrganisationResponse.from(organisation);
    }

    @PostMapping("/{organisationId}/cohorts")
    public CohortResponse createCohort(@PathVariable Long organisationId, @Valid @RequestBody CreateCohortRequest request) {
        Cohort cohort = organisationService.createCohort(
                organisationId, request.requestingUserId(), request.name(), request.startsOn(), request.weeks());
        return CohortResponse.from(cohort);
    }

    @PostMapping("/cohorts/{cohortId}/members")
    public CohortMemberResponse addCohortMember(@PathVariable Long cohortId, @Valid @RequestBody AddCohortMemberRequest request) {
        CohortMembership membership =
                organisationService.addCohortMember(cohortId, request.requestingUserId(), request.newMemberUserId());
        return toMemberResponse(membership);
    }

    @GetMapping("/cohorts/{cohortId}/members")
    public List<CohortMemberResponse> listCohortMembers(@PathVariable Long cohortId, @RequestParam Long requestingUserId) {
        return organisationService.listCohortMembers(cohortId, requestingUserId).stream()
                .map(this::toMemberResponse)
                .toList();
    }

    @PutMapping("/{organisationId}/seat-limit")
    public void setSeatLimit(@PathVariable Long organisationId, @Valid @RequestBody SetSeatLimitRequest request) {
        organisationService.setSeatLimit(organisationId, request.requestingUserId(), request.seatLimit());
    }

    @PostMapping("/cohorts/{cohortId}/bulk-invite")
    public List<CsvInviteRowResult> bulkInvite(@PathVariable Long cohortId, @Valid @RequestBody BulkInviteRequest request) {
        return organisationService.bulkInviteByCsv(cohortId, request.requestingUserId(), request.csv());
    }

    @GetMapping("/cohorts/{cohortId}/dashboard")
    public CohortDashboardSummary dashboard(@PathVariable Long cohortId, @RequestParam Long requestingUserId) {
        return cohortDashboardService.summarize(cohortId, requestingUserId, LocalDate.now());
    }

    @GetMapping("/cohorts/{cohortId}/dashboard/by-group")
    public List<CohortGroupMeasure> dashboardByGroup(@PathVariable Long cohortId, @RequestParam Long requestingUserId) {
        return cohortDashboardService.summarizeByGroup(cohortId, requestingUserId, LocalDate.now());
    }

    @GetMapping("/cohorts/{cohortId}/dashboard/weekly-check-ins")
    public CohortWeeklyCheckInChart weeklyCheckIns(@PathVariable Long cohortId, @RequestParam Long requestingUserId) {
        return cohortDashboardService.weeklyCheckIns(cohortId, requestingUserId, LocalDate.now());
    }

    @PostMapping("/cohorts/{cohortId}/survey-response")
    public void submitSurveyResponse(@PathVariable Long cohortId, @Valid @RequestBody SubmitSurveyResponseRequest request) {
        cohortDashboardService.submitSurveyResponse(cohortId, request.requestingUserId(), request.answer());
    }

    private CohortMemberResponse toMemberResponse(CohortMembership membership) {
        User user = userRepository.findById(membership.getUserId())
                .orElseThrow(() -> new NoSuchElementException("No user with id " + membership.getUserId()));
        return new CohortMemberResponse(user.getId(), user.getEmail(), membership.getStudyGroup(), membership.getJoinedAt());
    }
}
