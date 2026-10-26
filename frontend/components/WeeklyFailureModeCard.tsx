import { answerFailureMode } from "@/lib/actions";
import { FAILURE_MODES, weeklyCard, type FailureModeCurrent } from "@/lib/failureModes";

function Choices() {
  return (
    <form action={answerFailureMode} className="mt-3 grid grid-cols-1 gap-2 sm:grid-cols-2">
      {FAILURE_MODES.map((mode) => (
        <button
          key={mode.value}
          type="submit"
          name="failureMode"
          value={mode.value}
          className="rounded-[12px] border hairline bg-surface px-3 py-2 text-left text-[14px] hover:bg-fill"
        >
          <span className="font-semibold">{mode.label}</span>
          <span className="block text-[13px] text-muted">{mode.sign}</span>
        </button>
      ))}
    </form>
  );
}

// The weekly question (staged Prompt 7): asked until this week has an answer, then shows it with a way to change it.
export function WeeklyFailureModeCard({ current }: { current: FailureModeCurrent | null }) {
  const card = weeklyCard(current);
  if (card.kind === "hidden") return null;

  if (card.kind === "answered") {
    return (
      <details className="card mb-6 p-4" data-testid="weekly-failure-mode">
        <summary className="cursor-pointer text-[14px]">
          <span className="font-semibold">This week&rsquo;s failure mode: {card.label}.</span>{" "}
          <span className="text-muted">Change it</span>
        </summary>
        <Choices />
      </details>
    );
  }

  return (
    <section className="card mb-6 p-4" data-testid="weekly-failure-mode" aria-labelledby="weekly-failure-mode-title">
      <h2 id="weekly-failure-mode-title" className="text-[17px] font-semibold">
        Which failure mode did you meet this week?
      </h2>
      <p className="mt-1 text-[14px] text-muted">Name it. Naming it is the first step. If none came up, say so.</p>
      <Choices />
    </section>
  );
}
