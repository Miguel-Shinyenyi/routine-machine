// UTC-based throughout, same reasoning as schedule.ts's dayOfWeekName: a server-local
// timezone must never shift which calendar day a date string refers to.
function parseUtc(isoDate: string): Date {
  const [year, month, day] = isoDate.split("-").map(Number);
  return new Date(Date.UTC(year, month - 1, day));
}

function toIso(date: Date): string {
  return date.toISOString().slice(0, 10);
}

export function addDays(isoDate: string, deltaDays: number): string {
  const date = parseUtc(isoDate);
  date.setUTCDate(date.getUTCDate() + deltaDays);
  return toIso(date);
}

// Monday of the week containing isoDate. getUTCDay() is 0=Sunday..6=Saturday; Monday is 1,
// so Sunday (0) is 6 days after the preceding Monday, every other day is (weekday - 1) days
// after it.
export function mondayOf(isoDate: string): string {
  const date = parseUtc(isoDate);
  const weekday = date.getUTCDay();
  const daysSinceMonday = weekday === 0 ? 6 : weekday - 1;
  return addDays(isoDate, -daysSinceMonday);
}

export function weekDates(mondayIsoDate: string): string[] {
  return Array.from({ length: 7 }, (_, i) => addDays(mondayIsoDate, i));
}
