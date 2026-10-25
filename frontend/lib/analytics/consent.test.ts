import { describe, expect, it } from "vitest";
import { analyticsConfigured, decideConsent, parseStoredChoice, readSignals } from "./consent";

// Staged Prompt 5, part 1 (docs/frontend.md, "Analytics"): consent comes first. Nothing is captured, and the analytics library
// is not even loaded, until a visitor accepts. A visitor who declines is not tracked. Do Not Track and Global Privacy Control
// are honoured, and win over an earlier "accept".

describe("parseStoredChoice", () => {
  it("reads only the two values this app writes", () => {
    expect(parseStoredChoice("accepted")).toBe("accepted");
    expect(parseStoredChoice("declined")).toBe("declined");
  });
  it("treats anything else as no choice made", () => {
    for (const raw of [null, undefined, "", "yes", "true", "ACCEPTED", " accepted", "{}", "1"]) {
      expect(parseStoredChoice(raw as string | null)).toBeNull();
    }
  });
});

describe("readSignals", () => {
  it("sees Do Not Track in every place a browser has put it", () => {
    expect(readSignals({ doNotTrack: "1" }).doNotTrack).toBe(true);
    expect(readSignals({ doNotTrack: "yes" }).doNotTrack).toBe(true);
    expect(readSignals({ msDoNotTrack: "1" }).doNotTrack).toBe(true);
    expect(readSignals({}, { doNotTrack: "1" }).doNotTrack).toBe(true);
  });
  it("does not mistake an unset or refused Do Not Track for a request", () => {
    for (const value of ["0", "no", "unspecified", null, undefined, ""]) {
      expect(readSignals({ doNotTrack: value as string | null }).doNotTrack).toBe(false);
    }
  });
  it("sees Global Privacy Control only when it is exactly true", () => {
    expect(readSignals({ globalPrivacyControl: true }).globalPrivacyControl).toBe(true);
    expect(readSignals({ globalPrivacyControl: false }).globalPrivacyControl).toBe(false);
    expect(readSignals({}).globalPrivacyControl).toBe(false);
    expect(readSignals({ globalPrivacyControl: "true" as unknown as boolean }).globalPrivacyControl).toBe(false);
  });
});

describe("decideConsent", () => {
  const none = { doNotTrack: false, globalPrivacyControl: false };

  it("asks when there is no signal and no earlier choice", () => {
    expect(decideConsent({ stored: null, signals: none })).toEqual({ state: "ask", reason: "no choice yet" });
  });
  it("remembers an earlier choice", () => {
    expect(decideConsent({ stored: "accepted", signals: none }).state).toBe("granted");
    expect(decideConsent({ stored: "declined", signals: none }).state).toBe("declined");
  });
  it("lets Do Not Track and Global Privacy Control win, even over an earlier accept", () => {
    expect(decideConsent({ stored: "accepted", signals: { ...none, doNotTrack: true } })).toEqual({ state: "declined", reason: "Do Not Track" });
    expect(decideConsent({ stored: "accepted", signals: { ...none, globalPrivacyControl: true } })).toEqual({ state: "declined", reason: "Global Privacy Control" });
  });
  it("does not even ask a visitor who has already said no through their browser", () => {
    expect(decideConsent({ stored: null, signals: { doNotTrack: true, globalPrivacyControl: false } }).state).toBe("declined");
    expect(decideConsent({ stored: null, signals: { doNotTrack: false, globalPrivacyControl: true } }).state).toBe("declined");
  });
});

describe("analyticsConfigured", () => {
  it("is on only with both a key and an http(s) host", () => {
    expect(analyticsConfigured("phc_abc", "https://eu.i.posthog.com")).toBe(true);
    expect(analyticsConfigured("phc_abc", "http://localhost:9099")).toBe(true);
  });
  it("is off, and so the banner never shows, when either is missing or the host is not a web address", () => {
    for (const [key, host] of [["", "https://eu.i.posthog.com"], ["phc_abc", ""], [undefined, "https://x.com"], ["phc_abc", undefined], ["   ", "https://x.com"],
      ["phc_abc", "javascript:alert(1)"], ["phc_abc", "//evil.example"], ["phc_abc", "not a url"], ["phc_abc", "ftp://x.com"]]) {
      expect(analyticsConfigured(key as string | undefined, host as string | undefined), `${key} ${host}`).toBe(false);
    }
  });
});
