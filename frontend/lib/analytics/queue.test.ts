import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

// The server side of staged Prompt 5, part 3: after a Server Action succeeds it leaves a note for the browser (lib/analytics/pending.ts).
// It leaves nothing when analytics is off, and nothing for an event that is not exactly one of the allowed shapes.
const store = { value: undefined as string | undefined, sets: [] as { name: string; value: string; options: Record<string, unknown> }[] };
vi.mock("next/headers", () => ({
  cookies: async () => ({
    get: (name: string) => (name === "rm_events" && store.value !== undefined ? { name, value: store.value } : undefined),
    set: (name: string, value: string, options: Record<string, unknown>) => {
      store.sets.push({ name, value, options });
      store.value = value;
    },
  }),
}));

import { queueAnalyticsEvent, readPendingEvents } from "./queue";
import { parsePending } from "./pending";

beforeEach(() => {
  store.value = undefined;
  store.sets.length = 0;
  vi.stubEnv("POSTHOG_KEY", "phc_test_key");
  vi.stubEnv("POSTHOG_HOST", "https://us.i.posthog.com");
});
afterEach(() => vi.unstubAllEnvs());

describe("queueAnalyticsEvent", () => {
  it("leaves a short-lived note for the browser", async () => {
    await queueAnalyticsEvent("hillside_joined");
    expect(store.sets).toHaveLength(1);
    expect(store.sets[0].name).toBe("rm_events");
    expect(store.sets[0].options).toMatchObject({ path: "/", maxAge: 120, sameSite: "lax", httpOnly: false });
    expect(parsePending(store.value, Date.now()).map((e) => e.name)).toEqual(["hillside_joined"]);
  });
  it("adds to the notes already waiting", async () => {
    await queueAnalyticsEvent("pause_logged");
    await queueAnalyticsEvent("day_logged", { source: "manual" });
    expect(parsePending(store.value, Date.now()).map((e) => e.name)).toEqual(["pause_logged", "day_logged"]);
  });
  it("leaves nothing at all when analytics is not configured", async () => {
    vi.stubEnv("POSTHOG_KEY", "");
    await queueAnalyticsEvent("hillside_joined");
    expect(store.sets).toEqual([]);
    vi.stubEnv("POSTHOG_KEY", "phc_test_key");
    vi.stubEnv("POSTHOG_HOST", "");
    await queueAnalyticsEvent("hillside_joined");
    expect(store.sets).toEqual([]);
  });
  it("leaves nothing for an event that is not allowed, such as one carrying an amount", async () => {
    await queueAnalyticsEvent("pause_logged", { amount: 500 });
    await queueAnalyticsEvent("not_an_event" as never);
    await queueAnalyticsEvent("day_logged", { source: "my private notes" });
    expect(store.sets).toEqual([]);
  });
  it("gives each note its own id, so the browser can send it once", async () => {
    await queueAnalyticsEvent("pause_logged");
    await queueAnalyticsEvent("pause_logged");
    const ids = parsePending(store.value, Date.now()).map((e) => e.id);
    expect(new Set(ids).size).toBe(2);
  });
});

describe("readPendingEvents", () => {
  it("returns the notes waiting, checked again", async () => {
    await queueAnalyticsEvent("hillside_left");
    expect((await readPendingEvents()).map((event) => event.name)).toEqual(["hillside_left"]);
  });
  it("returns nothing when analytics is not configured, even if a cookie is there", async () => {
    await queueAnalyticsEvent("hillside_left");
    vi.stubEnv("POSTHOG_HOST", "");
    expect(await readPendingEvents()).toEqual([]);
  });
  it("returns nothing for a cookie that was edited into something that is not allowed", async () => {
    store.value = JSON.stringify([{ id: "x", name: "pause_logged", properties: { amount: 500 }, at: Date.now() }, { id: "y", name: "$autocapture", properties: {}, at: Date.now() }]);
    expect(await readPendingEvents()).toEqual([]);
  });
  it("returns nothing when there is no cookie", async () => {
    expect(await readPendingEvents()).toEqual([]);
  });
});
