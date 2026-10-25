import { describe, expect, it } from "vitest";
import { PENDING_COOKIE, appendPending, parsePending } from "./pending";
import { eventForPath } from "./paths";

// How the SERVER tells the BROWSER that a named action just happened (staged Prompt 5, part 3). The server never sends an event: it
// does not know whether the visitor accepted. It leaves a short-lived note (an event name and at most one fixed-value property) in a
// cookie, the browser sends it if and only if the visitor accepted, and clears the note either way.
describe("appendPending and parsePending", () => {
  const now = 1_000_000;

  it("keeps an event the server queued, with a short life", () => {
    const raw = appendPending(undefined, { name: "hillside_joined", properties: {} }, now, "id-1");
    expect(parsePending(raw, now + 1000)).toEqual([{ id: "id-1", name: "hillside_joined", properties: {} }]);
  });
  it("adds to what is already there, in order", () => {
    let raw = appendPending(undefined, { name: "pause_logged", properties: {} }, now, "a");
    raw = appendPending(raw, { name: "day_logged", properties: { source: "manual" } }, now + 10, "b");
    expect(parsePending(raw, now + 20).map((e) => e.id)).toEqual(["a", "b"]);
  });
  it("lets an event expire after two minutes, so a note never lingers", () => {
    const raw = appendPending(undefined, { name: "pause_logged", properties: {} }, now, "a");
    expect(parsePending(raw, now + 119_000)).toHaveLength(1);
    expect(parsePending(raw, now + 121_000)).toHaveLength(0);
  });
  it("holds at most ten events", () => {
    let raw: string | undefined;
    for (let i = 0; i < 15; i += 1) raw = appendPending(raw, { name: "pause_logged", properties: {} }, now, `e${i}`);
    expect(parsePending(raw, now)).toHaveLength(10);
    expect(parsePending(raw, now).map((e) => e.id)[9]).toBe("e14");
  });
  it("never trusts the cookie: an event with a name or a property that is not allowed is dropped, and so is garbage", () => {
    const forged = JSON.stringify([
      { id: "ok", name: "hillside_left", properties: {}, at: now },
      { id: "bad1", name: "$autocapture", properties: {}, at: now },
      { id: "bad2", name: "pause_logged", properties: { amount: 500 }, at: now },
      { id: "bad3", name: "day_logged", properties: { source: "free text" }, at: now },
      { name: "no_id", properties: {}, at: now },
      "not an object",
      null,
    ]);
    expect(parsePending(forged, now).map((e) => e.id)).toEqual(["ok"]);
    for (const raw of [undefined, "", "not json", "{}", "123", "null"]) expect(parsePending(raw, now), String(raw)).toEqual([]);
  });
  it("refuses to queue an event that is not allowed", () => {
    const raw = appendPending(undefined, { name: "pause_logged", properties: { amount: 5 } } as never, now, "x");
    expect(parsePending(raw, now)).toEqual([]);
  });
  it("names its cookie", () => {
    expect(PENDING_COOKIE).toBe("rm_events");
  });
});

describe("eventForPath", () => {
  it("names the cohort dashboard for a cohort's address, and sends nothing about which cohort", () => {
    expect(eventForPath("/cohort/12")).toBe("cohort_dashboard_viewed");
    expect(eventForPath("/cohort/abc-9/")).toBe("cohort_dashboard_viewed");
  });
  it("names nothing for any other page", () => {
    for (const path of ["/", "/today", "/cohort", "/cohort/", "/cohort/12/members", "/privacy", "/hillside"]) expect(eventForPath(path), path).toBeNull();
  });
});
