// Staged Prompt 5, part 3. The names a captured field may NOT have: anything that is, or contains as a whole word, amount, text,
// note, email or name. Matching is by whole words (split on punctuation and camelCase), so "userEmail", "note_text" and "amountKes" are
// caught, and PostHog's own "$pathname", and ordinary words that merely contain those letters ("namespace", "context"), are not.
const FORBIDDEN_WORDS = new Set(["amount", "amounts", "text", "texts", "note", "notes", "email", "emails", "name", "names"]);

export function isForbiddenField(key: string): boolean {
  const words = key
    .replace(/([a-z0-9])([A-Z])/g, "$1 $2")
    .toLowerCase()
    .split(/[^a-z0-9]+/)
    .filter(Boolean);
  return words.some((word) => FORBIDDEN_WORDS.has(word));
}
