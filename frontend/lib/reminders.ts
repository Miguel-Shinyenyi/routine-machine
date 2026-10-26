// The daily slow-down reminder (staged Prompt 7). The text lives in content/reminders.txt so it can be edited without a code change.

export function parseReminders(text: string): string[] {
  return text
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line !== "" && !line.startsWith("#"));
}

const ISO_DATE = /^(\d{4})-(\d{2})-(\d{2})$/;

// Whole days since 1970-01-01 for a calendar date, or null when it is not a real date. Built from UTC so the machine's time zone and
// daylight saving can never skip or repeat a day.
function dayNumber(isoDate: string): number | null {
  const match = ISO_DATE.exec(isoDate);
  if (!match) return null;
  const [year, month, day] = [Number(match[1]), Number(match[2]), Number(match[3])];
  const ms = Date.UTC(year, month - 1, day);
  const check = new Date(ms);
  if (check.getUTCFullYear() !== year || check.getUTCMonth() !== month - 1 || check.getUTCDate() !== day) return null;
  return Math.floor(ms / 86_400_000);
}

// One reminder for a date: the same all day, the next one tomorrow, wrapping after the last. null when there is nothing to show.
export function reminderFor(reminders: string[], isoDate: string): string | null {
  const day = dayNumber(isoDate);
  if (day === null || reminders.length === 0) return null;
  return reminders[((day % reminders.length) + reminders.length) % reminders.length];
}
