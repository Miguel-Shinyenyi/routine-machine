import Link from "next/link";
import { authenticatedBackendFetch, backendFetch } from "@/lib/api";
import { setHillsideOptIn, questionCredit } from "@/lib/actions";
import { getSessionToken } from "@/lib/auth/session";
import { mondayOf, addDays } from "@/lib/weekDates";
import { todayIsoDate } from "@/lib/schedule";
import type { CurrentUser, EvidenceEvent, Goal, HillsideV2 } from "@/lib/types";

function buildQuery(scope: string, goalId: string | null, seasonStart: string): string {
  const params = new URLSearchParams();
  params.set("scope", scope);
  if (goalId !== null) params.set("goalId", goalId);
  params.set("seasonStart", seasonStart);
  return params.toString();
}

function buildHref(scope: string, goalId: string | null, seasonStart: string): string {
  return `/hillside?${buildQuery(scope, goalId, seasonStart)}`;
}

function formatDay(iso: string): string {
  const date = new Date(iso + "T00:00:00Z");
  return date.toLocaleDateString("en-GB", { day: "numeric", month: "short", timeZone: "UTC" });
}

// Cosmetic only, like the design reference's own "Season 42" — counts whole weeks since an
// arbitrary fixed Monday, just so the number increments by one each real week rather than
// being invented fresh every render.
const SEASON_EPOCH_MONDAY = "2026-01-05";
function seasonNumber(seasonStartIso: string): number {
  const epoch = new Date(SEASON_EPOCH_MONDAY + "T00:00:00Z").getTime();
  const start = new Date(seasonStartIso + "T00:00:00Z").getTime();
  return Math.round((start - epoch) / (7 * 24 * 60 * 60 * 1000)) + 1;
}

const EVIDENCE_SOURCE_LABELS: Record<string, string> = {
  MANUAL: "You tapped it",
  HUB_SYNC: "UMWAYI hub",
  SCHEDULE: "Schedule",
  WAKATIME_SYNC: "WakaTime",
  ACTIVITYWATCH_SYNC: "ActivityWatch",
  GITHUB_SYNC: "GitHub",
  RESCUETIME_SYNC: "RescueTime",
  READER_NOTES: "Reader's note",
};

function evidenceDescription(event: EvidenceEvent): string {
  const label = EVIDENCE_SOURCE_LABELS[event.source] ?? event.source;
  return event.minutes !== null ? `${label}, ${event.minutes} min` : label;
}

const STATUS_LABELS: Record<string, string> = {
  ACCEPTED: "Accepted",
  REJECTED: "Not counted",
  PENDING: "Pending",
  SELF_REPORTED: "Self-reported",
};

