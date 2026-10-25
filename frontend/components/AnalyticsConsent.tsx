"use client";

import { useEffect, useSyncExternalStore } from "react";
import { startAnalytics, stopAnalytics } from "@/lib/analytics/client";
import {
  CONSENT_STORAGE_KEY,
  decideConsent,
  parseStoredChoice,
  readSignals,
  type Choice,
  type ConsentState,
} from "@/lib/analytics/consent";

// Staged Prompt 5, part 1 (docs/frontend.md, "Analytics"). Asks once, remembers the answer, and starts the analytics library
// only after "Accept". Rendered by the root layout only when the deployment has a PostHog key and host; with neither, nothing
// here exists. The rules themselves are in lib/analytics/consent.ts, where they are tested.

// localStorage fires "storage" only in other tabs, so choose() tells this tab's listeners itself (the same arrangement as
// ThemeToggle).
const listeners = new Set<() => void>();

function subscribe(onStoreChange: () => void) {
  listeners.add(onStoreChange);
  window.addEventListener("storage", onStoreChange);
  return () => {
    listeners.delete(onStoreChange);
    window.removeEventListener("storage", onStoreChange);
  };
}

function getSnapshot(): ConsentState {
  let stored: Choice | null = null;
  try {
    stored = parseStoredChoice(localStorage.getItem(CONSENT_STORAGE_KEY));
  } catch {
    // Blocked storage: the choice cannot be remembered, so it is asked again each visit.
  }
  return decideConsent({ stored, signals: readSignals(navigator, window as unknown as { doNotTrack?: string | null }) }).state;
}

// On the server nothing is known, and nothing is shown: no banner and no analytics.
function getServerSnapshot(): ConsentState {
  return "declined";
}

function choose(choice: Choice) {
  try {
    localStorage.setItem(CONSENT_STORAGE_KEY, choice);
  } catch {
    // Not remembered; the choice still applies until the page is closed.
  }
  listeners.forEach((listener) => listener());
}

export function AnalyticsConsent({ posthogKey, posthogHost }: { posthogKey: string; posthogHost: string }) {
  const state = useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot);

  useEffect(() => {
    if (state === "granted") {
      void startAnalytics(posthogKey, posthogHost);
    } else if (state === "declined") {
      void stopAnalytics();
    }
  }, [state, posthogKey, posthogHost]);

  if (state !== "ask") return null;

  return (
    <section
      aria-label="Analytics choice"
      className="card fixed inset-x-3 bottom-24 z-50 flex flex-col gap-3 p-4 shadow-lg sm:inset-x-auto sm:bottom-4 sm:right-4 sm:max-w-[380px]"
    >
      <p className="text-[14px]">
        May we count how this app is used, to improve it? If you accept, we send which pages you open and a few named actions to
        PostHog, our analytics service. We never send what you write, any amount or anyone&rsquo;s name. If you decline, nothing is
        sent.
      </p>
      <div className="flex gap-2">
        <button
          type="button"
          onClick={() => choose("accepted")}
          className="flex-1 rounded-[10px] border hairline bg-surface px-4 py-2 text-[14px] font-semibold"
        >
          Accept
        </button>
        <button
          type="button"
          onClick={() => choose("declined")}
          className="flex-1 rounded-[10px] border hairline bg-surface px-4 py-2 text-[14px] font-semibold"
        >
          Decline
        </button>
      </div>
    </section>
  );
}
