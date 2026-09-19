import "./viz-tokens.css";
import type { Streak } from "@/lib/types";

const ROW_HEIGHT = 28;
const LABEL_WIDTH = 120;
const CHART_WIDTH = 400;

// Form: before -> after per item -> dumbbell, one hue in two shades (references/choosing-a-form.md
// / marks-and-anatomy.md). "Before" here is longest streak (the reference point), "after" is the
// current streak (what matters most today), so current gets the darker, more prominent shade.
export function StreakChart({ streaks }: { streaks: Streak[] }) {
  if (streaks.length === 0) {
    return <p className="text-sm text-black/50 dark:text-white/50">No routine items yet.</p>;
  }

  const maxStreak = Math.max(1, ...streaks.map((s) => s.longestStreak));
  const scale = (value: number) => (value / maxStreak) * (CHART_WIDTH - LABEL_WIDTH - 40);

  return (
    <div className="viz-root" style={{ background: "var(--surface)" }}>
      <div className="mb-2 flex items-center gap-4 text-xs" style={{ color: "var(--text-secondary)" }}>
        <span className="flex items-center gap-1">
          <span
            className="inline-block h-2 w-2 rounded-full"
            style={{ background: "var(--sequential-light)" }}
          />
          Longest
        </span>
        <span className="flex items-center gap-1">
          <span className="inline-block h-2 w-2 rounded-full" style={{ background: "var(--sequential-dark)" }} />
          Current
        </span>
      </div>

      <svg
        viewBox={`0 0 ${CHART_WIDTH} ${streaks.length * ROW_HEIGHT}`}
        width="100%"
        height={streaks.length * ROW_HEIGHT}
        role="img"
        aria-label="Current versus longest streak per routine item"
      >
        {streaks.map((streak, index) => {
          const y = index * ROW_HEIGHT + ROW_HEIGHT / 2;
          const x1 = LABEL_WIDTH + scale(streak.currentStreak);
          const x2 = LABEL_WIDTH + scale(streak.longestStreak);
          return (
            <g key={streak.routineItemId}>
              <text
                x={0}
                y={y}
                dy="0.32em"
                fontSize={11}
                fill="var(--text-secondary)"
              >
                {streak.name}
              </text>
              <line x1={x1} y1={y} x2={x2} y2={y} stroke="var(--baseline)" strokeWidth={2} />
              <circle cx={x2} cy={y} r={5} fill="var(--sequential-light)" stroke="var(--surface)" strokeWidth={2}>
                <title>{`${streak.name}: longest streak ${streak.longestStreak} days`}</title>
              </circle>
              <circle cx={x1} cy={y} r={5} fill="var(--sequential-dark)" stroke="var(--surface)" strokeWidth={2}>
                <title>{`${streak.name}: current streak ${streak.currentStreak} days`}</title>
              </circle>
              <text x={x2 + 10} y={y} dy="0.32em" fontSize={10} fill="var(--text-muted)">
                {streak.longestStreak}
              </text>
            </g>
          );
        })}
      </svg>

      <table className="mt-4 w-full text-xs" style={{ color: "var(--text-secondary)" }}>
        <caption className="sr-only">Streak table</caption>
        <thead>
          <tr className="text-left">
            <th className="font-normal">Routine item</th>
            <th className="font-normal">Current streak</th>
            <th className="font-normal">Longest streak</th>
          </tr>
        </thead>
        <tbody>
          {streaks.map((streak) => (
            <tr key={streak.routineItemId}>
              <td>{streak.name}</td>
              <td>{streak.currentStreak}</td>
              <td>{streak.longestStreak}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
