import { describe, expect, it } from "vitest";
import { ALLOWED_EVENTS } from "./events";
import { sanitizeEvent, stripUrl } from "./sanitize";

// Found by looking at what a real accepted visit sends (staged Prompt 5, part 1): a $pageleave event with scroll statistics that
// nobody asked for, full URLs including query strings, and the page title. This runs on every event just before it leaves the
// browser (PostHog's before_send), so the rules hold whatever else is added to the app.

function event(name: string, properties: Record<string, unknown> = {}) {
  return { uuid: "u", event: name, properties };
}

describe("ALLOWED_EVENTS", () => {
  it("is the pageview, identify, and the eight custom events the prompt names, and nothing else", () => {
    expect([...ALLOWED_EVENTS].sort()).toEqual([
      "$identify", "$pageview", "cohort_dashboard_viewed", "credit_questioned", "day_logged", "goal_created", "hillside_joined",
      "hillside_left", "pause_logged", "reading_note_submitted",
    ]);
  });
});

describe("sanitizeEvent: which events leave", () => {
  it("lets a pageview and the named events through", () => {
    for (const name of ALLOWED_EVENTS) {
      expect(sanitizeEvent(event(name)), name).not.toBeNull();
    }
  });
  it("drops every other event: page-leave, automatic clicks, errors, web vitals, anything invented later", () => {
    for (const name of ["$pageleave", "$autocapture", "$exception", "$web_vitals", "$rageclick", "$snapshot", "note_text_typed", "", "PAGEVIEW"]) {
      expect(sanitizeEvent(event(name)), name).toBeNull();
    }
  });
});

describe("stripUrl", () => {
  it("keeps the address and the path, and drops the query string and the fragment", () => {
    expect(stripUrl("http://localhost:3000/watchtower?range=1h&id=abc-123#logs")).toBe("http://localhost:3000/watchtower");
    expect(stripUrl("https://app.example.com/reading/12?note=hello")).toBe("https://app.example.com/reading/12");
    expect(stripUrl("https://app.example.com/today")).toBe("https://app.example.com/today");
  });
  it("cuts a value that is not a full address at the first ? or #, so nothing after them survives", () => {
    expect(stripUrl("/today?date=2026-10-25")).toBe("/today");
    expect(stripUrl("/path#frag?x=1")).toBe("/path");
  });
  it("leaves PostHog's own markers alone", () => {
    expect(stripUrl("$direct")).toBe("$direct");
    expect(stripUrl("")).toBe("");
  });
});

describe("sanitizeEvent: what stays on an event that leaves", () => {
  it("strips the query string from every URL property", () => {
    const out = sanitizeEvent(event("$pageview", {
      $current_url: "http://localhost:3000/tally?window=all",
      $referrer: "http://localhost:3000/today?date=2026-10-24",
      $session_entry_url: "http://localhost:3000/login?next=%2Fgranary",
      $session_entry_referrer: "https://search.example/?q=my+private+question",
    }));
    expect(out?.properties).toMatchObject({
      $current_url: "http://localhost:3000/tally",
      $referrer: "http://localhost:3000/today",
      $session_entry_url: "http://localhost:3000/login",
      $session_entry_referrer: "https://search.example/",
    });
  });
  it("drops the page title, which could one day hold something private", () => {
    const out = sanitizeEvent(event("$pageview", { title: "Reading: my private book", $pathname: "/reading" }));
    expect(out?.properties).not.toHaveProperty("title");
    expect(out?.properties).toHaveProperty("$pathname", "/reading");
  });
  it("leaves the other properties as they were", () => {
    const out = sanitizeEvent(event("goal_created", { source: "form", n: 3 }));
    expect(out?.properties).toEqual({ source: "form", n: 3 });
  });
  it("does not change the event it was given", () => {
    const original = event("$pageview", { $current_url: "http://x.test/a?b=1", title: "t" });
    sanitizeEvent(original);
    expect(original.properties).toEqual({ $current_url: "http://x.test/a?b=1", title: "t" });
  });
});
