import { redirect } from "next/navigation";
import { ApiError, backendFetch } from "@/lib/api";
import { getSessionUserId } from "@/lib/auth/session";
import { submitCohortSurveyResponse } from "@/lib/actions";
import type {
  CohortDashboardSummary,
  CohortGroupMeasure,
  CohortWeeklyCheckInChart,
} from "@/lib/types";

// Phase 12's last part: the cohort dashboard page. Read-only for now — creating an
// organisation/cohort, bulk CSV invites, and setting a seat limit are an admin UI that's a
// follow-up slice (today those are only reachable via the API directly). "Admins see
// aggregates only. Learners can read this exact page" (docs/product-direction.md) means this
// one page serves both roles identically; the backend's own admin-or-member check is what
// decides who gets in, not anything in this component.

function fraction(measure: { count: number; denominator: number }): string {
  return `${measure.count} of ${measure.denominator}`;
}

export default async function CohortDashboardPage({
  params,
}: {
  params: Promise<{ cohortId: string }>;
}) {
  const { cohortId } = await params;
  const userId = await getSessionUserId();
  if (userId === null) {
    redirect("/login");
  }

  let summary: CohortDashboardSummary;
  let groups: CohortGroupMeasure[];
  let weeklyChart: CohortWeeklyCheckInChart;
  try {
    [summary, groups, weeklyChart] = await Promise.all([
      backendFetch<CohortDashboardSummary>(
        `/api/organisations/cohorts/${cohortId}/dashboard?requestingUserId=${userId}`,
      ),
      backendFetch<CohortGroupMeasure[]>(
        `/api/organisations/cohorts/${cohortId}/dashboard/by-group?requestingUserId=${userId}`,
      ),
      backendFetch<CohortWeeklyCheckInChart>(
        `/api/organisations/cohorts/${cohortId}/dashboard/weekly-check-ins?requestingUserId=${userId}`,
      ),
    ]);
  } catch (error) {
    if (error instanceof ApiError && error.status === 403) {
      return (
        <div className="card flex flex-col gap-2 p-5">
          <h1 className="page-title">Not your cohort.</h1>
          <p className="text-[14px] text-muted">
            This dashboard is only visible to the cohort&apos;s admins and its own learners.
          </p>
        </div>
      );
    }
    throw error;
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <p className="text-[15px] font-semibold text-tint-text">Cohort {cohortId}</p>
        <h1 className="page-title">How the cohort is walking.</h1>
        <p className="mt-2 max-w-[62ch] text-[17px] text-muted">
          Totals only. You never see a learner&apos;s notes, journal, money, interests or
          hillside name. Every rate shows its count.
        </p>
      </div>

      <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
        <div className="card flex flex-col gap-1 p-4">
          <span className="text-[13px] text-muted">Activated in the first 7 days</span>
          <span className="text-[22px] font-semibold">{fraction(summary.activatedWithinSevenDays)}</span>
          <span className="text-[12px] text-muted">Set a goal and checked in once</span>
        </div>
        <div className="card flex flex-col gap-1 p-4">
          <span className="text-[13px] text-muted">Checked in this week</span>
          <span className="text-[22px] font-semibold">{fraction(summary.checkedInThisWeek)}</span>
          <span className="text-[12px] text-muted">Of activated learners</span>
        </div>
        <div className="card flex flex-col gap-1 p-4">
          <span className="text-[13px] text-muted">Came back after a missed day</span>
          <span className="text-[22px] font-semibold">{fraction(summary.returnedAfterAMissedDay)}</span>
          <span className="text-[12px] text-muted">Returned within 7 days</span>
        </div>
        <div className="card flex flex-col gap-1 p-4">
          <span className="text-[13px] text-muted">Said it felt supportive</span>
          <span className="text-[22px] font-semibold">{fraction(summary.feltSupportive)}</span>
          <span className="text-[12px] text-muted">
            {summary.saidPressuring} said pressuring, {summary.saidNeutral} neutral
          </span>
        </div>
      </div>

      <section className="card flex flex-col gap-3 p-5">
        <div className="flex flex-wrap items-baseline justify-between gap-2">
          <h2 className="text-[17px] font-semibold">Weekly check-ins</h2>
          <span className="text-[13px] text-muted">
            Checked in at least once that week, of {weeklyChart.activatedDenominator} activated
          </span>
        </div>
        <div className="grid grid-cols-8 items-end gap-2" style={{ height: 160 }}>
          {weeklyChart.weeks.map((week) => {
            const maxHeight = 120;
            const barHeight =
              week.checkedIn === null || weeklyChart.activatedDenominator === 0
                ? 0
                : Math.max(6, Math.round((week.checkedIn / weeklyChart.activatedDenominator) * maxHeight));
            return (
              <div key={week.week} className="flex flex-col items-center gap-1.5 text-[12px] text-muted">
                <span>{week.checkedIn ?? " "}</span>
                <div
                  className="w-full rounded-t-[6px] rounded-b-[2px]"
                  style={{
                    height: week.isFuture ? 40 : Math.max(barHeight, 4),
                    background: week.isFuture ? "transparent" : week.isCurrentWeek ? "var(--tint)" : "#1D1D1F",
                    border: week.isFuture ? "1.5px dashed #AEAEB2" : undefined,
                  }}
                />
                <span>Wk {week.week}</span>
              </div>
            );
          })}
        </div>
      </section>

      {groups.length > 0 && (
        <section className="card flex flex-col gap-3 p-5">
          <h2 className="text-[17px] font-semibold">By study group</h2>
          <div className="overflow-x-auto">
            <table className="w-full min-w-[480px] text-[14px]">
              <thead>
                <tr className="text-left text-muted">
                  <th className="pb-2 font-medium">Group</th>
                  <th className="pb-2 font-medium">Learners</th>
                  <th className="pb-2 font-medium">Checked in this week</th>
                  <th className="pb-2 font-medium">Came back after a break</th>
                </tr>
              </thead>
              <tbody>
                {groups.map((group, i) => (
                  <tr key={group.studyGroup} className={i === 0 ? "" : "border-t hairline"}>
                    <td className="py-2">{group.studyGroup}</td>
                    <td className="py-2">{group.memberCount}</td>
                    {group.hidden ? (
                      <td className="py-2 text-muted" colSpan={2}>
                        Hidden: fewer than 5 people
                      </td>
                    ) : (
                      <>
                        <td className="py-2">{group.checkedInThisWeek ? fraction(group.checkedInThisWeek) : "—"}</td>
                        <td className="py-2">
                          {group.returnedAfterAMissedDay ? fraction(group.returnedAfterAMissedDay) : "—"}
                        </td>
                      </>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p className="text-[13px] text-muted">
            Groups under 5 are hidden so no one can be picked out from a total.
          </p>
        </section>
      )}

      <section className="card flex flex-col gap-3 p-5">
        <h2 className="text-[17px] font-semibold">How did this week feel?</h2>
        <p className="text-[13px] text-muted">
          Optional, and only ever shown to the pilot as a total — never attached to your name.
        </p>
        <form action={submitCohortSurveyResponse} className="flex flex-wrap gap-2">
          <input type="hidden" name="cohortId" value={cohortId} />
          <button
            type="submit"
            name="answer"
            value="SUPPORTIVE"
            className="rounded-full px-4 py-2 text-[14px] font-semibold"
            style={{ background: "var(--fill)" }}
          >
            Supportive
          </button>
          <button
            type="submit"
            name="answer"
            value="NEUTRAL"
            className="rounded-full px-4 py-2 text-[14px] font-semibold"
            style={{ background: "var(--fill)" }}
          >
            Neutral
          </button>
          <button
            type="submit"
            name="answer"
            value="PRESSURING"
            className="rounded-full px-4 py-2 text-[14px] font-semibold"
            style={{ background: "var(--fill)" }}
          >
            Pressuring
          </button>
        </form>
      </section>
    </div>
  );
}
