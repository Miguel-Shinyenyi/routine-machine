import Link from "next/link";
import { ApiError, backendFetch, intelligenceFetch } from "@/lib/api";
import { requireSessionUserId } from "@/lib/auth/session";
import { getUserToday } from "@/lib/auth/userToday";
import { steepDayInsight, learningInsight } from "@/lib/tallyInsights";
import type { Book, Goal, Patterns, Tally } from "@/lib/types";
import { GoalHoursChart } from "@/components/charts/GoalHoursChart";

// A day on the tally stick counts as "shown up" once this many routines were tended.
// Mirrors intelligence-service's TALLY_STICK_THRESHOLD; see docs/intelligence-service.md.
const TALLY_STICK_THRESHOLD = 3;

function meterLabel(ratio: number): { label: string; chipClass: string } {
  if (ratio >= 0.8) return { label: "Steady", chipClass: "chip-tended" };
  if (ratio >= 0.5) return { label: "Settling in", chipClass: "" };
  return { label: "Wandering", chipClass: "chip-tint" };
}

async function fetchCurrentBook(): Promise<Book | null> {
  try {
    return await backendFetch<Book>("/api/books/current");
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) return null;
    throw error;
  }
}

export default async function TallyPage(props: PageProps<"/tally">) {
  await requireSessionUserId();
  const today = await getUserToday();
  const searchParams = await props.searchParams;
  // "4 weeks" is the default (matches /tally's own default windowDays=28); "Since the start"
  // asks for a window wide enough to include every day ever logged, same trick Path's own
  // week navigation uses of encoding state in the URL rather than client component state.
  const sinceStart = searchParams.window === "all";
  const windowDays = sinceStart ? 3650 : 28;

  const [tally, patterns, goals, book] = await Promise.all([
    intelligenceFetch<Tally>(`/tally?windowDays=${windowDays}&sinceStart=${sinceStart}`),
    intelligenceFetch<Patterns>("/patterns"),
    backendFetch<Goal[]>("/api/goals"),
    fetchCurrentBook(),
  ]);

  const latestWeekStartByGoal = new Map<number, string>();
  const latestHoursByGoal = new Map<number, number>();
  for (const week of tally.goalWeekHours) {
    const latestWeekStart = latestWeekStartByGoal.get(week.goalId);
    if (latestWeekStart === undefined || week.weekStart > latestWeekStart) {
      latestWeekStartByGoal.set(week.goalId, week.weekStart);
      latestHoursByGoal.set(week.goalId, week.cumulativeHours);
    }
  }
  const totalHours = Array.from(latestHoursByGoal.values()).reduce((a, b) => a + b, 0);
  const sinceDate = tally.daysShownUp[0]?.date;

  // Grouped in fives, matching a shepherd's tally stick in the design reference.
  const notchGroups: typeof tally.daysShownUp[] = [];
  for (let i = 0; i < tally.daysShownUp.length; i += 5) {
    notchGroups.push(tally.daysShownUp.slice(i, i + 5));
  }

  const steepDays = steepDayInsight(patterns.skipHeavyDays);
  const learning = learningInsight(patterns.topicRoutineCorrelation);

  return (
    <div className="flex flex-col gap-8">
      <div className="flex flex-wrap items-end justify-between gap-5">
        <div className="min-w-0 flex-1 basis-[420px]">
          {sinceDate && <p className="text-[15px] font-semibold text-tint-text">Since {sinceDate}</p>}
          <h1 className="page-title">The tally.</h1>
          <p className="mt-2 max-w-[62ch] text-[17px] text-muted">
            A shepherd cut one notch for every sheep that came home. Each notch here is a day you
            showed up. Small days count the same as big ones.
          </p>
        </div>
        <div className="inline-flex gap-0.5 rounded-[9px] p-0.5" style={{ background: "var(--fill)" }}>
          <Link
            href="/tally"
            aria-pressed={!sinceStart}
            className="rounded-[7px] px-3.5 py-1.5 text-[13px] font-medium text-ink"
            style={!sinceStart ? { background: "var(--surface)", fontWeight: 600, boxShadow: "0 3px 8px rgba(0,0,0,.12), 0 3px 1px rgba(0,0,0,.04)" } : undefined}
          >
            4 weeks
          </Link>
          <Link
            href="/tally?window=all"
            aria-pressed={sinceStart}
            className="rounded-[7px] px-3.5 py-1.5 text-[13px] font-medium text-ink"
            style={sinceStart ? { background: "var(--surface)", fontWeight: 600, boxShadow: "0 3px 8px rgba(0,0,0,.12), 0 3px 1px rgba(0,0,0,.04)" } : undefined}
          >
            Since the start
          </Link>
        </div>
      </div>

      <section className="card p-4">
        <div className="flex flex-wrap items-baseline justify-between gap-2">
          <h2 className="section-title">
            {tally.daysShownUpCount} notches in {tally.daysShownUp.length} days.
          </h2>
          <span className="text-[14px] text-muted">
            A day counts when you tend at least {TALLY_STICK_THRESHOLD} of your flock. Dashed: you rested.
          </span>
        </div>
        <div
          className="mt-3.5 flex flex-wrap items-end gap-x-[18px] gap-y-3.5"
          role="img"
          aria-label={`Tally stick: ${tally.daysShownUpCount} days shown up out of ${tally.daysShownUp.length}`}
        >
          {notchGroups.map((group, gi) => (
            <div key={gi} className="flex items-end gap-[5px]">
              {group.map((day) => {
                const isToday = day.date === today;
                return (
                  <span
                    key={day.date}
                    title={day.date}
                    className="h-[38px] w-[5px] rounded-[3px]"
                    style={
                      day.shownUp
                        ? { background: isToday ? "var(--tint)" : "var(--ink)" }
                        : { background: "transparent", border: "1.5px dashed rgba(118,118,128,.5)" }
                    }
                  />
                );
              })}
            </div>
          ))}
        </div>
      </section>

      <section className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <div className="card p-4">
          <p className="text-[13px] text-muted">Days shown up</p>
          <p className="text-[28px] font-semibold">
            {tally.daysShownUpCount} <small className="text-[17px] font-medium text-muted">of {tally.daysShownUp.length}</small>
          </p>
        </div>
        <div className="card p-4">
          <p className="text-[13px] text-muted">Hours toward goals</p>
          <p className="text-[28px] font-semibold">{totalHours.toFixed(1)}</p>
        </div>
        <div className="card p-4">
          <p className="text-[13px] text-muted">Reading progress</p>
          <p className="text-[15px] text-muted">{book ? book.title : "Not tracked yet"}</p>
        </div>
        <div className="card p-4">
          <p className="text-[13px] text-muted">Learning notes</p>
          <p className="text-[15px] text-muted">Not tracked yet (Phase 7)</p>
        </div>
      </section>

      <section className="card p-4">
        <div className="flex flex-wrap items-baseline justify-between gap-2">
          <h2 className="section-title">The green pastures are closer.</h2>
          <span className="text-[14px] text-muted">Cumulative hours per goal, by week</span>
        </div>
        <p className="mt-1.5 max-w-[62ch] text-[15px] text-muted">
          No single week looks like much. Seven of them stacked do. The lines only ever go up: a
          rest day flattens them, it never takes anything away.
        </p>
        <GoalHoursChart weeks={tally.goalWeekHours} goals={goals} />
      </section>

      <div className="flex flex-wrap items-start gap-5">
        <section className="min-w-0 flex-[999_1_520px]">
          <h2 className="section-title mb-2.5">How each sheep is doing.</h2>
          <div className="overflow-x-auto rounded-[14px]">
            <div className="min-w-[560px] overflow-hidden rounded-[14px] bg-surface">
              {tally.routineShownUp.map((routine, i) => {
                const ratio = routine.windowDays === 0 ? 0 : routine.shownUpDays / routine.windowDays;
                const meter = meterLabel(ratio);
                return (
                  <div
                    key={routine.routineItemId}
                    className={`grid items-center gap-3.5 px-4 py-2 text-[14px] ${i === 0 ? "" : "border-t hairline"}`}
                    style={{ gridTemplateColumns: "minmax(140px,1fr) minmax(160px,2fr) 70px minmax(110px,1fr)" }}
                  >
                    <strong className="font-semibold">{routine.name}</strong>
                    <div className="h-2.5 overflow-hidden rounded-[5px]" style={{ background: "#e8e8ed" }}>
                      <div
                        className="h-full rounded-[5px]"
                        style={{ width: `${Math.round(ratio * 100)}%`, background: "var(--ink)" }}
                      />
                    </div>
                    <span>
                      {routine.shownUpDays} of {routine.windowDays}
                    </span>
                    <span className={`chip self-start ${meter.chipClass}`}>{meter.label}</span>
                  </div>
                );
              })}
              {tally.routineShownUp.length === 0 && <p className="p-4 text-[13px] text-muted">No routines yet. Add one in the Flock and each shows here.</p>}
            </div>
          </div>
          <p className="mt-2 px-0.5 text-[13px] text-muted">
            Counted against the days each routine was on the path, not every day. No streak
            counters: a missed day never resets anything.
          </p>
        </section>

        <aside className="flex min-w-0 flex-[1_1_300px] flex-col gap-4">
          <div className="card p-4">
            <span className="chip chip-tint self-start">Steep days</span>
            <h2 className="mt-2 text-[17px] font-semibold">{steepDays.headline}</h2>
            <p className="mt-1.5 text-[14px] text-muted">{steepDays.detail}</p>
          </div>
          <div className="card p-4">
            <span className="chip self-start">Learning and the rest</span>
            <h2 className="mt-2 text-[17px] font-semibold">{learning.headline}</h2>
            <p className="mt-1.5 text-[14px] text-muted">{learning.detail}</p>
          </div>
        </aside>
      </div>
    </div>
  );
}
