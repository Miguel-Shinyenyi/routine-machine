"use client";

import { useSyncExternalStore } from "react";
import { choose, getDecisionKey, getServerDecisionKey, parseDecisionKey, subscribe } from "@/lib/analytics/consent-store";
import { describeChoice } from "@/lib/analytics/disclosure";

// The "Your choice" box on the privacy page (staged Prompt 5, part 2). It shows the visitor's current choice in plain words and lets them
// change it at any time, so a visitor who declined once, or accepted once, is never stuck with it. It reads the same store as the consent
// banner (lib/analytics/consent-store.ts). When the browser itself says no (Do Not Track or Global Privacy Control) the buttons are off,
// because that signal wins and the page says so.
export function AnalyticsChoice() {
  const { state, reason } = parseDecisionKey(useSyncExternalStore(subscribe, getDecisionKey, getServerDecisionKey));
  const browserSaysNo = reason === "Do Not Track" || reason === "Global Privacy Control";

  return (
    <div className="card flex flex-col gap-3 p-4" aria-live="polite">
      <p className="text-[15px] font-semibold" data-testid="analytics-choice-state">
        {describeChoice(state, reason)}
      </p>
      <div className="flex gap-2">
        <button
          type="button"
          disabled={browserSaysNo}
          aria-pressed={state === "granted" && !browserSaysNo}
          onClick={() => choose("accepted")}
          className="flex-1 rounded-[10px] border hairline bg-surface px-4 py-2 text-[14px] font-semibold disabled:opacity-50"
        >
          Count my visits
        </button>
        <button
          type="button"
          disabled={browserSaysNo}
          aria-pressed={state === "declined" && !browserSaysNo}
          onClick={() => choose("declined")}
          className="flex-1 rounded-[10px] border hairline bg-surface px-4 py-2 text-[14px] font-semibold disabled:opacity-50"
        >
          Do not count my visits
        </button>
      </div>
    </div>
  );
}
