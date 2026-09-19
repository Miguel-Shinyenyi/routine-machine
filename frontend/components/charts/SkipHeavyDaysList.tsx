import "./viz-tokens.css";
import type { SkipHeavyDay } from "@/lib/types";

// Form: a handful of flagged event days, not a magnitude/trend/identity job -> not a chart
// (references/choosing-a-form.md). A list with a status badge fits; the warning color is never
// the only signal, it always ships with the "Skip-heavy" text label per the status-color rule.
export function SkipHeavyDaysList({ days }: { days: SkipHeavyDay[] }) {
  if (days.length === 0) {
    return <p className="text-sm text-black/50 dark:text-white/50">No skip-heavy days yet.</p>;
  }

  return (
    <div className="viz-root" style={{ background: "var(--surface)" }}>
      <ul className="flex flex-col gap-2 text-sm">
        {days.map((day) => (
          <li key={day.date} className="flex items-center gap-2">
            <span
              className="rounded px-2 py-0.5 text-xs font-medium"
              style={{ background: "var(--status-warning)", color: "#0b0b0b" }}
            >
              ⚠ Skip-heavy
            </span>
            <span style={{ color: "var(--text-primary)" }}>{day.date}</span>
            <span style={{ color: "var(--text-muted)" }}>
              {day.completedCount}/{day.totalItems} completed ({Math.round(day.skipRatio * 100)}% skipped)
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}
