import { ApiError, backendFetch, intelligenceFetch } from "@/lib/api";
import type { CurrentReading, Goal, Patterns, Tally } from "@/lib/types";
import { GoalHoursChart } from "@/components/charts/GoalHoursChart";
import { CorrelationStatTiles } from "@/components/charts/CorrelationStatTiles";

// A day on the tally stick counts as "shown up" once this many routines were tended.
// Mirrors intelligence-service's TALLY_STICK_THRESHOLD; see docs/intelligence-service.md.
const TALLY_STICK_THRESHOLD = 3;

const STILL_WATERS_DAYS = new Set([0, 6]); // Sunday, Saturday (Date#getUTCDay())

function meterLabel(ratio: number): { label: string; color: string } {
  if (ratio >= 0.8) return { label: "Steady", color: "var(--tended-ink)" };
  if (ratio >= 0.5) return { label: "Settling in", color: "var(--tint-text)" };
  return { label: "Wandering", color: "var(--muted)" };
}

async function fetchCurrentReading(): Promise<CurrentReading | null> {
  try {
    return await backendFetch<CurrentReading>("/api/current-reading");
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) return null;
    throw error;
  }
}

export default async function TallyPage() {
  const [tally, patterns, goals, currentReading] = await Promise.all([
    intelligenceFetch<Tally>("/tally"),
    intelligenceFetch<Patterns>("/patterns"),
    backendFetch<Goal[]>("/api/goals"),
    fetchCurrentReading(),
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

  return (
    <div className="flex flex-col gap-8">
      <h1 className="page-title">The tally.</h1>

      <section className="card p-4">
        <h2 className="section-title mb-3">Tally stick</h2>
        <div className="flex flex-wrap gap-1">
          {tally.daysShownUp.map((day) => {
            const weekday = new Date(`${day.date}T00:00:00Z`).getUTCDay();
            const stillWaters = STILL_WATERS_DAYS.has(weekday);
            return (
              <span
                key={day.date}
                title={day.date}
                className="h-4 w-2 rounded-full"
                style={{
                  background: day.shownUp ? "var(--tint)" : "var(--fill)",
                  border: stillWaters && !day.shownUp ? "1px dashed var(--private-ink)" : "none",
                }}
              />
            );
          })}
        </div>
        <p className="mt-2 text-[13px] text-muted">
          Shown up {tally.daysShownUpCount} of the last {tally.daysShownUp.length} days. A day counts once at
          least {TALLY_STICK_THRESHOLD} routines were tended.
        </p>
      </section>

      <section className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <div className="tile p-4">
          <p className="text-[13px] text-muted">Days shown up</p>
          <p className="text-[28px] font-semibold">{tally.daysShownUpCount}</p>
        </div>
        <div className="tile p-4">
          <p className="text-[13px] text-muted">Hours toward goals</p>
          <p className="text-[28px] font-semibold">{totalHours.toFixed(1)}</p>
        </div>
        <div className="tile p-4">
          <p className="text-[13px] text-muted">Reading progress</p>
          <p className="text-[15px] text-muted">
            {currentReading ? currentReading.title : "Not tracked yet"}
          </p>
        </div>
        <div className="tile p-4">
          <p className="text-[13px] text-muted">Learning notes</p>
          <p className="text-[15px] text-muted">Not tracked yet (Phase 7)</p>
        </div>
      </section>

      <section>
        <h2 className="section-title mb-3">Cumulative hours per goal</h2>
        <div className="card p-4">
          <GoalHoursChart weeks={tally.goalWeekHours} goals={goals} />
        </div>
      </section>

      <section>
        <h2 className="section-title mb-3">Per-routine meters</h2>
        <div className="card flex flex-col p-2">
          {tally.routineShownUp.map((routine) => {
            const ratio = routine.windowDays === 0 ? 0 : routine.shownUpDays / routine.windowDays;
            const meter = meterLabel(ratio);
            return (
              <div key={routine.routineItemId} className="flex items-center gap-3 border-b hairline px-2 py-2 last:border-b-0">
                <span className="flex-1 text-[15px]">{routine.name}</span>
                <span className="text-[13px]" style={{ color: meter.color }}>
                  {meter.label}
                </span>
                <span className="text-[13px] text-muted">
                  {routine.shownUpDays} of {routine.windowDays}
                </span>
              </div>
            );
          })}
          {tally.routineShownUp.length === 0 && <p className="p-2 text-[13px] text-muted">No routine items yet</p>}
        </div>
      </section>

      <section>
        <h2 className="section-title mb-3">Steep days</h2>
        <div className="card flex flex-col p-2">
          {patterns.skipHeavyDays.map((day) => (
            <div key={day.date} className="flex items-center justify-between border-b hairline px-2 py-2 last:border-b-0 text-[13px]">
              <span>{day.date}</span>
              <span className="text-muted">
                {day.completedCount} of {day.totalItems} tended
              </span>
            </div>
          ))}
          {patterns.skipHeavyDays.length === 0 && <p className="p-2 text-[13px] text-muted">None recently</p>}
        </div>
      </section>

      <section>
        <h2 className="section-title mb-3">Learning topic vs routine completions</h2>
        <CorrelationStatTiles correlation={patterns.topicRoutineCorrelation} />
      </section>
    </div>
  );
}
