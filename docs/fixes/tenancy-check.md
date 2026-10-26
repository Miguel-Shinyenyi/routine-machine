# Tenancy check (stage 1, 2026-10-26)

Which organisation's records each endpoint can reach, how the code decides, and which test proves it. Report only. No test was added and no source changed.

## How the code decides

- Every route under `/api/organisations/**` is on the token-required chain (`SecurityConfig.TOKEN_REQUIRED_PATTERNS`). No token gives 401.
- The caller is the user in the signed token (`TokenUser.idOf(jwt)`). No route reads a user id from the body or query.
- Organisation id routes check `OrganisationAdminRepository.existsByOrganisationIdAndUserId(organisationId, userId)` (`OrganisationService.requireAdmin`). That is "admin of this organisation", not "admin of any".
- Cohort id routes load the cohort, then check the same thing against the cohort's own organisation id. The dashboard routes also accept a cohort member (`CohortDashboardService.requireParticipant`).
- A failed check throws `NotAnOrganisationAdminException` or `NotACohortParticipantException`. `GlobalExceptionHandler` maps both to 403. A missing cohort or organisation maps to 404.
- No table other than organisations, admins, cohorts, memberships, invites and survey responses is scoped by organisation. The hillside "cohort" switch does not use the cohort tables: `HillsideV2Service` says "Cohort" and "open" resolve to the same opted-in population (lines 20 and 21).

## Every endpoint that takes an organisation or cohort id

"Other org" means: the caller is a real admin of a different organisation.

| # | Endpoint | How the code decides | Test that an admin of another org is refused | Test that a person with no organisation is refused |
|---|---|---|---|---|
| 1 | `POST /api/organisations` | Any signed-in user may create one. The founding admin is the token's user. | not applicable (creates a new organisation) | `OrganisationTokenApiIntegrationTest.theFoundingAdminIsTheTokensUserWhateverTheRequestSays` |
| 2 | `POST /api/organisations/{organisationId}/cohorts` | 404 if the organisation does not exist, then `requireAdmin(organisationId)` gives 403. | **no test** | `OrganisationApiIntegrationTest.someoneWhoIsNotAnAdminGetsForbiddenCreatingACohort`; `OrganisationServiceTest.someoneWhoIsNotAnOrganisationAdminCannotCreateACohort` |
| 3 | `POST /api/organisations/cohorts/{cohortId}/members` | 404 if no cohort, then `requireAdmin(cohort.organisationId)`. | **no test** | service level only: `OrganisationServiceTest.someoneWhoIsNotAnOrganisationAdminCannotAddACohortMember`. No API-level refusal test. |
| 4 | `GET /api/organisations/cohorts/{cohortId}/members` | Admin of the cohort's organisation only. A member is refused. | `CrossUserIsolationApiIntegrationTest.anAccountCannotReadOrRunAnotherAccountsCohort` (asserts 403) | `OrganisationTokenApiIntegrationTest.aMemberSubmitsTheirOwnSurveyAnswerNotSomeoneElses` (member gets 403); `OrganisationServiceTest.someoneWhoIsNotAnOrganisationAdminCannotListCohortMembers` |
| 5 | `PUT /api/organisations/{organisationId}/seat-limit` | `requireAdmin` first, so a missing organisation also answers 403 (documented in the controller). | **no test** | service level only: `OrganisationServiceTest.someoneWhoIsNotAnOrganisationAdminCannotSetASeatLimit`. No API-level refusal test. |
| 6 | `POST /api/organisations/cohorts/{cohortId}/bulk-invite` | Admin of the cohort's organisation only. | `CrossUserIsolationApiIntegrationTest.anAccountCannotReadOrRunAnotherAccountsCohort` (403) | `OrganisationServiceTest.someoneWhoIsNotAnOrganisationAdminCannotBulkInvite` |
| 7 | `GET .../cohorts/{cohortId}/dashboard` | Admin of the organisation or member of the cohort. | `CrossUserIsolationApiIntegrationTest.anAccountCannotReadOrRunAnotherAccountsCohort` (403) | `OrganisationApiIntegrationTest.someoneWhoIsNeitherAnAdminNorAMemberCannotReadTheDashboard`; `OrganisationTokenApiIntegrationTest.aNonAdminCannotOpenTheDashboardEvenWhenNamingTheAdmin`; `CohortDashboardServiceTest.someoneWhoIsNeitherAnAdminNorAMemberCannotViewTheDashboard` |
| 8 | `GET .../cohorts/{cohortId}/dashboard/by-group` | Same as 7. | same test as 7 (403) | no separate test for a person with no organisation |
| 9 | `GET .../cohorts/{cohortId}/dashboard/weekly-check-ins` | Same as 7. | same test as 7 (403) | `CohortDashboardServiceTest.someoneWhoIsNeitherAnAdminNorAMemberCannotViewTheWeeklyChart` |
| 10 | `POST .../cohorts/{cohortId}/survey-response` | Cohort member only. An admin who is not a member is refused too. | `CrossUserIsolationApiIntegrationTest.anAccountCannotReadOrRunAnotherAccountsCohort` (403) | `CohortDashboardServiceTest.someoneWhoIsNotAMemberCannotSubmitASurveyResponse` |
| 11 | No token on any of the above | 401 | `OrganisationTokenApiIntegrationTest.everyRouteRefusesARequestWithNoToken`; `EveryRouteNeedsATokenTest` | same |

