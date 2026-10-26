import { describe, expect, it } from "vitest";
import { planPending, shouldTrackPath } from "./plan";
import type { PendingEvent } from "./pending";

// What the browser does with the notes the server left (staged Prompt 5, part 3), decided in one place so it can be tested. The rule that
// matters most: a note is sent ONLY if the visitor accepted, and it is cleared in EVERY case, so one that was not sent cannot linger and be
// sent later, after a different choice.
const note = (id: string): PendingEvent => ({ id, name: "hillside_joined", properties: {} });

describe("planPending", () => {
  it("does nothing when there are no notes", () => {
    expect(planPending({ pending: [], state: "granted", sentIds: new Set() })).toEqual({ send: [], clear: false });
  });
  it("sends the notes of a visitor who accepted, and clears them", () => {
    expect(planPending({ pending: [note("a"), note("b")], state: "granted", sentIds: new Set() })).toEqual({ send: [note("a"), note("b")], clear: true });
  });
  it("sends each note once, even if the page asks twice", () => {
    expect(planPending({ pending: [note("a"), note("b")], state: "granted", sentIds: new Set(["a"]) })).toEqual({ send: [note("b")], clear: true });
  });
  it("sends nothing for a visitor who declined, and clears the notes", () => {
    expect(planPending({ pending: [note("a")], state: "declined", sentIds: new Set() })).toEqual({ send: [], clear: true });
  });
  it("sends nothing for a visitor who has not chosen yet, and clears the notes, so they cannot be sent later", () => {
    expect(planPending({ pending: [note("a")], state: "ask", sentIds: new Set() })).toEqual({ send: [], clear: true });
  });
});

describe("shouldTrackPath", () => {
  it("names the cohort dashboard once per visit to it, for a visitor who accepted", () => {
    expect(shouldTrackPath({ state: "granted", pathname: "/cohort/12", lastPathname: "/today" })).toBe("cohort_dashboard_viewed");
  });
  it("does not name it again while the visitor stays on the same page", () => {
    expect(shouldTrackPath({ state: "granted", pathname: "/cohort/12", lastPathname: "/cohort/12" })).toBeNull();
  });
  it("names it again when the visitor leaves and comes back", () => {
    expect(shouldTrackPath({ state: "granted", pathname: "/cohort/12", lastPathname: "/today" })).toBe("cohort_dashboard_viewed");
  });
  it("names nothing for a visitor who declined or has not chosen, or for any other page", () => {
    expect(shouldTrackPath({ state: "declined", pathname: "/cohort/12", lastPathname: null })).toBeNull();
    expect(shouldTrackPath({ state: "ask", pathname: "/cohort/12", lastPathname: null })).toBeNull();
    expect(shouldTrackPath({ state: "granted", pathname: "/today", lastPathname: null })).toBeNull();
  });
});
