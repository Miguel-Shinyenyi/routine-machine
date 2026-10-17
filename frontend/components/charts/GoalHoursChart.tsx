import type { Goal, GoalWeekHours } from "@/lib/types";

const LINE_COLORS = ["var(--goal-line-1)", "var(--goal-line-2)", "var(--goal-line-3)"];

export function GoalHoursChart({ weeks, goals }: { weeks: GoalWeekHours[]; goals: Goal[] }) {
  const goalNameById = new Map(goals.map((g) => [g.id, g.name]));
  const goalIds = Array.from(new Set(weeks.map((w) => w.goalId))).sort((a, b) => a - b);
  const allWeekStarts = Array.from(new Set(weeks.map((w) => w.weekStart))).sort();

  if (goalIds.length === 0 || allWeekStarts.length === 0) {
    return <p className="text-[13px] text-muted">No hours logged against a goal yet.</p>;
  }

  const width = 560;
  const height = 220;
  const padding = 24;
  const maxHours = Math.max(1, ...weeks.map((w) => w.cumulativeHours));

  const seriesByGoal = goalIds.map((goalId) => {
    const byWeek = new Map(weeks.filter((w) => w.goalId === goalId).map((w) => [w.weekStart, w.cumulativeHours]));
    let lastValue = 0;
    const points = allWeekStarts.map((weekStart, i) => {
      if (byWeek.has(weekStart)) lastValue = byWeek.get(weekStart)!;
      const x = padding + (i / Math.max(1, allWeekStarts.length - 1)) * (width - padding * 2);
      const y = height - padding - (lastValue / maxHours) * (height - padding * 2);
      return [x, y] as const;
    });
    return { goalId, points, lastValue };
  });

  return (
    <div>
      <svg viewBox={`0 0 ${width} ${height}`} className="w-full" role="img" aria-label="Cumulative hours per goal by week">
        {seriesByGoal.map((series, i) => (
          <polyline
            key={series.goalId}
            points={series.points.map(([x, y]) => `${x},${y}`).join(" ")}
            fill="none"
            stroke={LINE_COLORS[i % LINE_COLORS.length]}
            strokeWidth={2}
          />
        ))}
      </svg>
      <table className="mt-3 w-full text-[13px]">
        <thead>
          <tr className="text-left text-muted">
            <th className="pr-3 font-medium">Goal</th>
            <th className="font-medium">Cumulative hours</th>
          </tr>
        </thead>
        <tbody>
          {seriesByGoal.map((series, i) => (
            <tr key={series.goalId} className="border-t hairline">
              <td className="flex items-center gap-2 py-1 pr-3">
                <span className="inline-block h-2 w-2 rounded-full" style={{ background: LINE_COLORS[i % LINE_COLORS.length] }} />
                {goalNameById.get(series.goalId) ?? `Goal ${series.goalId}`}
              </td>
              <td className="py-1">{series.lastValue.toFixed(1)}h</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
