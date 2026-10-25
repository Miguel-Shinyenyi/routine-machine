import { cookies } from "next/headers";
import { analyticsConfigured } from "./consent";
import { buildEvent, type CustomEvent } from "./events";
import { appendPending, parsePending, PENDING_COOKIE, PENDING_TTL_MS, type PendingEvent } from "./pending";

// Server side of staged Prompt 5, part 3 (docs/frontend.md, "Analytics"). Called by a Server Action AFTER it has succeeded. The server
// never sends an analytics event itself, because it does not know whether the visitor accepted (that choice lives in their browser).
// It leaves a short-lived note instead: an event name and at most one fixed-value property, never anything from the form. The root layout
// hands the note to the browser, which sends it only if the visitor accepted and clears it either way. Leaves nothing when analytics is
// off, and nothing for an event that is not exactly one of the allowed shapes.
export async function queueAnalyticsEvent(name: CustomEvent, properties: Record<string, unknown> = {}): Promise<void> {
  if (!analyticsConfigured(process.env.POSTHOG_KEY, process.env.POSTHOG_HOST)) return;
  if (!buildEvent(name, properties)) return;
  const store = await cookies();
  const value = appendPending(store.get(PENDING_COOKIE)?.value, { name, properties }, Date.now(), crypto.randomUUID());
  // Readable by the page on purpose (httpOnly off): the browser clears it after reading. It holds an event name and nothing private, it
  // is never sent to a third party, and it expires by itself in two minutes.
  store.set(PENDING_COOKIE, value, {
    path: "/",
    maxAge: PENDING_TTL_MS / 1000,
    sameSite: "lax",
    httpOnly: false,
    secure: process.env.NODE_ENV === "production",
  });
}

// The notes waiting for the browser, read again and checked (a visitor can edit a cookie). Called by the root layout. Nothing unless analytics
// is configured. Kept here, not in the layout, because it reads the clock, and a page is not allowed to.
export async function readPendingEvents(): Promise<PendingEvent[]> {
  if (!analyticsConfigured(process.env.POSTHOG_KEY, process.env.POSTHOG_HOST)) return [];
  return parsePending((await cookies()).get(PENDING_COOKIE)?.value, Date.now());
}
