import { posthogOptions } from "./options";

// The only place the analytics library is loaded (staged Prompt 5, part 1). It is imported dynamically, and only after a visitor
// has accepted, so before that the library's code is not even downloaded and no request goes to the analytics host. Browser only.
type PostHog = (typeof import("posthog-js"))["default"];

// The library is started once per page and kept, even after a visitor withdraws, so that accepting again opts back in on the same
// instance (a second init would be ignored by the library).
let started: Promise<PostHog> | null = null;

export function startAnalytics(key: string, host: string): Promise<PostHog> {
  if (!started) {
    started = import("posthog-js").then((module) => {
      module.default.init(key.trim(), posthogOptions(host));
      return module.default;
    });
  } else {
    // Came back after stopping: the library is already loaded and opted out, so opt it back in.
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
  const posthog = await started;
  posthog.reset();
  posthog.opt_out_capturing();
}
