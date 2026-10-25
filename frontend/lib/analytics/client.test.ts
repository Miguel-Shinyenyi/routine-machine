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
