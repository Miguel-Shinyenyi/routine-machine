import type { ConsentState } from "./consent";
import type { CustomEvent } from "./events";
import { eventForPath } from "./paths";
import type { PendingEvent } from "./pending";

// What the browser does with the notes the server left, and with the page it is on (staged Prompt 5, part 3). Pure, so each rule is tested.

// A note is sent ONLY if the visitor accepted, each at most once, and the notes are cleared in every case: a note that was not sent
// (the visitor declined, or had not chosen) must not linger, or it could be sent later after a different choice.
export function planPending(input: {
  pending: PendingEvent[];
  state: ConsentState;
  sentIds: ReadonlySet<string>;
}): { send: PendingEvent[]; clear: boolean } {
  if (input.pending.length === 0) return { send: [], clear: false };
  const send = input.state === "granted" ? input.pending.filter((event) => !input.sentIds.has(event.id)) : [];
  return { send, clear: true };
}

// A named action that is a page: once per visit to it, for a visitor who accepted, never while they stay on it.
export function shouldTrackPath(input: { state: ConsentState; pathname: string; lastPathname: string | null }): CustomEvent | null {
  if (input.state !== "granted" || input.pathname === input.lastPathname) return null;
  return eventForPath(input.pathname);
}
