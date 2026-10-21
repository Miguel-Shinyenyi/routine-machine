import Link from "next/link";
import { materializeDay } from "@/lib/materialize";
import { todayIsoDate } from "@/lib/schedule";
import { formatEyebrowDate } from "@/lib/formatDate";
import { authenticatedBackendFetch, backendFetch, intelligenceFetch } from "@/lib/api";
import { getSessionToken, requireSessionUserId } from "@/lib/auth/session";
import { withUserId } from "@/lib/scope";
import { decidePause, reflectPause } from "@/lib/actions";
import type { AnalogyResponse, Goal, Patterns, PauseDecision, Profile, RoutineItem, Suggestion, Tally } from "@/lib/types";
import { TaskCard } from "@/components/TaskCard";

// Inline placeholder, same role as docs/design-reference/README.md's "Not in the reference":
// "The mascot: ... a simple inline SVG placeholder. The full illustrated shepherd lives in
// the UMWAYI site illustrations."
function ShepherdMascot() {
  return (
    <svg viewBox="0 0 120 150" width="40" height="50" aria-hidden="true" className="shrink-0">
      <path d="M92 146V44q0-18-14-18-12 0-12 12" fill="none" stroke="#7a4a1e" strokeWidth="6" strokeLinecap="round" />
      <path d="M30 146L40 74Q60 60 80 74L90 146Z" fill="#3e7b4f" stroke="#1d1d1f" strokeWidth="3" />
      <path d="M44 74L84 124L78 132L38 86Z" fill="#c8372d" stroke="#1d1d1f" strokeWidth="3" />
      <circle cx="60" cy="50" r="17" fill="#6b4226" stroke="#1d1d1f" strokeWidth="3" />
      <circle cx="90" cy="92" r="7" fill="#6b4226" stroke="#1d1d1f" strokeWidth="3" />
    </svg>
  );
}

function adjacentDate(isoDate: string, deltaDays: number): string {
  const [year, month, day] = isoDate.split("-").map(Number);
  const date = new Date(Date.UTC(year, month - 1, day));
  date.setUTCDate(date.getUTCDate() + deltaDays);
  return date.toISOString().slice(0, 10);
}

