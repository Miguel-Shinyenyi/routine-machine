import { describe, expect, it } from "vitest";
import { posthogOptions } from "./options";
import { sanitizeEvent } from "./sanitize";

// What the analytics library is told when it starts, after a visitor has accepted. Each setting here is a privacy decision.
describe("posthogOptions", () => {
  const options = posthogOptions("https://eu.i.posthog.com");

  it("sends events only to the configured host", () => {
    expect(options.api_host).toBe("https://eu.i.posthog.com");
  });
  it("captures only what the app names: no automatic click capture, no recording, no surveys, no feature-flag calls", () => {
    expect(options.autocapture).toBe(false);
    expect(options.disable_session_recording).toBe(true);
    expect(options.disable_surveys).toBe(true);
    expect(options.advanced_disable_flags).toBe(true);
  });
  it("counts pages as the visitor moves between them, with no person profile until someone is identified", () => {
    expect(options.capture_pageview).toBe("history_change");
    expect(options.person_profiles).toBe("identified_only");
  });
  it("honours Do Not Track as well, and keeps its identifier in local storage and not in a cookie", () => {
    expect(options.respect_dnt).toBe(true);
    expect(options.persistence).toBe("localStorage");
  });
  it("sends no page-leave event and no scroll statistics, and filters every event just before it leaves", () => {
    expect(options.capture_pageleave).toBe(false);
    expect(options.disable_scroll_properties).toBe(true);
    expect(options.before_send).toBe(sanitizeEvent);
  });
  it("never uses cookieless mode, which would still count a visitor who said no", () => {
    expect(options).not.toHaveProperty("cookieless_mode");
  });
});
