// The only events this app ever sends to the analytics service (staged Prompt 5; docs/frontend.md, "Analytics"). Anything not on this
// list is dropped just before it leaves the browser (lib/analytics/sanitize.ts), including PostHog's own automatic events.
// `$pageview` and `$identify` are PostHog's names for a page opened and for tying events to the internal user id. The eight named
// actions below are named by the prompt; each has a CLOSED schema of the properties it may carry, and nothing outside it can be sent.
export const CUSTOM_EVENTS = [
  "goal_created",
  "day_logged",
  "credit_questioned",
  "reading_note_submitted",
  "pause_logged",
  "hillside_joined",
  "hillside_left",
  "cohort_dashboard_viewed",
] as const;

export type CustomEvent = (typeof CUSTOM_EVENTS)[number];

export const ALLOWED_EVENTS: ReadonlySet<string> = new Set(["$pageview", "$identify", ...CUSTOM_EVENTS]);

// Each property an event may carry, with the only values it may have. Every property listed is required. A property whose name looks
// like amount, text, note, email or name is not allowed to exist (lib/analytics/named-events.test.ts fails the build if one is added).
// `day_logged` carries only where the log came from; the browser can only produce a tap, so "manual" is the one value for now.
export const EVENT_PROPERTIES: Record<CustomEvent, Record<string, readonly string[]>> = {
  goal_created: {},
  day_logged: { source: ["manual"] },
  credit_questioned: {},
  reading_note_submitted: {},
  pause_logged: {},
  hillside_joined: {},
  hillside_left: {},
  cohort_dashboard_viewed: {},
};

// Checks a named action and its properties against the schema, and returns a clean copy, or null if anything is off: an unknown name,
// a property that is not in the schema, a value that is not on its list, or a required property missing. Nothing is "fixed up": an
// event that is not exactly right is not sent.
export function buildEvent(
  name: string,
  properties: Record<string, unknown> = {},
): { name: CustomEvent; properties: Record<string, string> } | null {
  if (!Object.hasOwn(EVENT_PROPERTIES, name)) return null;
  const schema = EVENT_PROPERTIES[name as CustomEvent];
  const given = Object.keys(properties);
  if (given.some((key) => !Object.hasOwn(schema, key))) return null;
  const clean: Record<string, string> = {};
  for (const [key, allowed] of Object.entries(schema)) {
    const value = properties[key];
    if (typeof value !== "string" || !allowed.includes(value)) return null;
    clean[key] = value;
  }
  return { name: name as CustomEvent, properties: clean };
}
