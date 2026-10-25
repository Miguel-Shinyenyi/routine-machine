// Staged Prompt 5, part 2. People are identified to the analytics service by their INTERNAL USER ID only, never an email and
// never a name. This is the one place that decides what an identifier may look like: a positive whole number, as text. Anything
// else (a string that happens to be an email, a made-up value, zero, a fraction, NaN) is refused and nothing is identified.
export function distinctIdFor(userId: unknown): string | null {
  return typeof userId === "number" && Number.isSafeInteger(userId) && userId > 0 ? String(userId) : null;
}