Not an endpoint, but it moves people into cohorts: `OrganisationService.fulfillInvitesForNewUser`, called from register and the GitHub sign-in path. It matches on email.

### What "no test" means here

In `CrossUserIsolationApiIntegrationTest` each of two accounts founds its own organisation and cohort (lines 115 to 118). The test then calls account A's cohort as account B, who is a real admin of B's organisation. That is the exact case the prompt asks about, and it covers rows 4, 6, 7, 8, 9 and 10.

Rows 2, 3 and 5 are guarded by the same `requireAdmin` call, and the code reads as correct. But their tests only use a person who is an admin of nothing. A regression that asked "is this person an admin of any organisation?" would pass every one of them. The six routes in the isolation test would catch a regression in their own service methods, but rows 2, 3 and 5 each call `requireAdmin` from their own method, so a break there would go unseen.

## Findings from reading the code (not run live)

1. **404 versus 403 tells a signed-in stranger which ids exist.** `findCohort` runs before the permission check, so a cohort id that does not exist answers 404 and one that exists in another organisation answers 403. The same holds for `POST /{organisationId}/cohorts`. Seat limit answers 403 for both, so it does not leak. Low severity, since the ids are small integers and the response carries no data.
2. **Any account can found an organisation.** Row 1 has no role check, no limit and no approval. The founder becomes its admin. This follows from "organisation admins are a table, not a role", but it means any signed-in person can create an organisation and then add other people's user ids to a cohort of their own.
3. **An admin adds a person by numeric user id, with no consent step and no seat-limit check** (`addCohortMember`). Only the bulk CSV path checks the seat limit. The added person's check-ins then count in that cohort's totals. A user id that does not exist is not handled in the service and is not tested.
4. **Invites match on an unverified email.** Register takes an email and a password only. Anyone who registers with an invited address joins that cohort. I found no email verification step in the register flow. What a cohort member can then read is aggregate totals only.
5. **The code comment on `OrganisationController` is stale.** It says access is checked at the service layer "the same way the rest of this app scopes by a client-supplied userId". The class now takes the user from the token. (Docs rule: say so, do not decide silently.)
6. **The Hillside "Cohort" switch is not a cohort.** It reads the same opted-in population as "Open", per `HillsideV2Service`. Not a leak, but the label promises a boundary that does not exist.

## Test run

Command, from `backend/`:

```
./mvnw -B test -Dtest='OrganisationServiceTest,CohortDashboardServiceTest,OrganisationApiIntegrationTest,OrganisationTokenApiIntegrationTest,CrossUserIsolationApiIntegrationTest,EveryRouteNeedsATokenTest'
```

Real output (Testcontainers, Docker running):

```
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 20.74 s -- in com.routinemachine.core.api.OrganisationApiIntegrationTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 5.539 s -- in com.routinemachine.core.api.EveryRouteNeedsATokenTest
[INFO] Tests run: 17, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 13.02 s -- in com.routinemachine.core.api.CrossUserIsolationApiIntegrationTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 5.442 s -- in com.routinemachine.core.api.OrganisationTokenApiIntegrationTest
[INFO] Tests run: 23, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 4.810 s -- in com.routinemachine.core.service.OrganisationServiceTest
[INFO] Tests run: 16, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 4.391 s -- in com.routinemachine.core.service.CohortDashboardServiceTest
[INFO] Tests run: 70, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

70 tests, 0 failures. These tests prove what the table says they prove and no more. Rows marked "no test" were not exercised.

## For stage 3 (not started)

Add API-level tests where the caller is an admin of a different organisation, for rows 2, 3 and 5, in `CrossUserIsolationApiIntegrationTest` beside the existing cohort test. Then decide findings 1 to 4.
