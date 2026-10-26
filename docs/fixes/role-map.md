# Role map (stage 1, 2026-10-26)

Who sees which pages, and what the code enforces. Report only. No source changed. Decision D-1 (is this app the learner side of an organisation tool?) is open, so this lists differences and does not choose a fix.

## What exists in the code

- `UserRole` has `USER` and `OWNER`. `User.role` defaults to `USER`. **Nothing reads it.** The only references are the enum and the field (`User.java` lines 50 and 153). The owner is found by email instead: `OwnerAccount.isOwner` compares the user id with the account whose email is `OWNER_EMAIL`.
- Organisation admin is a row in `organisation_admins`. Cohort member is a row in `cohort_memberships`. Neither is a role.
- The frontend never asks the backend for any role. It has one navigation for personal pages and one for cohort pages, chosen by the URL (`components/Shell.tsx`, line 120).

## Who sees what

Routes are from `frontend/app/`. "Nav" is what `Shell.tsx` draws.

| Relationship | Pages and navigation they get | What the code enforces |
|---|---|---|
| **Visitor** (no session) | `/login`, `/register`, `/privacy`. Every other page sends them to `/login`. The two sign-in pages have no navigation. | Backend: every personal route needs a token (401). `EveryRouteNeedsATokenTest`. |
| **User** (every signed-in account) | Today, Path, Flock, Tally, Reading, Granary, Hillside, Watering holes, Profile, Privacy. Sidebar with those, or five phone tabs (Today, Reading, Granary, Tally, Hillside). | Each personal route acts only for the token's user. `CrossUserIsolationApiIntegrationTest`. |
| **Owner** (account named by `OWNER_EMAIL`) | Everything a user sees, plus `/watchtower` and the API documentation viewer under it. **No link** to either; the owner types the URL. In Watering holes the owner's connected sources show as on; everyone else sees them off. | Backend: `OwnerAccount.isOwner` in `WatchtowerController`, `OwnerOnlyDocsFilter` and `WateringHoleStatusController`. A non-owner gets 404. Checked live: `/watchtower` answered 404 for an ordinary account. Integrations write only to the owner's rows. |
| **Organisation admin** (row in `organisation_admins`) | The same personal pages as any user, because the account is an ordinary account. Plus `/cohort/[cohortId]`. **No link to the cohort page anywhere**, so the admin must know the URL. No page to create an organisation or cohort, invite people or set a seat limit. Those are API calls only (`page.tsx` header comment). | Backend: `requireAdmin` on create cohort, add member, list members, bulk invite, seat limit. `requireParticipant` (admin or member) on the three dashboard routes. See `tenancy-check.md`. |
| **Cohort member** (learner, row in `cohort_memberships`) | The same personal pages, plus `/cohort/[cohortId]`, again with no link to it. The page shows the same view as for an admin, plus three survey buttons. | Backend: may read the three dashboard routes and answer the survey. May not list members (403). |

Observed live with a throwaway account that is neither admin nor member: `/cohort/1` shows "Not your cohort." inside the cohort navigation (one "Cohort 1" link and "My own Today"). The 403 comes from the backend. The page itself does not check membership.

## Where the code differs from the rule

The rule: cohort admins see only the cohort page and are not learners; learners can see both.

| # | Difference | Evidence |
|---|---|---|
| 1 | **An admin sees every personal page.** Nothing separates an admin account from a learner account. An admin has their own Today, Flock, Granary and Profile. Making admins "only the cohort page" needs a way to tell an admin account from a learner account, and none exists (the unused `role` column is the obvious candidate). | `Shell.tsx` (no role input); `UserRole` unused. |
| 2 | **A learner cannot find the cohort page.** The rule says learners see both, and the backend allows it. But no personal page, sidebar or tab links to `/cohort/[id]`, and the Profile page has no "Your programme" card (the reference does). | `grep "/cohort"` in `app/`, `components/`, `lib/` finds only the cohort page, `Shell.tsx`, the survey action and an analytics sanitiser. |
| 3 | **The cohort navigation shows for anyone and any id.** It is chosen from the path, before the backend answers. A stranger who types a cohort id gets the cohort sidebar around the "Not your cohort" message. | Seen live. `Shell.tsx` line 120. |
| 4 | **The cohort sidebar names the cohort by number only**, and has no organisation name, cohort list, Seats and invites, Reports or Privacy and data links. The reference has all of these. The footer text "Admins see cohort totals only. Learners can read this exact page." is shown to both, and does not say which one the reader is. | `Shell.tsx` lines 113 to 177; `cohort.html`. |
| 5 | **No admin screens.** Create organisation, create cohort, add member, bulk invite, seat limit and member list exist in the API only. The reference shows Export CSV and Invite learners buttons. | `OrganisationController`; `page.tsx` header comment. |
| 6 | **The survey form shows to everyone who can open the page.** The backend accepts answers from members only, so an admin who is not a member would be refused. I did not check what the page then shows. | `page.tsx` lines 171 to 205; `submitSurveyResponse`. |
| 7 | **No way to remove or add an admin, or remove a member.** The controller has no delete route and no route to add a second admin. The founder is the only admin unless the table is edited. | `OrganisationController` mappings. |
| 8 | **Any signed-in account can found an organisation**, and so becomes an admin. No approval, no limit. | `createOrganisation`; tenancy finding 2. |
| 9 | **On a phone the cohort page has no navigation.** The cohort branch of `Shell` draws only the side panel, and that panel is hidden below the medium width. The bottom tab bar is drawn only on the personal branch. By code only; not captured at 390 wide. | `Shell.tsx` lines 125 and 258. |
| 10 | **The `OWNER` role is not enforced.** The role column and the email check can disagree. Only the email check has any effect. | `OwnerAccount.java`; `User.java`. |

## Open questions for D-1

1. Is an organisation admin ever also a learner? Today the code allows both for one account (admin and member of the same cohort). The rule says no.
2. If admins are not learners, does an admin account get its own sign-up path and a role, or is "admin" decided by the invite?
3. Should the owner be a role in the table, or stay an email in the environment?
