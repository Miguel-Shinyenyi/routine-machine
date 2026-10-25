import { describe, expect, it } from "vitest";
import { describeChoice, EVENT_DISCLOSURE, regionOf } from "./disclosure";
import { ALLOWED_EVENTS } from "./events";

// The public privacy page (/privacy) says what is collected. It is built from EVENT_DISCLOSURE, and this test makes the two lists
// the same list: an event cannot be allowed to leave the browser without being described to the people it is collected from.
describe("EVENT_DISCLOSURE", () => {
  it("describes every event that is allowed to leave the browser, and no other", () => {
    expect(Object.keys(EVENT_DISCLOSURE).sort()).toEqual([...ALLOWED_EVENTS].sort());
  });
  it("says in plain words what each event is and what it carries", () => {
    for (const [name, entry] of Object.entries(EVENT_DISCLOSURE)) {
      expect(entry.when.length, `${name} when`).toBeGreaterThan(8);
      expect(entry.carries.length, `${name} carries`).toBeGreaterThan(8);
    }
  });
  it("marks as sent today only the events the app really sends: the two PostHog ones and the seven named actions that have a place in the app", () => {
    const sent = Object.entries(EVENT_DISCLOSURE).filter(([, entry]) => entry.sentToday).map(([name]) => name).sort();
    expect(sent).toEqual(["$identify", "$pageview", "cohort_dashboard_viewed", "credit_questioned", "day_logged", "hillside_joined", "hillside_left", "pause_logged", "reading_note_submitted"]);
  });
  it("says plainly that goal_created is not sent, because nothing in the app creates a goal yet", () => {
    expect(EVENT_DISCLOSURE.goal_created.sentToday).toBe(false);
  });
  it("promises nothing private on the named actions", () => {
    for (const name of ["goal_created", "credit_questioned", "reading_note_submitted", "pause_logged", "hillside_joined", "hillside_left", "cohort_dashboard_viewed"]) {
      expect(EVENT_DISCLOSURE[name].carries, name).toMatch(/\bno\b|\bnothing\b|\bonly\b/i);
    }
  });
});

describe("regionOf", () => {
  it("names where PostHog's two hosted regions are", () => {
    expect(regionOf("https://us.i.posthog.com")).toBe("the United States");
    expect(regionOf("https://eu.i.posthog.com")).toBe("the European Union");
  });
  it("says nothing for any other host, rather than guessing", () => {
    expect(regionOf("https://analytics.example.org")).toBeNull();
    expect(regionOf("http://localhost:9099")).toBeNull();
    expect(regionOf("")).toBeNull();
    expect(regionOf("not a url")).toBeNull();
  });
});

describe("describeChoice", () => {
  it("says what the visitor's current choice means, in plain words", () => {
    expect(describeChoice("ask", "no choice yet")).toBe("You have not chosen yet. Nothing is counted until you accept.");
    expect(describeChoice("granted", "accepted earlier")).toBe("You accepted. The pages you open are being counted.");
    expect(describeChoice("declined", "declined earlier")).toBe("You declined. Nothing is being counted.");
  });
  it("says so when the browser has already said no, whatever was chosen here", () => {
    expect(describeChoice("declined", "Do Not Track")).toBe("Your browser sends Do Not Track, so nothing is counted, whatever you choose here.");
    expect(describeChoice("declined", "Global Privacy Control")).toBe("Your browser sends Global Privacy Control, so nothing is counted, whatever you choose here.");
  });
});