export default async function HillsidePage(props: PageProps<"/hillside">) {
  const searchParams = await props.searchParams;
  const rawScope = searchParams.scope;
  const scope = typeof rawScope === "string" ? rawScope : "cohort";
  const rawGoalId = searchParams.goalId;
  const goalId = typeof rawGoalId === "string" && rawGoalId !== "all" ? rawGoalId : null;
  const rawSeasonStart = searchParams.seasonStart;
  const seasonStart = typeof rawSeasonStart === "string" ? rawSeasonStart : mondayOf(todayIsoDate());
  const seasonEnd = addDays(seasonStart, 6);

  const sessionToken = await getSessionToken();
  const me = sessionToken
    ? await authenticatedBackendFetch<CurrentUser>("/api/users/me", sessionToken).catch(() => null)
    : null;

  const [hillside, myGoals, ledger] = await Promise.all([
    backendFetch<HillsideV2>(`/api/hillside/v2?${buildQuery(scope, goalId, seasonStart)}`),
    me ? backendFetch<Goal[]>("/api/goals").catch(() => []) : Promise.resolve([]),
    me
      ? backendFetch<EvidenceEvent[]>(
          `/api/evidence-events?seasonStart=${seasonStart}&seasonEnd=${seasonEnd}`,
        ).catch(() => [])
      : Promise.resolve([]),
  ]);

  const yourPseudonym = hillside.neighbors.find((n) => n.isYou)?.pseudonym ?? null;

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-end justify-between gap-5">
        <div className="min-w-0 flex-1 basis-[420px]">
          <p className="text-[15px] font-semibold text-tint-text">
            The hillside &middot; Season {seasonNumber(seasonStart)} &middot; {formatDay(seasonStart)} to {formatDay(seasonEnd)}
          </p>
          <h1 className="page-title">Walking together, not racing.</h1>
          <p className="mt-2 max-w-[62ch] text-[17px] text-muted">
            Optional, and only with people you were invited alongside. One credit for each goal
            you show up for in a day, up to 3. A missed day takes nothing away, and the board
            starts fresh every Monday.
          </p>
          {yourPseudonym && (
            <p className="mt-2 text-[13px] text-muted">
              On the hillside you are {yourPseudonym}. Nobody here knows your name.
            </p>
          )}
        </div>
        {me && (
          <form action={setHillsideOptIn} className="flex items-center gap-2.5">
            <span className="text-[14px]">Show the hillside</span>
            <input type="hidden" name="optIn" value={me.hillsideOptIn ? "false" : "true"} />
            <button type="submit" role="switch" aria-checked={me.hillsideOptIn} className="switch" />
          </form>
        )}
      </div>

      {me && !me.hillsideOptIn ? (
        <section className="card flex flex-col gap-1.5 p-5">
          <h2 className="text-[17px] font-semibold">Personal progress only.</h2>
          <p className="text-[15px] text-muted">
            The hillside is hidden. Your credits still count in your own tally, and nobody can
            see you on the board until you switch it back on.
          </p>
          <Link href="/tally" className="text-[15px] text-tint-text hover:underline">
            Open your tally &rsaquo;
          </Link>
        </section>
      ) : (
        <div className="flex flex-col gap-[22px]">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="inline-flex gap-0.5 rounded-[9px] p-0.5" style={{ background: "var(--fill)" }}>
              {(["cohort", "open"] as const).map((s) => (
                <Link
                  key={s}
                  href={buildHref(s, goalId, seasonStart)}
                  aria-pressed={scope === s}
                  className="rounded-[7px] px-3.5 py-1.5 text-[13px] font-medium text-ink"
                  style={
                    scope === s
                      ? { background: "var(--surface)", fontWeight: 600, boxShadow: "0 3px 8px rgba(0,0,0,.12), 0 3px 1px rgba(0,0,0,.04)" }
                      : undefined
                  }
                >
                  {s === "cohort" ? `Cohort · ${hillside.totalParticipants} walking` : `Open hillside · ${hillside.totalParticipants} walking`}
                </Link>
              ))}
            </div>
            <div className="inline-flex max-w-full flex-wrap gap-0.5 rounded-[9px] p-0.5" style={{ background: "var(--fill)" }}>
              <Link
                href={buildHref(scope, null, seasonStart)}
                aria-pressed={goalId === null}
                className="rounded-[7px] px-3.5 py-1.5 text-[13px] font-medium text-ink"
                style={goalId === null ? { background: "var(--surface)", fontWeight: 600, boxShadow: "0 3px 8px rgba(0,0,0,.12), 0 3px 1px rgba(0,0,0,.04)" } : undefined}
              >
                All goals
              </Link>
              {myGoals.map((goal) => (
                <Link
                  key={goal.id}
                  href={buildHref(scope, String(goal.id), seasonStart)}
                  aria-pressed={goalId === String(goal.id)}
                  className="rounded-[7px] px-3.5 py-1.5 text-[13px] font-medium text-ink"
                  style={
                    goalId === String(goal.id)
                      ? { background: "var(--surface)", fontWeight: 600, boxShadow: "0 3px 8px rgba(0,0,0,.12), 0 3px 1px rgba(0,0,0,.04)" }
                      : undefined
                  }
                >
                  {goal.name}
                </Link>
              ))}
            </div>
          </div>

          <div className="flex flex-wrap items-stretch gap-5">
            <section className="card flex min-w-0 flex-[1_1_340px] flex-col gap-1.5 p-5">
              <span className="text-[13px] text-muted">{scope === "cohort" ? "Cohort" : "Open hillside"} &middot; this season</span>
              {hillside.yourScore !== null ? (
                <>
                  <h2 className="text-[28px] font-semibold tracking-tight">{hillside.band}</h2>
                  <p className="text-[15px]">
                    <strong className="font-semibold">{hillside.yourScore} credits</strong> from{" "}
                    {hillside.yourDaysShownUp} days shown up, across {hillside.yourGoalCount}{" "}
                    {hillside.yourGoalCount === 1 ? "goal" : "goals"}.
                  </p>
                </>
              ) : me && !myGoals.length ? (
                <>
                  <h2 className="text-[20px] font-semibold">No goals to earn credit for yet.</h2>
                  <p className="text-[15px] text-muted">Create a goal on Flock, then tend a routine linked to it.</p>
                </>
              ) : (
                <>
                  <h2 className="text-[20px] font-semibold">Not on the board yet this season.</h2>
                  <p className="text-[15px] text-muted">
                    {hillside.totalParticipants} people show up here. Tend a goal to see where
                    you&rsquo;d stand.
                  </p>
                  {!me && (
                    <p className="mt-1.5 text-[13px] text-muted">
                      <Link href="/login" className="text-tint-text hover:underline">
                        Sign in
                      </Link>{" "}
                      to see your own band.
                    </p>
                  )}
                </>
              )}
            </section>
            <section className="card flex min-w-0 flex-[1_1_300px] flex-col gap-1.5 p-5">
              <span className="chip chip-tint self-start">Returns</span>
              <h2 className="text-[20px] font-semibold">{hillside.cohortReturns} people came back this season.</h2>
              <p className="text-[14px] text-muted">
                After a missed day or more, they checked in again. Coming back is the habit this
                board exists to notice. It never ranks anyone.
              </p>
            </section>
          </div>

          <div className="flex flex-wrap items-start gap-5">
            <section className="min-w-0 flex-[999_1_520px]">
              <div className="mb-2 flex flex-wrap items-baseline justify-between gap-2 px-1">
                <h2 className="text-[17px] font-semibold">Walking near you</h2>
                <span className="text-[13px] text-muted">Three ahead, three behind. There is no top-ten page.</span>
              </div>
              {hillside.neighbors.length === 0 ? (
                <p className="px-1 text-[13px] text-muted">No neighbours to show yet.</p>
              ) : (
                <div className="overflow-x-auto rounded-[14px] bg-surface">
                  <div style={{ minWidth: "560px" }}>
                    {hillside.neighbors.map((row, i) => (
                      <div
                        key={row.pseudonym}
                        className={`grid items-center gap-3 px-4 py-2 ${i === 0 ? "" : "border-t hairline"}`}
                        style={{
                          gridTemplateColumns: "44px minmax(150px,1.3fr) minmax(130px,1fr) minmax(140px,1.3fr)",
                          background: row.isYou ? "var(--tint-fill)" : undefined,
                        }}
                      >
                        <span className="text-[15px] font-semibold text-muted" style={{ fontVariantNumeric: "tabular-nums" }}>
                          {row.rank}
                        </span>
                        <span className="block text-[14px] font-semibold">
                          {row.pseudonym}
                          {row.isYou && " (you)"}
                        </span>
                        <span className="flex gap-1" role="img" aria-label={`${row.daysShownUp} of 7 days`}>
                          {Array.from({ length: 7 }).map((_, dotIndex) => (
                            <i
                              key={dotIndex}
                              className="h-3 w-3 rounded-full"
                              style={{
                                background:
                                  dotIndex < row.daysShownUp ? (row.isYou ? "var(--tint-text)" : "var(--ink)") : "#e8e8ed",
                              }}
                            />
                          ))}
                        </span>
                        <span className="text-[13px] text-muted">{row.credits} credits</span>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </section>

            <aside className="card min-w-0 flex-[1_1_300px] p-5" aria-labelledby="rules">
              <h2 id="rules" className="mb-2 text-[17px] font-semibold">
                Why this hillside is calm.
              </h2>
              <div className="flex flex-col gap-2.5 text-[14px]">
                <div className="flex gap-2.5">
                  <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" aria-hidden="true" className="mt-0.5 shrink-0 text-muted">
                    <path d="M5 5v14M9 5v14M13 5v14M17 5v14" />
                  </svg>
                  <span>
                    <b className="block font-semibold">Showing up, not volume.</b>
                    <span className="text-muted">
                      Thirty minutes on five days beats five hours on one. Hours, pages and lines
                      of code are never ranked.
                    </span>
                  </span>
                </div>
                <div className="flex gap-2.5 border-t hairline pt-2.5">
                  <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" aria-hidden="true" className="mt-0.5 shrink-0 text-muted">
                    <path d="M3 12a9 9 0 1 0 3-6.7M3 4v5h5" />
                  </svg>
                  <span>
                    <b className="block font-semibold">Seasons, not forever.</b>
                    <span className="text-muted">Every Monday the board starts again. Your own tally keeps everything.</span>
                  </span>
                </div>
                <div className="flex gap-2.5 border-t hairline pt-2.5">
                  <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" aria-hidden="true" className="mt-0.5 shrink-0 text-muted">
                    <circle cx="12" cy="9" r="4" />
                    <path d="M4 21c1.5-4 4.5-6 8-6s6.5 2 8 6" />
                  </svg>
                  <span>
                    <b className="block font-semibold">Small, invited groups.</b>
                    <span className="text-muted">
                      Your cohort by default. Pseudonyms change each season, and groups under 5
                      are never shown.
                    </span>
                  </span>
                </div>
                <div className="flex gap-2.5 border-t hairline pt-2.5">
                  <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" aria-hidden="true" className="mt-0.5 shrink-0 text-muted">
                    <path d="M3 12h18M12 3v18" />
                  </svg>
                  <span>
                    <b className="block font-semibold">You choose.</b>
                    <span className="text-muted">
                      Hide it any time. There is no &ldquo;discipline score&rdquo;, because a
                      record of days is not a verdict on a person.
                    </span>
                  </span>
                </div>
              </div>
            </aside>
          </div>

          {me && (
            <section aria-labelledby="ledger">
              <div className="mb-2 flex flex-wrap items-baseline justify-between gap-2 px-1">
                <h2 id="ledger" className="text-[17px] font-semibold">
                  How your credits were counted
                </h2>
              </div>
              {ledger.length === 0 ? (
                <p className="px-1 text-[13px] text-muted">No evidence recorded this season yet.</p>
              ) : (
                <div className="overflow-x-auto rounded-[14px] bg-surface">
                  <table className="w-full text-[14px]" style={{ minWidth: "760px" }}>
                    <thead>
                      <tr className="text-left text-[13px] text-muted">
                        <th className="px-4 py-2 font-medium">Day</th>
                        <th className="px-4 py-2 font-medium">Goal</th>
                        <th className="px-4 py-2 font-medium">Evidence</th>
                        <th className="px-4 py-2 font-medium">Status</th>
                        <th className="px-4 py-2 font-medium">Credit</th>
                        <th className="px-4 py-2 font-medium">Why</th>
                        <th className="px-4 py-2 font-medium"></th>
                      </tr>
                    </thead>
                    <tbody>
                      {ledger.map((event, i) => (
                        <tr key={event.id} className={i === 0 ? "" : "border-t hairline"}>
                          <td className="px-4 py-2">{formatDay(event.localDate)}</td>
                          <td className="px-4 py-2">{event.goalName}</td>
                          <td className="px-4 py-2 text-muted">{evidenceDescription(event)}</td>
                          <td className="px-4 py-2">
                            <span
                              className={
                                event.status === "ACCEPTED" || event.status === "SELF_REPORTED"
                                  ? "chip chip-tended"
                                  : event.status === "PENDING"
                                    ? "chip chip-tint"
                                    : "chip"
                              }
                            >
                              {STATUS_LABELS[event.status]}
                            </span>
                          </td>
                          <td className="px-4 py-2" style={{ fontVariantNumeric: "tabular-nums" }}>
                            {event.status === "ACCEPTED" || event.status === "SELF_REPORTED"
                              ? "1"
                              : event.status === "PENDING"
                                ? "–"
                                : "0"}
                          </td>
                          <td className="px-4 py-2 text-muted">{event.reason}</td>
                          <td className="px-4 py-2 text-right">
                            {event.questioned ? (
                              <span className="text-[13px] text-muted">Questioned</span>
                            ) : (
                              <form action={questionCredit}>
                                <input type="hidden" name="eventId" value={event.id} />
                                <button type="submit" className="text-[13px] font-medium text-tint-text hover:underline">
                                  Question this &rsaquo;
                                </button>
                              </form>
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </section>
          )}
        </div>
      )}
    </div>
  );
}
