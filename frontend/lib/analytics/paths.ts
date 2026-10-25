import type { CustomEvent } from "./events";

// Named actions that are a PAGE, not a form: seeing the cohort dashboard. The event carries nothing about which cohort (the address has
// its id; the event does not).
export function eventForPath(pathname: string): CustomEvent | null {
  return /^\/cohort\/[^/]+\/?$/.test(pathname) ? "cohort_dashboard_viewed" : null;
}
