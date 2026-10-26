// The daily slow-down reminder (staged Prompt 7). The text lives in content/reminders.txt so it can be edited without a code change.

const STRONGER_HEADING = /^\[after missed days\]$/i;

// The file has two lists. Lines before a "[after missed days]" heading are the daily reminders; lines after it are shown instead when the
// person has not shown up for a few days. The heading is not a reminder.
export function parseReminderFile(text: string): { daily: string[]; stronger: string[] } {
  const daily: string[] = [];
  const stronger: string[] = [];
  let target = daily;
  for (const raw of text.split(/\r?\n/)) {
    const line = raw.trim();
    if (line === "" || line.startsWith("#")) continue;
    if (STRONGER_HEADING.test(line)) {
      target = stronger;
      continue;
    }
    target.push(line);
  }
  return { daily, stronger };
}

export function parseReminders(text: string): string[] {
  return parseReminderFile(text).daily;
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

const MISSED_WINDOW_DAYS = 3;

// True when the person has shown up on some earlier day, and not on any of the MISSED_WINDOW_DAYS days just before isoDate. Someone who has
// never shown up is not "missing" anything yet, and days missing from the record are not guessed at, so both give false.
export function missedRecently(days: { date: string; shownUp: boolean }[], isoDate: string): boolean {
  const today = dayNumber(isoDate);
  if (today === null) return false;
  const byDay = new Map<number, boolean>();
  for (const entry of days) {
    const n = dayNumber(entry.date);
    if (n !== null && n < today) byDay.set(n, entry.shownUp);
  }
  for (let back = 1; back <= MISSED_WINDOW_DAYS; back++) {
    const shown = byDay.get(today - back);
    if (shown === undefined || shown) return false;
  }
  return [...byDay.values()].some((shown) => shown);
}

// After missed days, a stronger reminder (from its own list, rotated by date the same way); otherwise the daily one.
export function chooseReminder(input: { daily: string[]; stronger: string[]; isoDate: string; missed: boolean }): string | null {
  if (input.missed && input.stronger.length > 0) return reminderFor(input.stronger, input.isoDate);
  return reminderFor(input.daily, input.isoDate);
}
