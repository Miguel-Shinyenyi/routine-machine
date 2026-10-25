// Staged Prompt 5, part 1 (docs/frontend.md, "Analytics"). Whether this visitor may be counted. Pure, so each rule can be tested.
// The rule is: nothing is captured, and the analytics library is not even loaded, until the visitor accepts. A visitor who
// declines is not tracked at all (PostHog's cookieless mode would still count them, so it is not used). Do Not Track and Global
// Privacy Control are a "no" given through the browser: they win over an earlier "accept", and the visitor is not asked.

export type Choice = "accepted" | "declined";
export type ConsentState = "granted" | "declined" | "ask";

export const CONSENT_STORAGE_KEY = "rm_analytics_consent";

// Only the two values this app writes count; anything else in storage is treated as no choice made.
export function parseStoredChoice(raw: string | null | undefined): Choice | null {
  return raw === "accepted" || raw === "declined" ? raw : null;
}

export interface Signals {
  doNotTrack: boolean;
  globalPrivacyControl: boolean;
}

interface NavigatorLike {
  doNotTrack?: string | null;
  msDoNotTrack?: string | null;
  globalPrivacyControl?: boolean;
}

// Do Not Track has lived in several places over the years (navigator, the older IE property, window). "1" and "yes" mean
// "do not track me"; "0", "no" and "unspecified" do not. Global Privacy Control counts only when it is exactly true.
export function readSignals(nav: NavigatorLike, win?: { doNotTrack?: string | null }): Signals {
  const dntValues = [nav.doNotTrack, nav.msDoNotTrack, win?.doNotTrack];
  return {
    doNotTrack: dntValues.some((value) => value === "1" || value === "yes"),
    globalPrivacyControl: nav.globalPrivacyControl === true,
  };
}

export function decideConsent(input: { stored: Choice | null; signals: Signals }): { state: ConsentState; reason: string } {
  if (input.signals.doNotTrack) return { state: "declined", reason: "Do Not Track" };
  if (input.signals.globalPrivacyControl) return { state: "declined", reason: "Global Privacy Control" };
  if (input.stored === "accepted") return { state: "granted", reason: "accepted earlier" };
  if (input.stored === "declined") return { state: "declined", reason: "declined earlier" };
  return { state: "ask", reason: "no choice yet" };
}

// Analytics is on only when the deployment has been given both a project key and a host. Without them there is nothing to
// consent to, so no banner is shown and nothing is loaded. The host must be a web address: it is where events are sent.
export function analyticsConfigured(key: string | undefined, host: string | undefined): boolean {
  if (!key || key.trim() === "" || !host) return false;
  return /^https?:\/\/[^\s/]+/i.test(host.trim());
}
