"use client";

import Link from "next/link";
import { useEffect, useSyncExternalStore } from "react";
import { identifyUser, startAnalytics, stopAnalytics } from "@/lib/analytics/client";
import { choose, getDecisionKey, getServerDecisionKey, parseDecisionKey, subscribe } from "@/lib/analytics/consent-store";

// Staged Prompt 5, part 1 (docs/frontend.md, "Analytics"). Asks once, remembers the answer, and starts the analytics library
// only after "Accept". Rendered by the root layout only when the deployment has a PostHog key and host; with neither, nothing
// here exists. The rules themselves are in lib/analytics/consent.ts, where they are tested.

// userId is the signed-in person's INTERNAL id, or null when signed out. It is the only thing the analytics service is ever told about
// who someone is (lib/analytics/identity.ts): never an email, never a name.
export function AnalyticsConsent({ posthogKey, posthogHost, userId }: { posthogKey: string; posthogHost: string; userId: number | null }) {
  const { state } = parseDecisionKey(useSyncExternalStore(subscribe, getDecisionKey, getServerDecisionKey));

  useEffect(() => {
    if (state === "granted") {
      void startAnalytics(posthogKey, posthogHost);
    } else if (state === "declined") {
      void stopAnalytics();
    }
  }, [state, posthogKey, posthogHost]);

  // Runs after the effect above, so on the first render the library has already been asked to start. Does nothing unless analytics
  // is running, so nothing is identified before consent or after a visitor withdraws.
  useEffect(() => {
    if (state === "granted") {
      void identifyUser(userId);
    }
  }, [state, userId]);

  if (state !== "ask") return null;

  return (
    <section
      aria-label="Analytics choice"
      className="card fixed inset-x-3 bottom-24 z-50 flex flex-col gap-3 p-4 shadow-lg sm:inset-x-auto sm:bottom-4 sm:right-4 sm:max-w-[380px]"
    >
      <p className="text-[14px]">
        May we count how this app is used, to improve it? If you accept, we send which pages you open, and your internal user number if
        you are signed in, to PostHog, our analytics service. We never send what you write, any amount, your email or your name. If you
        decline, nothing is sent.{" "}
        <Link href="/privacy" className="text-tint-text underline">
          What we count, and how to change your mind
        </Link>
        .
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
