import { intelligenceFetch } from "@/lib/api";
import type { Patterns } from "@/lib/types";
import { CompletionRateChart } from "@/components/charts/CompletionRateChart";
import { StreakChart } from "@/components/charts/StreakChart";
import { SkipHeavyDaysList } from "@/components/charts/SkipHeavyDaysList";
import { CorrelationStatTiles } from "@/components/charts/CorrelationStatTiles";

export default async function ReportsPage() {
  const patterns = await intelligenceFetch<Patterns>("/patterns");

  return (
    <div className="flex flex-col gap-10">
      <h1 className="text-lg font-semibold">Reports</h1>

      <section>
        <h2 className="mb-3 text-sm font-medium text-black/60 dark:text-white/60">Completion rates</h2>
        <CompletionRateChart rates={patterns.completionRates} />
      </section>

      <section>
        <h2 className="mb-3 text-sm font-medium text-black/60 dark:text-white/60">Streaks</h2>
        <StreakChart streaks={patterns.streaks} />
      </section>

      <section>
        <h2 className="mb-3 text-sm font-medium text-black/60 dark:text-white/60">Skip-heavy days</h2>
        <SkipHeavyDaysList days={patterns.skipHeavyDays} />
      </section>

      <section>
        <h2 className="mb-3 text-sm font-medium text-black/60 dark:text-white/60">
          Learning topic vs routine completions
        </h2>
        <CorrelationStatTiles correlation={patterns.topicRoutineCorrelation} />
      </section>
    </div>
  );
}
