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