export default async function TodayPage(props: PageProps<"/today">) {
  const searchParams = await props.searchParams;
  const userId = await requireSessionUserId();
  const rawDate = searchParams.date;
  const date = typeof rawDate === "string" ? rawDate : todayIsoDate();

  const sessionToken = await getSessionToken();
  const profile = sessionToken
    ? await authenticatedBackendFetch<Profile>("/api/users/me/profile", sessionToken).catch(() => null)
    : null;

  const [tasks, routineItems, goals, suggestion, tally, patterns, duePauses] = await Promise.all([
    materializeDay(date, userId),
    backendFetch<RoutineItem[]>("/api/routine-items"),
    backendFetch<Goal[]>("/api/goals"),
    intelligenceFetch<Suggestion>(withUserId("/suggestion", userId)).catch(() => null),
    intelligenceFetch<Tally>(withUserId("/tally", userId)).catch(() => null),
    intelligenceFetch<Patterns>(withUserId("/patterns", userId)).catch(() => null),
    backendFetch<PauseDecision[]>("/api/granary/pauses/due").catch(() => []),
  ]);

  // Phase 10: explained through the first interest the user picked on their Profile page,
  // only when they've left the switch on and a topic actually exists to explain — see
  // docs/product-direction.md's Profile section.
  const analogyInterest = profile?.analogiesEnabled ? profile.interests?.[0] : undefined;
  const analogy =
    analogyInterest && suggestion
      ? await intelligenceFetch<AnalogyResponse>("/analogy", {
          method: "POST",
          body: JSON.stringify({ topic: suggestion.topic.name, interest: analogyInterest }),
        }).catch(() => null)
      : null;

  const routineItemById = new Map(routineItems.map((item) => [item.id, item]));
  const doneCount = tasks.filter((t) => t.status === "DONE").length;
  const progress = tasks.length === 0 ? 0 : Math.round((doneCount / tasks.length) * 100);
  const goalById = new Map(goals.map((g) => [g.id, g.name]));

  const latestWeekStartByGoal = new Map<number, string>();
  const latestHoursByGoal = new Map<number, number>();
  if (tally) {
    for (const week of tally.goalWeekHours) {
      const latestWeekStart = latestWeekStartByGoal.get(week.goalId);
      if (latestWeekStart === undefined || week.weekStart > latestWeekStart) {
        latestWeekStartByGoal.set(week.goalId, week.weekStart);
        latestHoursByGoal.set(week.goalId, week.cumulativeHours);
      }
    }
  }

  const maxGoalHours = Math.max(1, ...Array.from(latestHoursByGoal.values()));
  const lastWandered = patterns?.skipHeavyDays.at(-1);

  return (
    <div>
      <div className="mb-6 flex flex-wrap items-end justify-between gap-5">
        <div className="min-w-0 flex-1 basis-[420px]">
          <p className="text-[15px] font-semibold text-tint-text">{formatEyebrowDate(date)}</p>
          <h1 className="page-title">Today&rsquo;s pasture.</h1>
          <p className="mt-2 max-w-[62ch] text-[17px] text-muted">
            {tasks.length === 0
              ? "Nothing scheduled yet. The tally keeps every one you tend."
              : `${tasks.length} small ${tasks.length === 1 ? "thing" : "things"}. None of them is big, and each one counts. The tally keeps every one you tend.`}
          </p>
        </div>
        {suggestion && (
          <div className="glass flex basis-[380px] items-start gap-3 rounded-[18px] p-4" role="status">
            <ShepherdMascot />
            <div className="min-w-0">
              <div className="flex gap-2 text-[12px] text-muted">
                <strong className="font-semibold text-ink">Shepherd</strong>
                <span>now</span>
              </div>
              <p className="mt-0.5 text-[15px] leading-tight">{suggestion.reason}</p>
            </div>
          </div>
        )}
      </div>

      {duePauses.length > 0 && (
        <div className="mb-6 flex flex-col gap-3">
          {duePauses.map((pause) => (
            <div key={pause.id} className="glass flex items-start gap-3 rounded-[18px] p-4">
              <ShepherdMascot />
              <div className="min-w-0 flex-1">
                <div className="flex gap-2 text-[12px] text-muted">
                  <strong className="font-semibold text-ink">Shepherd</strong>
                  <span>about {pause.what}</span>
                </div>
                {pause.dueForTwentyFourHourFollowUp ? (
                  <>
                    <p className="mt-0.5 text-[15px] leading-tight">
                      You wrote this down yesterday. Have you decided?
                    </p>
                    <div className="mt-2 flex flex-wrap gap-2">
                      <form action={decidePause}>
                        <input type="hidden" name="pauseId" value={pause.id} />
                        <input type="hidden" name="outcome" value="DECLINED" />
                        <button type="submit" className="rounded-full px-3.5 py-1.5 text-[13px] font-semibold" style={{ background: "var(--tended-fill)", color: "var(--tended-ink)" }}>
                          Declined
                        </button>
                      </form>
                      <form action={decidePause}>
                        <input type="hidden" name="pauseId" value={pause.id} />
                        <input type="hidden" name="outcome" value="PROCEEDED" />
                        <button type="submit" className="rounded-full px-3.5 py-1.5 text-[13px] font-semibold" style={{ background: "var(--fill)" }}>
                          Proceeded
                        </button>
                      </form>
                    </div>
                  </>
                ) : (
                  <>
                    <p className="mt-0.5 text-[15px] leading-tight">
                      Thirty days on — looking back, how do you feel about it?
                    </p>
                    <form action={reflectPause} className="mt-2 flex flex-wrap gap-2">
                      <input type="hidden" name="pauseId" value={pause.id} />
                      <input name="reflection" required placeholder="A short note…" className="field w-[220px]" />
                      <button type="submit" className="rounded-full px-3.5 py-1.5 text-[13px] font-semibold btn-fill">
                        Save
                      </button>
                    </form>
                  </>
                )}
              </div>
            </div>
          ))}
        </div>
      )}

      <div className="mb-6 flex items-center justify-between text-[13px]">
        <Link href={`/today?date=${adjacentDate(date, -1)}`} className="text-tint-text hover:underline">
          &larr; Previous day
        </Link>
        <Link href={`/today?date=${adjacentDate(date, 1)}`} className="text-tint-text hover:underline">
          Next day &rarr;
        </Link>
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-[1fr_320px]">
        <div className="flex flex-col gap-3">
          <div className="flex flex-wrap items-baseline justify-between gap-3">
            <h2 className="text-[20px] font-semibold">Your flock today</h2>
            {tasks.length > 0 && (
              <span className="text-[14px] text-muted">
                {doneCount} of {tasks.length} tended &middot; in the order you walk them
              </span>
            )}
          </div>
          <div className="h-2 w-full overflow-hidden rounded-[4px]" style={{ background: "#e8e8ed" }}>
            <div className="h-full rounded-[4px]" style={{ width: `${progress}%`, background: "var(--tint)" }} />
          </div>

          <div className="card p-2">
            {tasks.length === 0 && <p className="p-3 text-[13px] text-muted">Nothing scheduled yet.</p>}
            {tasks.map((task) => (
              <TaskCard
                key={task.id}
                task={task}
                routineItem={task.routineItemId ? routineItemById.get(task.routineItemId) : undefined}
              />
            ))}
          </div>
          {tasks.length > 0 && (
            <p className="px-0.5 text-[13px] text-muted">
              Rows with a source chip fill themselves in. You only tap the ones nothing can see.
            </p>
          )}
        </div>

        <div className="flex flex-col gap-4">
          <div className="card p-4">
            <h2 className="section-title">Closer than yesterday.</h2>
            <p className="mb-3 mt-1.5 text-[14px] text-muted">
              Hours given to each green pasture since you started.
            </p>
            {latestHoursByGoal.size === 0 && <p className="text-[13px] text-muted">No hours logged yet.</p>}
            <div className="flex flex-col gap-3">
              {Array.from(latestHoursByGoal.entries()).map(([goalId, hours], i) => {
                // No real per-goal target exists to measure a bar against, so each bar is
                // sized relative to the goal with the most hours here, not a fabricated cap.
                const width = Math.round((hours / maxGoalHours) * 100);
                return (
                  <div key={goalId} className={i === 0 ? "" : "border-t hairline pt-3"}>
                    <div className="flex items-baseline justify-between text-[14px]">
                      <strong className="font-semibold">{goalById.get(goalId) ?? `Goal ${goalId}`}</strong>
                      <span>{hours.toFixed(1)} h</span>
                    </div>
                    <div className="mt-1.5 h-2 overflow-hidden rounded-[4px]" style={{ background: "#e8e8ed" }}>
                      <div className="h-full rounded-[4px]" style={{ width: `${width}%`, background: "var(--tint)" }} />
                    </div>
                  </div>
                );
              })}
            </div>
            {latestHoursByGoal.size > 0 && (
              <Link href="/tally" className="mt-2.5 inline-block text-[15px] text-tint-text">
                Open the tally &rsaquo;
              </Link>
            )}
          </div>

          <div className="card flex flex-col gap-2 p-4">
            {suggestion ? (
              <>
                <span className="chip chip-tint self-start">The staff</span>
                <h2 className="text-[17px] font-semibold">{suggestion.topic.name}</h2>
                <p className="text-[14px] text-muted">{suggestion.reason}</p>
                {analogy && (
                  <div className="rounded-[14px] p-3 text-[14px] leading-relaxed" style={{ background: "var(--fill)" }}>
                    <p className="m-0">
                      <strong className="font-semibold">{analogy.analogy}</strong>
                    </p>
                    <p className="mt-1.5 text-[13px] text-muted">{analogy.definition}</p>
                    <p className="mt-1.5 text-[11px] text-muted">
                      An analogy through {analogy.interest}, not the full truth — see{" "}
                      <Link href="/profile" className="text-tint-text hover:underline">
                        Profile
                      </Link>{" "}
                      to turn it off.
                    </p>
                  </div>
                )}
                <div className="mt-1 flex flex-wrap gap-2">
                  {/* Matches the reference's own pair of buttons, which it never wires to a
                      click handler either (app.js only handles Today's checks and Hillside's
                      switches) — there's no dedicated "learning session" page in this app to
                      send "Start learning" to, and no "pick a different topic" endpoint for
                      "Another topic" to call, so both stay visual-only like the reference. */}
                  <button type="button" className="btn-fill rounded-full px-4 py-2 text-[14px] font-semibold">
                    Start learning
                  </button>
                  <button
                    type="button"
                    className="rounded-full px-4 py-2 text-[14px] font-semibold"
                    style={{ background: "var(--fill)", color: "var(--tint-text)" }}
                  >
                    Another topic
                  </button>
                </div>
              </>
            ) : (
              <>
                <h2 className="section-title">The staff</h2>
                <p className="text-[13px] text-muted">No learning topics to suggest yet.</p>
              </>
            )}
          </div>

          <div className="card p-4">
            <h2 className="section-title mb-2">One sheep wandered.</h2>
            {lastWandered ? (
              <p className="text-[13px] text-muted">
                {lastWandered.date}: {lastWandered.completedCount} of {lastWandered.totalItems} tended.
              </p>
            ) : (
              <p className="text-[13px] text-muted">Nothing wandered recently.</p>
            )}
          </div>

          <Link href="/hillside" className="card flex items-center gap-3 p-4 text-ink">
            <svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="var(--tint-text)" strokeWidth="1.8" strokeLinejoin="round" aria-hidden="true">
              <path d="M2 19l7-10 4 5 3-3 6 8z" />
            </svg>
            <span className="min-w-0">
              <strong className="block font-semibold">See where you walk.</strong>
              <span className="text-[13px] text-muted">
                Hillside, anonymous, ranked by days shown up, not by volume.
              </span>
            </span>
            <span className="ml-auto text-tint-text" aria-hidden="true">
              &rsaquo;
            </span>
          </Link>
        </div>
      </div>
    </div>
  );
}
