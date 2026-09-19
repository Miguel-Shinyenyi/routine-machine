import "./viz-tokens.css";
import type { CompletionRate } from "@/lib/types";

// Form: a ratio against a limit, per item -> a meter per row (references/choosing-a-form.md).
// Track uses the recessive gridline gray rather than a lighter step of the same blue ramp
// (the skill's stated default for a meter): simpler, and safe in both light and dark without
// a second, dark-tuned step of the sequential ramp that palette.md doesn't provide.
export function CompletionRateChart({ rates }: { rates: CompletionRate[] }) {
  if (rates.length === 0) {
    return <p className="text-sm text-black/50 dark:text-white/50">No routine items yet.</p>;
  }

  return (
    <div className="viz-root" style={{ background: "var(--surface)" }}>
      <div className="flex flex-col gap-3">
        {rates.map((rate) => {
          const percent = Math.round(rate.rate * 100);
          return (
            <div key={rate.routineItemId} title={`${rate.name}: ${rate.loggedDays}/${rate.totalDays} days`}>
              <div className="mb-1 flex justify-between text-xs" style={{ color: "var(--text-secondary)" }}>
                <span>{rate.name}</span>
                <span>{percent}%</span>
              </div>
              <div className="h-3 rounded-full" style={{ background: "var(--gridline)" }}>
                <div
                  className="h-3 rounded-full"
                  style={{ width: `${percent}%`, background: "var(--sequential)" }}
                />
              </div>
            </div>
          );
        })}
      </div>

      <table className="mt-4 w-full text-xs" style={{ color: "var(--text-secondary)" }}>
        <caption className="sr-only">Completion rate table</caption>
        <thead>
          <tr className="text-left">
            <th className="font-normal">Routine item</th>
            <th className="font-normal">Logged / total days</th>
            <th className="font-normal">Rate</th>
          </tr>
        </thead>
        <tbody>
          {rates.map((rate) => (
            <tr key={rate.routineItemId}>
              <td>{rate.name}</td>
              <td>
                {rate.loggedDays} / {rate.totalDays}
              </td>
              <td>{Math.round(rate.rate * 100)}%</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
