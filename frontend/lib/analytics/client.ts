import { buildEvent } from "./events";
import { distinctIdFor } from "./identity";
import { posthogOptions } from "./options";

// The only place the analytics library is loaded (staged Prompt 5, part 1). It is imported dynamically, and only after a visitor
// has accepted, so before that the library's code is not even downloaded and no request goes to the analytics host. Browser only.
type PostHog = (typeof import("posthog-js"))["default"];

// The library is started once per page and kept, even after a visitor withdraws, so that accepting again opts back in on the same
// instance (a second init would be ignored by the library).
let started: Promise<PostHog> | null = null;
// True after a visitor withdrew, until they accept again: nothing is identified in between.
let stopped = false;
// Who the library has been told about, so the same person is not identified twice in a row.
let identifiedAs: string | null = null;

export function startAnalytics(key: string, host: string): Promise<PostHog> {
  const comingBack = stopped;
  stopped = false;
  if (!started) {
    started = import("posthog-js").then((module) => {
      module.default.init(key.trim(), posthogOptions(host));
      return module.default;
    });
  } else if (comingBack) {
    // Came back after stopping: the library is already loaded and opted out, so opt it back in. (Called again while it is
    // running, nothing happens: opting in again would be sent as an extra $opt_in event.)
    started = started.then((posthog) => {
      posthog.opt_in_capturing();
      return posthog;
    });
  }
  return started;
}

// A visitor who withdraws consent, or whose browser starts sending Do Not Track or Global Privacy Control: stop capturing and forget
// the identifier. Does nothing if it never started. THE ORDER MATTERS (found live, lib/analytics/client.test.ts): the library's
// reset() also clears its consent setting back to the default, which is "opted in", so it must come FIRST and the opt-out after;
// the other way round, the visitor who just said no would be captured again under a fresh identifier.
export async function stopAnalytics(): Promise<void> {
  if (!started) return;
  stopped = true;
  identifiedAs = null;
  const posthog = await started;
  posthog.reset();
  posthog.opt_out_capturing();
}

// Staged Prompt 5, part 2. Tell the library who the signed-in person is, by internal user id ALONE: no email, no name, and no person
// properties (identify is called with the id and nothing else). A signed-out visitor (null) is forgotten, if anyone was identified.
// Does nothing, and loads nothing, unless analytics is running: not before consent, and not after a visitor withdrew.
export async function identifyUser(userId: unknown): Promise<void> {
  if (!started || stopped) return;
  const posthog = await started;
  if (userId === null || userId === undefined) {
    if (identifiedAs !== null) {
      identifiedAs = null;
      posthog.reset();
    }
    return;
  }
  const id = distinctIdFor(userId);
  if (id === null || id === identifiedAs) return;
  identifiedAs = id;
  posthog.identify(id);
}

// Staged Prompt 5, part 3. Send one of the eight named actions, with only the properties its schema allows (lib/analytics/events.ts).
// Does nothing, and loads nothing, unless analytics is running: not before consent, and not after a visitor withdrew. An event that is not
// exactly one of the allowed shapes is not sent, so a private value cannot go out even by mistake.
export async function track(name: string, properties?: Record<string, unknown>): Promise<void> {
  if (!started || stopped) return;
  const event = buildEvent(name, properties);
  if (!event) return;
  const posthog = await started;
  posthog.capture(event.name, event.properties);
}
