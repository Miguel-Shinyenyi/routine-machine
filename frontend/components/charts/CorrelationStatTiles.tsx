import "./viz-tokens.css";
import type { TopicRoutineCorrelation } from "@/lib/types";

// Form: a single comparison of two headline numbers -> a stat-tile pair, not a chart
// (references/choosing-a-form.md: "a handful of headline numbers -> KPI row of stat tiles").
export function CorrelationStatTiles({ correlation }: { correlation: TopicRoutineCorrelation }) {
  return (
    <div className="viz-root grid grid-cols-2 gap-4" style={{ background: "var(--surface)" }}>
      <div className="rounded border p-3" style={{ borderColor: "var(--gridline)" }}>
        <p className="text-xs" style={{ color: "var(--text-secondary)" }}>
          Avg. routine completions on days with a learning-topic log ({correlation.daysWithTopicLog} days)
        </p>
        <p className="mt-1 text-2xl font-semibold" style={{ color: "var(--text-primary)" }}>
          {correlation.avgRoutineCompletionsOnTopicLogDays.toFixed(1)}
        </p>
      </div>
      <div className="rounded border p-3" style={{ borderColor: "var(--gridline)" }}>
        <p className="text-xs" style={{ color: "var(--text-secondary)" }}>
          Avg. routine completions on other days ({correlation.daysWithoutTopicLog} days)
        </p>
        <p className="mt-1 text-2xl font-semibold" style={{ color: "var(--text-primary)" }}>
          {correlation.avgRoutineCompletionsOnOtherDays.toFixed(1)}
        </p>
      </div>
    </div>
  );
}
