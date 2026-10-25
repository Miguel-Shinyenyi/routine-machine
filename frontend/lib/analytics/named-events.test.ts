import { describe, expect, it } from "vitest";
import { ALLOWED_EVENTS, CUSTOM_EVENTS, EVENT_PROPERTIES, buildEvent } from "./events";
import { isForbiddenField } from "./fields";
import { sanitizeEvent } from "./sanitize";

// Staged Prompt 5, part 3. The eight named actions, each with a CLOSED set of properties. This file is the check the prompt asks for:
// "a test or script fails the build if a captured event contains a field named like amount, text, note, email or name".

describe("isForbiddenField", () => {
  it("catches a field named like amount, text, note, email or name, in any common spelling", () => {
    for (const key of ["amount", "Amount", "amountKes", "amount_kes", "text", "noteText", "note_text", "notes", "email", "userEmail", "emailAddress",
      "name", "userName", "user_name", "firstName", "names", "item_name", "label.text", "pause-note"]) {
      expect(isForbiddenField(key), key).toBe(true);
    }
  });
  it("does not catch PostHog's own standard properties or ordinary words that merely contain those letters", () => {
    for (const key of ["$pathname", "$current_url", "$raw_user_agent", "$browser_language", "distinct_id", "source", "$session_id", "$screen_width", "namespace", "context", "denote"]) {
      expect(isForbiddenField(key), key).toBe(false);
    }
  });
});

describe("CUSTOM_EVENTS and EVENT_PROPERTIES", () => {
  it("are the eight named actions, each allowed to leave the browser", () => {
    expect([...CUSTOM_EVENTS].sort()).toEqual(["cohort_dashboard_viewed", "credit_questioned", "day_logged", "goal_created", "hillside_joined", "hillside_left", "pause_logged", "reading_note_submitted"]);
    for (const name of CUSTOM_EVENTS) expect(ALLOWED_EVENTS.has(name), name).toBe(true);
  });
  it("give every named action a property schema", () => {
    expect(Object.keys(EVENT_PROPERTIES).sort()).toEqual([...CUSTOM_EVENTS].sort());
  });
  it("THE BUILD-FAILING CHECK: no event has a property named like amount, text, note, email or name", () => {
    for (const [event, properties] of Object.entries(EVENT_PROPERTIES)) {
      for (const property of Object.keys(properties)) {
        expect(isForbiddenField(property), `${event}.${property}`).toBe(false);
      }
    }
  });
  it("gives day_logged one property, the source, from a fixed list; every other event none", () => {
    expect(Object.keys(EVENT_PROPERTIES.day_logged)).toEqual(["source"]);
    for (const name of CUSTOM_EVENTS) if (name !== "day_logged") expect(Object.keys(EVENT_PROPERTIES[name]), name).toEqual([]);
  });
});

describe("buildEvent", () => {
  it("accepts each named action with its allowed properties, and returns a copy", () => {
    for (const name of CUSTOM_EVENTS) {
      if (name === "day_logged") continue;
      expect(buildEvent(name, {}), name).toEqual({ name, properties: {} });
      expect(buildEvent(name), name).toEqual({ name, properties: {} });
    }
    expect(buildEvent("day_logged", { source: "manual" })).toEqual({ name: "day_logged", properties: { source: "manual" } });
  });
  it("refuses a property that is not in the schema, so a field like amount or text can never ride along", () => {
    expect(buildEvent("pause_logged", { amount: 500 })).toBeNull();
    expect(buildEvent("reading_note_submitted", { text: "my note" })).toBeNull();
    expect(buildEvent("goal_created", { name: "Learn Rust" })).toBeNull();
    expect(buildEvent("hillside_joined", { email: "a@b.c" })).toBeNull();
    expect(buildEvent("day_logged", { source: "manual", amount: 1 })).toBeNull();
  });
  it("refuses a value that is not on the fixed list, even for an allowed property", () => {
    expect(buildEvent("day_logged", { source: "My private journal entry" })).toBeNull();
    expect(buildEvent("day_logged", { source: 5 as unknown as string })).toBeNull();
    expect(buildEvent("day_logged", {})).toBeNull();
  });
  it("refuses a name that is not a named action: PostHog's own events, unknown names, and prototype tricks", () => {
    for (const name of ["$pageview", "$identify", "unknown_event", "", "__proto__", "constructor", "toString"]) {
      expect(buildEvent(name, {}), name).toBeNull();
    }
  });
});

describe("sanitizeEvent as a backstop", () => {
  it("drops a property named like amount, text, note, email or name from any allowed event, and keeps the rest", () => {
    const out = sanitizeEvent({ uuid: "u", event: "pause_logged", properties: { amount: 500, source: "manual", $pathname: "/granary", userEmail: "a@b.c", note_text: "x" } } as never);
    expect(out?.properties).toEqual({ source: "manual", $pathname: "/granary" });
  });
});
