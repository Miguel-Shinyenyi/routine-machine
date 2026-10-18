import type { TopicRoutineCorrelation } from "@/lib/types";

// Not in the design reference (this comparison predates the Phase 5 shepherd redesign), so
// it's styled to match this app's own card/stat-tile tokens rather than left on the separate
// dataviz-skill palette it was originally built with, which didn't match anything else here.
export function CorrelationStatTiles({ correlation }: { correlation: TopicRoutineCorrelation }) {
  return (
    <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
      <div className="card p-4">
        <p className="text-[13px] text-muted">
          Avg. routine completions on days with a learning-topic log ({correlation.daysWithTopicLog} days)
        </p>
        <p className="text-[28px] font-semibold">{correlation.avgRoutineCompletionsOnTopicLogDays.toFixed(1)}</p>
      </div>
      <div className="card p-4">
        <p className="text-[13px] text-muted">
          Avg. routine completions on other days ({correlation.daysWithoutTopicLog} days)
        </p>
        <p className="text-[28px] font-semibold">{correlation.avgRoutineCompletionsOnOtherDays.toFixed(1)}</p>
      </div>
    </div>
  );
}
