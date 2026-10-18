import type { Goal, GoalWeekHours } from "@/lib/types";

const LINE_COLORS = ["var(--goal-line-1)", "var(--goal-line-2)", "var(--goal-line-3)"];

// Matches docs/design-reference/pages/tally.html's chart exactly: gridlines + axis labels,
// a line per goal, and an inline end-of-line dot + label naming the goal and its current
// total — not a separate legend table underneath.
export function GoalHoursChart({ weeks, goals }: { weeks: GoalWeekHours[]; goals: Goal[] }) {
  const goalNameById = new Map(goals.map((g) => [g.id, g.name]));
  const goalIds = Array.from(new Set(weeks.map((w) => w.goalId))).sort((a, b) => a - b);
  const allWeekStarts = Array.from(new Set(weeks.map((w) => w.weekStart))).sort();

  if (goalIds.length === 0 || allWeekStarts.length === 0) {
    return <p className="text-[13px] text-muted">No hours logged against a goal yet.</p>;
  }

  const width = 900;
  const height = 250;
  const left = 40;
  const right = 620;
  const top = 20;
  const bottom = 210;
  const maxHours = Math.max(1, ...weeks.map((w) => w.cumulativeHours));
  // Round the axis ceiling up to a clean multiple of 20, same step the reference uses.
  const axisMax = Math.max(20, Math.ceil(maxHours / 20) * 20);

  const xFor = (i: number) => left + (i / Math.max(1, allWeekStarts.length - 1)) * (right - left);
  const yFor = (hours: number) => bottom - (hours / axisMax) * (bottom - top);

  const seriesByGoal = goalIds.map((goalId) => {
    const byWeek = new Map(weeks.filter((w) => w.goalId === goalId).map((w) => [w.weekStart, w.cumulativeHours]));
    let lastValue = 0;
    const points = allWeekStarts.map((weekStart, i) => {
      if (byWeek.has(weekStart)) lastValue = byWeek.get(weekStart)!;
      return [xFor(i), yFor(lastValue)] as const;
    });
    return { goalId, points, lastValue };
  });

  const yTicks = [0, axisMax / 4, axisMax / 2, (axisMax * 3) / 4, axisMax];

  return (
    <svg
      viewBox={`0 0 ${width} ${height}`}
      width="100%"
      role="img"
      aria-label={`Cumulative hours: ${seriesByGoal
        .map((s) => `${goalNameById.get(s.goalId) ?? `Goal ${s.goalId}`} ${s.lastValue.toFixed(1)}`)
        .join(", ")} after ${allWeekStarts.length} weeks`}
      style={{ display: "block", maxWidth: 820 }}
    >
      <g stroke="var(--hairline)" strokeWidth={1}>
        {yTicks.map((tick) => (
          <path key={tick} d={`M${left} ${yFor(tick)}H${right}`} />
        ))}
      </g>
      <g fontSize={11} fill="var(--muted)">
        {yTicks.map((tick) => (
          <text key={tick} x={left - 8} y={yFor(tick) + 4} textAnchor="end">
            {tick === 0 ? "0" : `${tick} h`}
          </text>
        ))}
        {allWeekStarts.map((weekStart, i) => (
          <text key={weekStart} x={xFor(i)} y={bottom + 22} textAnchor="middle">
            {i === 0 ? "Wk 1" : i + 1}
          </text>
        ))}
      </g>
      {seriesByGoal.map((series, i) => (
        <polyline
          key={series.goalId}
          points={series.points.map(([x, y]) => `${x},${y}`).join(" ")}
          fill="none"
          stroke={LINE_COLORS[i % LINE_COLORS.length]}
          strokeWidth={2.5}
          strokeLinejoin="round"
        />
      ))}
      <g fill="var(--surface)" strokeWidth={2}>
        {seriesByGoal.map((series, i) => {
          const [x, y] = series.points[series.points.length - 1];
          return <circle key={series.goalId} cx={x} cy={y} r={4} stroke={LINE_COLORS[i % LINE_COLORS.length]} />;
        })}
      </g>
      <g fontSize={12} fontWeight={600}>
        {seriesByGoal.map((series, i) => {
          const [, y] = series.points[series.points.length - 1];
          const name = goalNameById.get(series.goalId) ?? `Goal ${series.goalId}`;
          return (
            <text key={series.goalId} x={right + 10} y={y + 4} fill={LINE_COLORS[i % LINE_COLORS.length]}>
              {name} &middot; {series.lastValue.toFixed(1)} h
            </text>
          );
        })}
      </g>
    </svg>
  );
}
