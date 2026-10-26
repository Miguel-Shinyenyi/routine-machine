// The weekly "Which failure mode did you meet this week?" (staged Prompt 7). The eight modes are those of docs/understanding-first.md, by
// name, each with the short sign from that document; "none this week" is last, a choice of last resort. A test holds the values equal to the
// backend's FailureMode enum and the names equal to the document.
export interface FailureModeChoice {
  value: string;
  label: string;
  sign: string;
}

export const FAILURE_MODES: readonly FailureModeChoice[] = [
  { value: "FORMING", label: "Forming", sign: "Right question, wrong answer." },
  { value: "DISLODGING", label: "Dislodging", sign: "Stuck in a rut." },
  { value: "ASSUMPTION", label: "Assumption", sign: "Wrong question, right answer." },
  { value: "LOCATION", label: "Location", sign: "So close, so far." },
  { value: "ACHIEVEMENT", label: "Achievement", sign: "A band aid on a broken bone." },
  { value: "PROGRESSION", label: "Progression", sign: "Falling behind without noticing." },
  { value: "INTERRUPTION", label: "Interruption", sign: "Someone broke the thought that was finding the goal." },
  { value: "MISLEAD", label: "Mislead", sign: "Suggestions that lead you astray." },
  { value: "NONE", label: "None this week", sign: "None of these came up." },
];

export function isFailureMode(value: unknown): value is string {
  return typeof value === "string" && FAILURE_MODES.some((mode) => mode.value === value);
}

export function failureModeLabel(value: string): string {
  return FAILURE_MODES.find((mode) => mode.value === value)?.label ?? value;
}

export interface FailureModeCurrent {
  weekStart: string;
  answered: boolean;
  failureMode: string | null;
}

export type WeeklyCard = { kind: "ask" } | { kind: "answered"; label: string } | { kind: "hidden" };

// What Today shows. The question until this week has an answer; the answer (changeable) after; nothing at all if it could not be read,
// so a failed call never blocks the page.
export function weeklyCard(current: FailureModeCurrent | null): WeeklyCard {
  if (current === null) return { kind: "hidden" };
  if (current.answered && isFailureMode(current.failureMode)) return { kind: "answered", label: failureModeLabel(current.failureMode) };
  return { kind: "ask" };
}
