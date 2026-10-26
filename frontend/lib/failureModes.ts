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

// The weekday the weekly question starts showing, chosen on the Profile page (default Monday). The names are the backend's DayOfWeek names.
export const PROMPT_DAYS: readonly { value: string; label: string }[] = [
  { value: "MONDAY", label: "Monday" },
  { value: "TUESDAY", label: "Tuesday" },
  { value: "WEDNESDAY", label: "Wednesday" },
  { value: "THURSDAY", label: "Thursday" },
  { value: "FRIDAY", label: "Friday" },
  { value: "SATURDAY", label: "Saturday" },
  { value: "SUNDAY", label: "Sunday" },
];

export function isPromptDay(value: unknown): value is string {
  return typeof value === "string" && PROMPT_DAYS.some((day) => day.value === value);
}

// Monday = 0 ... Sunday = 6 for a calendar date, or null when it is not a real date. Built from UTC so a time zone never moves the day.
function weekdayIndex(isoDate: string): number | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(isoDate);
  if (!match) return null;
  const [year, month, day] = [Number(match[1]), Number(match[2]), Number(match[3])];
  const date = new Date(Date.UTC(year, month - 1, day));
  if (date.getUTCFullYear() !== year || date.getUTCMonth() !== month - 1 || date.getUTCDate() !== day) return null;
  return (date.getUTCDay() + 6) % 7;
}

// Is the weekly question due on this date? It opens on the person's chosen weekday and stays open through Sunday; the next Monday it
// closes again (a new week). When it cannot tell (an unknown day name or a bad date) it says yes, so the question is never lost.
export function questionOpen(promptDay: string | undefined, isoDate: string): boolean {
  const start = PROMPT_DAYS.findIndex((day) => day.value === promptDay);
  const today = weekdayIndex(isoDate);
  if (start < 0 || today === null) return true;
  return today >= start;
}

export type WeeklyCard = { kind: "ask" } | { kind: "answered"; label: string } | { kind: "hidden" };

// What Today shows. The question once it is due and this week has no answer; the answer (changeable) once there is one, whatever the day;
// nothing at all if it could not be read, so a failed call never blocks the page.
export function weeklyCard(current: FailureModeCurrent | null, due: { promptDay: string | undefined; isoDate: string }): WeeklyCard {
  if (current === null) return { kind: "hidden" };
  if (current.answered && isFailureMode(current.failureMode)) return { kind: "answered", label: failureModeLabel(current.failureMode) };
  return questionOpen(due.promptDay, due.isoDate) ? { kind: "ask" } : { kind: "hidden" };
}
