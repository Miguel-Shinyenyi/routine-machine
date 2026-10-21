package com.routinemachine.core.api;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.routinemachine.core.security.TokenUser;
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
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
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
    public OrganisationResponse createOrganisation(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateOrganisationRequest request) {
        Organisation organisation = organisationService.createOrganisation(request.name(), TokenUser.idOf(jwt));
        return OrganisationResponse.from(organisation);
    }

    @PostMapping("/{organisationId}/cohorts")
    public CohortResponse createCohort(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long organisationId, @Valid @RequestBody CreateCohortRequest request) {
        Cohort cohort = organisationService.createCohort(
                organisationId, TokenUser.idOf(jwt), request.name(), request.startsOn(), request.weeks());
        return CohortResponse.from(cohort);
    }

    @PostMapping("/cohorts/{cohortId}/members")
    public CohortMemberResponse addCohortMember(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long cohortId, @Valid @RequestBody AddCohortMemberRequest request) {
        CohortMembership membership =
                organisationService.addCohortMember(cohortId, TokenUser.idOf(jwt), request.newMemberUserId());
        return toMemberResponse(membership);
    }

    @GetMapping("/cohorts/{cohortId}/members")
    public List<CohortMemberResponse> listCohortMembers(@AuthenticationPrincipal Jwt jwt, @PathVariable Long cohortId) {
        return organisationService.listCohortMembers(cohortId, TokenUser.idOf(jwt)).stream()
                .map(this::toMemberResponse)
                .toList();
    }

    @PutMapping("/{organisationId}/seat-limit")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setSeatLimit(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long organisationId, @Valid @RequestBody SetSeatLimitRequest request) {
        organisationService.setSeatLimit(organisationId, TokenUser.idOf(jwt), request.seatLimit());
    }

    @PostMapping("/cohorts/{cohortId}/bulk-invite")
    public List<CsvInviteRowResult> bulkInvite(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long cohortId, @Valid @RequestBody BulkInviteRequest request) {
        return organisationService.bulkInviteByCsv(cohortId, TokenUser.idOf(jwt), request.csv());
    }

    @GetMapping("/cohorts/{cohortId}/dashboard")
    public CohortDashboardSummary dashboard(@AuthenticationPrincipal Jwt jwt, @PathVariable Long cohortId) {
        return cohortDashboardService.summarize(cohortId, TokenUser.idOf(jwt), LocalDate.now());
    }

    @GetMapping("/cohorts/{cohortId}/dashboard/by-group")
    public List<CohortGroupMeasure> dashboardByGroup(@AuthenticationPrincipal Jwt jwt, @PathVariable Long cohortId) {
        return cohortDashboardService.summarizeByGroup(cohortId, TokenUser.idOf(jwt), LocalDate.now());
    }

    @GetMapping("/cohorts/{cohortId}/dashboard/weekly-check-ins")
    public CohortWeeklyCheckInChart weeklyCheckIns(@AuthenticationPrincipal Jwt jwt, @PathVariable Long cohortId) {
        return cohortDashboardService.weeklyCheckIns(cohortId, TokenUser.idOf(jwt), LocalDate.now());
    }

    @PostMapping("/cohorts/{cohortId}/survey-response")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void submitSurveyResponse(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long cohortId, @Valid @RequestBody SubmitSurveyResponseRequest request) {
        cohortDashboardService.submitSurveyResponse(cohortId, TokenUser.idOf(jwt), request.answer());
    }

    private CohortMemberResponse toMemberResponse(CohortMembership membership) {
        User user = userRepository.findById(membership.getUserId())
                .orElseThrow(() -> new NoSuchElementException("No user with id " + membership.getUserId()));
        return new CohortMemberResponse(user.getId(), user.getEmail(), membership.getStudyGroup(), membership.getJoinedAt());
    }
}
