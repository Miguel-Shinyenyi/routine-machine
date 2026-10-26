import { beforeEach, describe, expect, it, vi } from "vitest";

// The weekly failure-mode answer (staged Prompt 7): which server action sends what, and when it refuses to send.
const queue = vi.fn<(...args: unknown[]) => Promise<void>>(async () => {});
vi.mock("./analytics/queue", () => ({ queueAnalyticsEvent: (...args: unknown[]) => queue(...args) }));
vi.mock("next/cache", () => ({ revalidatePath: vi.fn() }));

const backendFetch = vi.fn<(...args: unknown[]) => Promise<unknown>>(async () => ({}));
const authenticatedBackendFetch = vi.fn<(...args: unknown[]) => Promise<unknown>>(async () => ({}));
vi.mock("./api", () => ({
  ApiError: class ApiError extends Error {},
  backendFetch: (...args: unknown[]) => backendFetch(...args),
  authenticatedBackendFetch: (...args: unknown[]) => authenticatedBackendFetch(...args),
  backendFetchMultipart: vi.fn(),
  intelligenceFetch: vi.fn(),
}));
vi.mock("./auth/session", () => ({
  getSessionToken: async () => "t",
  getSessionUserId: async () => 34,
  requireSessionUserId: async () => 34,
}));

import { answerFailureMode, updateProfile } from "./actions";

function form(values: Record<string, string>): FormData {
  const data = new FormData();
  for (const [key, value] of Object.entries(values)) data.set(key, value);
  return data;
}

beforeEach(() => {
  queue.mockClear();
  backendFetch.mockReset().mockResolvedValue({});
  authenticatedBackendFetch.mockReset().mockResolvedValue({});
});

describe("answerFailureMode", () => {
  it("sends the chosen mode to the backend", async () => {
    await answerFailureMode(form({ failureMode: "PROGRESSION" }));
    expect(backendFetch).toHaveBeenCalledWith("/api/failure-modes/current", {
      method: "PUT",
      body: JSON.stringify({ failureMode: "PROGRESSION" }),
    });
  });
  it("sends 'none this week' as NONE", async () => {
    await answerFailureMode(form({ failureMode: "NONE" }));
    expect(backendFetch).toHaveBeenCalledTimes(1);
  });
  it("sends nothing for a value that is not one of the choices", async () => {
    for (const bad of ["LAZINESS", "", "progression"]) await answerFailureMode(form({ failureMode: bad }));
    await answerFailureMode(form({}));
    expect(backendFetch).not.toHaveBeenCalled();
  });
  it("leaves no analytics note: the answer is personal and is not one of the named events", async () => {
    await answerFailureMode(form({ failureMode: "MISLEAD" }));
    expect(queue).not.toHaveBeenCalled();
  });
  it("lets a backend refusal through, so a failed save is not reported as saved", async () => {
    backendFetch.mockRejectedValueOnce(new Error("refused"));
    await expect(answerFailureMode(form({ failureMode: "FORMING" }))).rejects.toThrow();
  });
});

describe("updateProfile and the day the weekly question starts", () => {
  function sentBody(): Record<string, unknown> {
    const init = authenticatedBackendFetch.mock.calls[0][2] as { body: string };
    return JSON.parse(init.body);
  }

  it("sends the chosen day", async () => {
    await updateProfile(form({ failureModePromptDay: "THURSDAY" }));
    expect(sentBody().failureModePromptDay).toBe("THURSDAY");
  });
  it("sends no day when the value is not a day of the week, so the saved choice stays", async () => {
    await updateProfile(form({ failureModePromptDay: "FUNDAY" }));
    expect(sentBody().failureModePromptDay).toBeNull();
    authenticatedBackendFetch.mockClear();
    await updateProfile(form({}));
    expect(sentBody().failureModePromptDay).toBeNull();
  });
});
