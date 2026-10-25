// The only events this app ever sends to the analytics service (staged Prompt 5; docs/frontend.md, "Analytics"). Anything not on
// this list is dropped just before it leaves the browser (lib/analytics/sanitize.ts), including PostHog's own automatic events.
// `$pageview` and `$identify` are PostHog's names for a page opened and for tying events to the internal user id. The eight
// others are named by the prompt, and none carries a private value: a later part wires each one up and states what it carries.
export const ALLOWED_EVENTS: ReadonlySet<string> = new Set([
  "$pageview",
  "$identify",
  "goal_created",
  "day_logged",
  "credit_questioned",
  "reading_note_submitted",
  "pause_logged",
  "hillside_joined",
  "hillside_left",
  "cohort_dashboard_viewed",
]);
