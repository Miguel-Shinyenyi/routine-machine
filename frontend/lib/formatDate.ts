import { addDays } from "./weekDates";

const WEEKDAYS = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"];
const MONTHS = [
  "January", "February", "March", "April", "May", "June",
  "July", "August", "September", "October", "November", "December",
];

// UTC-based, matching today/page.tsx's existing adjacentDate: avoids the server's own
// timezone shifting an ISO date (YYYY-MM-DD, no time) to the wrong calendar day. Built from
// fixed name arrays, not Intl.DateTimeFormat, so the "Weekday, day Month" order (matching
// docs/design-reference/pages/today.html's "Friday, 16 October") doesn't depend on a
// locale's own formatting choice.
export function formatEyebrowDate(isoDate: string): string {
  const [year, month, day] = isoDate.split("-").map(Number);
  const date = new Date(Date.UTC(year, month - 1, day));
  return `${WEEKDAYS[date.getUTCDay()]}, ${date.getUTCDate()} ${MONTHS[date.getUTCMonth()]}`;
}

// The Path's week label (Backlog V): a Monday and the Sunday six days later. "19 to 25 October" inside one month; both months named when the
// week crosses a month ("26 October to 1 November"); both years named when it crosses new year. UTC-based, like formatEyebrowDate.
export function formatWeekRange(monday: string): string {
  const start = parts(monday);
  const end = parts(addDays(monday, 6));
  if (start.year !== end.year) {
    return `${start.day} ${MONTHS[start.month]} ${start.year} to ${end.day} ${MONTHS[end.month]} ${end.year}`;
  }
  if (start.month !== end.month) {
    return `${start.day} ${MONTHS[start.month]} to ${end.day} ${MONTHS[end.month]}`;
  }
  return `${start.day} to ${end.day} ${MONTHS[end.month]}`;
}

function parts(isoDate: string): { year: number; month: number; day: number } {
  const [year, month, day] = isoDate.split("-").map(Number);
  return { year, month: month - 1, day };
}

