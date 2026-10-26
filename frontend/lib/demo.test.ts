import { describe, expect, it, vi } from "vitest";
import { demoEnabledFrom, openDemo } from "./demo";

describe("demoEnabledFrom", () => {
  it("is on only for the exact word true", () => {
    expect(demoEnabledFrom("true")).toBe(true);
    for (const off of [undefined, "", "false", "TRUE", "1", "yes", " true"]) expect(demoEnabledFrom(off), String(off)).toBe(false);
  });
});

describe("openDemo", () => {
  const call = vi.fn<(token: string) => Promise<{ id: number; demo: boolean }>>();

  it("does nothing, and asks nobody, when the demo is off", async () => {
    call.mockReset();
    expect(await openDemo({ enabled: false, internalToken: "t", call })).toBeNull();
    expect(call).not.toHaveBeenCalled();
  });

  it("does nothing when there is no internal token to ask with", async () => {
    call.mockReset();
    expect(await openDemo({ enabled: true, internalToken: undefined, call })).toBeNull();
    expect(await openDemo({ enabled: true, internalToken: "", call })).toBeNull();
    expect(call).not.toHaveBeenCalled();
  });

  it("asks the backend with the token and returns the demo account's id", async () => {
    call.mockReset().mockResolvedValue({ id: 37, demo: true });
    expect(await openDemo({ enabled: true, internalToken: "the-token", call })).toBe(37);
    expect(call).toHaveBeenCalledWith("the-token");
  });

  it("refuses an account the backend does not call a demo, so a real person is never signed in this way", async () => {
    call.mockReset().mockResolvedValue({ id: 5, demo: false });
    expect(await openDemo({ enabled: true, internalToken: "t", call })).toBeNull();
  });

  it("returns null when the backend fails", async () => {
    call.mockReset().mockRejectedValue(new Error("down"));
    expect(await openDemo({ enabled: true, internalToken: "t", call })).toBeNull();
  });
});
