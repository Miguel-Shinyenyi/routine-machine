import { buildEvent, type CustomEvent } from "./events";

// How the SERVER tells the BROWSER that a named action just happened (staged Prompt 5, part 3). The server never sends an analytics
// event itself: it does not know whether the visitor accepted. After an action succeeds it leaves a short-lived note in a cookie (an
// event name and at most one fixed-value property: nothing private), the page hands the note to the browser, the browser sends it
// if and only if the visitor accepted, and clears the note either way. Everything read back is checked again: a visitor can edit a
// cookie, and an edited note is only ever sent if it is still exactly one of the allowed events.
export const PENDING_COOKIE = "rm_events";
export const PENDING_TTL_MS = 120_000;
const MAX_PENDING = 10;

export interface PendingEvent {
  id: string;
  name: CustomEvent;
  properties: Record<string, string>;
}

interface StoredEvent extends PendingEvent {
  at: number;
}

function readStored(raw: string | undefined, now: number): StoredEvent[] {
  if (!raw) return [];
  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch {
    return [];
  }
  if (!Array.isArray(parsed)) return [];
  const events: StoredEvent[] = [];
  for (const item of parsed) {
    if (!item || typeof item !== "object") continue;
    const { id, name, properties, at } = item as Record<string, unknown>;
    if (typeof id !== "string" || id === "" || typeof name !== "string" || typeof at !== "number") continue;
    if (now - at > PENDING_TTL_MS || at > now + 60_000) continue;
    const built = buildEvent(name, (properties ?? {}) as Record<string, unknown>);
    if (built) events.push({ id, name: built.name, properties: built.properties, at });
  }
  return events;
}

export function parsePending(raw: string | undefined, now: number): PendingEvent[] {
  return readStored(raw, now).map(({ id, name, properties }) => ({ id, name, properties }));
}

// The cookie value after adding one event to what is already there. An event that is not allowed is not added.
export function appendPending(
  raw: string | undefined,
  event: { name: string; properties: Record<string, unknown> },
  now: number,
  id: string,
): string {
  const current = readStored(raw, now);
  const built = buildEvent(event.name, event.properties);
  if (built) current.push({ id, name: built.name, properties: built.properties, at: now });
  return JSON.stringify(current.slice(-MAX_PENDING));
}
