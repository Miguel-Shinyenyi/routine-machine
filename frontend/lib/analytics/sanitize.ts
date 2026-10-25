import type { CaptureResult } from "posthog-js";
import { ALLOWED_EVENTS } from "./events";

// Runs on every event just before it leaves the browser (PostHog's before_send). Staged Prompt 5, part 1; found by looking at what
// a real accepted visit sends. Three rules: only events named in events.ts leave; every URL loses its query string and fragment
// (in this app they can hold ids); the page title is dropped; and so are the person-property fields. Returning null means "do not send".

// Every property PostHog fills with an address the visitor was on or came from.
const URL_PROPERTIES = ["$current_url", "$referrer", "$session_entry_url", "$session_entry_referrer"];

// An address without its query string and fragment. A value that is not a full address (PostHog uses "$direct" for none) is
// cut at the first ? or # instead, so nothing after them can survive whatever the shape.
export function stripUrl(value: string): string {
  const cut = value.search(/[?#]/);
  return cut === -1 ? value : value.slice(0, cut);
}

export function sanitizeEvent(event: CaptureResult): CaptureResult | null {
  if (!ALLOWED_EVENTS.has(event.event)) return null;
  const properties: Record<string, unknown> = { ...event.properties };
  for (const name of URL_PROPERTIES) {
    if (typeof properties[name] === "string") properties[name] = stripUrl(properties[name] as string);
  }
  delete properties.title;
  const result: CaptureResult = { ...event, properties };
  // Person properties are never used and never sent: PostHog attaches them to events, with full URLs in them ($initial_current_url)
  // and anything an app might one day set about a person.
  delete result.$set;
  delete result.$set_once;
  delete result.$unset;
  return result;
}
