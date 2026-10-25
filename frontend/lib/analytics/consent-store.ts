import {
  CONSENT_STORAGE_KEY,
  decideConsent,
  parseStoredChoice,
  readSignals,
  type Choice,
  type ConsentState,
} from "./consent";

// The visitor's analytics choice as an external store for React (useSyncExternalStore), shared by the consent banner and the privacy page's
// "your choice" box so they can never disagree. Browser only. localStorage fires "storage" only in OTHER tabs, so choose() tells this
// tab's listeners itself (the same arrangement as ThemeToggle).
const listeners = new Set<() => void>();

export function subscribe(onStoreChange: () => void): () => void {
  listeners.add(onStoreChange);
  window.addEventListener("storage", onStoreChange);
  return () => {
    listeners.delete(onStoreChange);
    window.removeEventListener("storage", onStoreChange);
  };
}

// The snapshot is one string, "state|reason", so it is stable between renders (a new object each time would loop).
export function getDecisionKey(): string {
  let stored: Choice | null = null;
  try {
    stored = parseStoredChoice(localStorage.getItem(CONSENT_STORAGE_KEY));
  } catch {
    // Blocked storage: the choice cannot be remembered, so it is asked again each visit.
  }
  const { state, reason } = decideConsent({
    stored,
    signals: readSignals(navigator, window as unknown as { doNotTrack?: string | null }),
  });
  return `${state}|${reason}`;
}

// On the server nothing is known, and nothing is shown: no banner and no analytics.
export function getServerDecisionKey(): string {
  return "declined|server";
}

export function parseDecisionKey(key: string): { state: ConsentState; reason: string } {
  const [state, ...rest] = key.split("|");
  return { state: state as ConsentState, reason: rest.join("|") };
}

export function choose(choice: Choice): void {
  try {
    localStorage.setItem(CONSENT_STORAGE_KEY, choice);
  } catch {
    // Not remembered; the choice still applies until the page is closed.
  }
  listeners.forEach((listener) => listener());
}
