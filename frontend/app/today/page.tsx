import Link from "next/link";
import { materializeDay } from "@/lib/materialize";
import { todayIsoDate } from "@/lib/schedule";
import { backendFetch, intelligenceFetch } from "@/lib/api";
import type { Goal, Patterns, RoutineItem, Suggestion, Tally } from "@/lib/types";
import { TaskCard } from "@/components/TaskCard";

function adjacentDate(isoDate: string, deltaDays: number): string {
  const [year, month, day] = isoDate.split("-").map(Number);
  const date = new Date(Date.UTC(year, month - 1, day));
  date.setUTCDate(date.getUTCDate() + deltaDays);
  return date.toISOString().slice(0, 10);
}

export default async function TodayPage(props: PageProps<"/today">) {
  const searchParams = await props.searchParams;
  const rawDate = searchParams.date;
  const date = typeof rawDate === "string" ? rawDate : todayIsoDate();

  const [tasks, routineItems, goals, suggestion, tally, patterns] = await Promise.all([
    materializeDay(date),
    backendFetch<RoutineItem[]>("/api/routine-items"),
    backendFetch<Goal[]>("/api/goals"),
    intelligenceFetch<Suggestion>("/suggestion").catch(() => null),
    intelligenceFetch<Tally>("/tally").catch(() => null),
    intelligenceFetch<Patterns>("/patterns").catch(() => null),
  ]);

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

  const lastWandered = patterns?.skipHeavyDays.at(-1);

  return (
    <div>
      <p className="text-[13px] text-muted">{date}</p>
      <h1 className="page-title mb-6">Today&rsquo;s pasture.</h1>

      <div className="mb-6 flex items-center justify-between text-[13px]">
        <Link href={`/today?date=${adjacentDate(date, -1)}`} className="text-tint-text hover:underline">
          &larr; Previous day
        </Link>
        <Link href={`/today?date=${adjacentDate(date, 1)}`} className="text-tint-text hover:underline">
          Next day &rarr;
        </Link>
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-[1fr_320px]">
        <div>
          <div className="card mb-4 p-4">
            <div className="mb-1 flex items-center justify-between text-[13px] text-muted">
              <span>Tended today</span>
              <span>{progress}%</span>
            </div>
            <div className="h-2 w-full overflow-hidden rounded-full" style={{ background: "var(--fill)" }}>
              <div className="h-full rounded-full" style={{ width: `${progress}%`, background: "var(--tint)" }} />
            </div>
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
        </div>

        <div className="flex flex-col gap-4">
          <div className="card p-4">
            <h2 className="section-title mb-3">Closer than yesterday.</h2>
            {latestHoursByGoal.size === 0 && <p className="text-[13px] text-muted">No hours logged yet.</p>}
            <ul className="flex flex-col gap-2 text-[13px]">
              {Array.from(latestHoursByGoal.entries()).map(([goalId, hours]) => (
                <li key={goalId} className="flex justify-between">
                  <span>{goalById.get(goalId) ?? `Goal ${goalId}`}</span>
                  <span className="text-tint-text">{hours.toFixed(1)}h</span>
                </li>
              ))}
            </ul>
          </div>

          <div className="card p-4">
            <h2 className="section-title mb-2">The staff</h2>
            {suggestion ? (
              <>
                <p className="text-[15px]">{suggestion.topic.name}</p>
                <p className="mt-1 text-[13px] text-muted">{suggestion.reason}</p>
              </>
            ) : (
              <p className="text-[13px] text-muted">No learning topics to suggest yet.</p>
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

          <div className="tile p-4 text-[13px] text-muted">
            The hillside isn&rsquo;t built yet &mdash; see Phase 8 in PROJECT.md.
          </div>
        </div>
      </div>
    </div>
  );
}
