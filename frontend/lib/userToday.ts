// "Today" as the person lives it (Backlog P). The server's own clock is UTC, so for someone in Nairobi (UTC+3) the server's date is still
// yesterday from midnight to 3 a.m. The date here comes from the person's saved time zone. A missing or unreal zone means UTC, never a
// failed page.
export function todayInZone(timeZone: string | null | undefined, now: Date = new Date()): string {
  return formatDate(timeZone, now) ?? formatDate("UTC", now) ?? now.toISOString().slice(0, 10);
}

function formatDate(timeZone: string | null | undefined, now: Date): string | null {
  if (!timeZone) return null;
  try {
    const parts = new Intl.DateTimeFormat("en-US", { timeZone, year: "numeric", month: "2-digit", day: "2-digit" }).formatToParts(now);
    const get = (type: string) => parts.find((part) => part.type === type)?.value;
    const [year, month, day] = [get("year"), get("month"), get("day")];
    return year && month && day ? `${year}-${month}-${day}` : null;
  } catch {
    return null;
  }
}
