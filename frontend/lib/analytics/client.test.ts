import { beforeEach, describe, expect, it, vi } from "vitest";

// The library is replaced by a recorder, so these tests are about the ORDER and the NUMBER of calls this app makes. Found live on
// 25 Oct 2026: the library's reset() also clears its consent setting back to the default (opted in), so opting out and THEN resetting
// left a visitor who had withdrawn consent capturing again. Reset first, then opt out.
const calls: string[] = [];
const posthog = {
  init: vi.fn((...args: unknown[]) => calls.push(`init:${String(args[0])}`)),
  reset: vi.fn(() => calls.push("reset")),
  opt_out_capturing: vi.fn(() => calls.push("opt_out_capturing")),
  opt_in_capturing: vi.fn(() => calls.push("opt_in_capturing")),
  identify: vi.fn((...args: unknown[]) => calls.push(`identify:${args.map(String).join(",")}`)),
};
vi.mock("posthog-js", () => ({ default: posthog }));

async function freshClient() {
  vi.resetModules();
  return import("./client");
}

beforeEach(() => {
  calls.length = 0;
  Object.values(posthog).forEach((fn) => fn.mockClear());
});

describe("startAnalytics", () => {
  it("starts the library once, with the trimmed key and the app's options", async () => {
    const { startAnalytics } = await freshClient();
    await startAnalytics("  phc_key  ", "https://eu.i.posthog.com");
    await startAnalytics("phc_key", "https://eu.i.posthog.com");
    expect(posthog.init).toHaveBeenCalledTimes(1);
    expect(posthog.init.mock.calls[0][0]).toBe("phc_key");
    expect(posthog.init.mock.calls[0][1]).toMatchObject({ api_host: "https://eu.i.posthog.com", autocapture: false, capture_pageleave: false });
  });
});

describe("startAnalytics, called again while already running", () => {
  it("does nothing more: it does not opt in again, which PostHog would send as an extra $opt_in event", async () => {
    const { startAnalytics } = await freshClient();
    await startAnalytics("phc_key", "https://eu.i.posthog.com");
    calls.length = 0;
    await startAnalytics("phc_key", "https://eu.i.posthog.com");
    await startAnalytics("phc_key", "https://eu.i.posthog.com");
    expect(calls).toEqual([]);
  });
});

describe("stopAnalytics", () => {
  it("does nothing, and loads nothing, when analytics never started", async () => {
    const { stopAnalytics } = await freshClient();
    await stopAnalytics();
    expect(calls).toEqual([]);
  });

  it("resets FIRST and opts out AFTER, because reset() puts the consent setting back to opted in", async () => {
    const { startAnalytics, stopAnalytics } = await freshClient();
    await startAnalytics("phc_key", "https://eu.i.posthog.com");
    calls.length = 0;
    await stopAnalytics();
    expect(calls).toEqual(["reset", "opt_out_capturing"]);
  });
});

describe("accepting again after stopping", () => {
  it("opts back in on the same instance and does not start the library a second time", async () => {
    const { startAnalytics, stopAnalytics } = await freshClient();
    await startAnalytics("phc_key", "https://eu.i.posthog.com");
    await stopAnalytics();
    calls.length = 0;
    await startAnalytics("phc_key", "https://eu.i.posthog.com");
    expect(calls).toEqual(["opt_in_capturing"]);
    expect(posthog.init).toHaveBeenCalledTimes(1);
  });
});

describe("identifyUser (staged Prompt 5, part 2)", () => {
  it("identifies with the internal id ALONE: no email, no name, no person properties", async () => {
    const { startAnalytics, identifyUser } = await freshClient();
    await startAnalytics("phc_key", "https://us.i.posthog.com");
    calls.length = 0;
    await identifyUser(34);
    expect(posthog.identify).toHaveBeenCalledTimes(1);
    expect(posthog.identify.mock.calls[0]).toEqual(["34"]);
  });

  it("does nothing, and loads nothing, when analytics never started (the visitor has not accepted)", async () => {
    const { identifyUser } = await freshClient();
    await identifyUser(34);
    expect(calls).toEqual([]);
  });

  it("does not identify the same person twice in a row", async () => {
    const { startAnalytics, identifyUser } = await freshClient();
    await startAnalytics("phc_key", "https://us.i.posthog.com");
    await identifyUser(34);
    await identifyUser(34);
    expect(posthog.identify).toHaveBeenCalledTimes(1);
  });

  it("identifies a different person who signs in next", async () => {
    const { startAnalytics, identifyUser } = await freshClient();
    await startAnalytics("phc_key", "https://us.i.posthog.com");
    await identifyUser(34);
    await identifyUser(35);
    expect(posthog.identify.mock.calls.map((call) => call[0])).toEqual(["34", "35"]);
  });

  it("forgets the person when they sign out, and does that only if someone was identified", async () => {
    const { startAnalytics, identifyUser } = await freshClient();
    await startAnalytics("phc_key", "https://us.i.posthog.com");
    calls.length = 0;
    await identifyUser(null);
    expect(calls).toEqual([]);
    await identifyUser(34);
    calls.length = 0;
    await identifyUser(null);
    expect(calls).toEqual(["reset"]);
  });

  it("refuses an id that is not a positive whole number", async () => {
    const { startAnalytics, identifyUser } = await freshClient();
    await startAnalytics("phc_key", "https://us.i.posthog.com");
    calls.length = 0;
    await identifyUser("miguel@example.com" as unknown as number);
    await identifyUser(-3);
    expect(posthog.identify).not.toHaveBeenCalled();
  });

  it("does not identify again after the visitor withdrew, until they accept again", async () => {
    const { startAnalytics, stopAnalytics, identifyUser } = await freshClient();
    await startAnalytics("phc_key", "https://us.i.posthog.com");
    await identifyUser(34);
    await stopAnalytics();
    calls.length = 0;
    await identifyUser(34);
    expect(calls).toEqual([]);
    await startAnalytics("phc_key", "https://us.i.posthog.com");
    calls.length = 0;
    await identifyUser(34);
    expect(calls).toEqual(["identify:34"]);
  });
});
