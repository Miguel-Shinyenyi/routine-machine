import { beforeEach, describe, expect, it, vi } from "vitest";

// Which server actions leave a note for the analytics service, and when (staged Prompt 5, part 3). The note is left only AFTER the action
// succeeded, and carries nothing from the form: the pause action receives an amount and an item name, and neither may ever reach it.
const queue = vi.fn<(...args: unknown[]) => Promise<void>>(async () => {});
vi.mock("./analytics/queue", () => ({ queueAnalyticsEvent: (...args: unknown[]) => queue(...args) }));
vi.mock("next/cache", () => ({ revalidatePath: vi.fn() }));

const backendFetch = vi.fn<(...args: unknown[]) => Promise<unknown>>(async () => ({}));
const authenticatedBackendFetch = vi.fn<(...args: unknown[]) => Promise<unknown>>(async () => ({}));
const intelligenceFetch = vi.fn<(...args: unknown[]) => Promise<unknown>>(async () => ({}));
vi.mock("./api", () => ({
  ApiError: class ApiError extends Error {},
  backendFetch: (...args: unknown[]) => backendFetch(...args),
  authenticatedBackendFetch: (...args: unknown[]) => authenticatedBackendFetch(...args),
  backendFetchMultipart: vi.fn(),
  intelligenceFetch: (...args: unknown[]) => intelligenceFetch(...args),
}));
const session = { token: "t" as string | null, userId: 34 as number | null };
vi.mock("./auth/session", () => ({
  getSessionToken: async () => session.token,
  getSessionUserId: async () => session.userId,
  requireSessionUserId: async () => 34,
}));

import { createPause, questionCredit, setHillsideOptIn, submitReadingNote, updateTaskStatus } from "./actions";

function form(values: Record<string, string>): FormData {
  const data = new FormData();
  for (const [key, value] of Object.entries(values)) data.set(key, value);
  return data;
}

beforeEach(() => {
  queue.mockClear();
  backendFetch.mockReset().mockResolvedValue({});
  authenticatedBackendFetch.mockReset().mockResolvedValue({});
  intelligenceFetch.mockReset().mockResolvedValue({});
  session.token = "t";
  session.userId = 34;
});

describe("setHillsideOptIn", () => {
  it("queues hillside_joined when someone joins, and hillside_left when they leave", async () => {
    await setHillsideOptIn(form({ optIn: "true" }));
    await setHillsideOptIn(form({ optIn: "false" }));
    expect(queue.mock.calls).toEqual([["hillside_joined"], ["hillside_left"]]);
  });
  it("queues nothing when the backend refuses, or when nobody is signed in", async () => {
    authenticatedBackendFetch.mockRejectedValueOnce(new Error("refused"));
    await expect(setHillsideOptIn(form({ optIn: "true" }))).rejects.toThrow();
    session.token = null;
    await setHillsideOptIn(form({ optIn: "true" }));
    expect(queue).not.toHaveBeenCalled();
  });
});

describe("questionCredit", () => {
  it("queues credit_questioned, with nothing about the credit", async () => {
    await questionCredit(form({ eventId: "77" }));
    expect(queue.mock.calls).toEqual([["credit_questioned"]]);
  });
  it("queues nothing when the backend refuses", async () => {
    backendFetch.mockRejectedValueOnce(new Error("refused"));
    await expect(questionCredit(form({ eventId: "77" }))).rejects.toThrow();
    expect(queue).not.toHaveBeenCalled();
  });
});

describe("createPause", () => {
  it("queues pause_logged with NOTHING from the form: no amount, no item", async () => {
    await createPause(form({ what: "A pair of shoes", amount: "4500", why: "they were on sale", heardFrom: "an advert" }));
    expect(queue.mock.calls).toEqual([["pause_logged"]]);
    expect(JSON.stringify(queue.mock.calls)).not.toMatch(/shoes|4500|sale|advert/);
  });
  it("queues nothing when the backend refuses, or when nobody is signed in", async () => {
    backendFetch.mockRejectedValueOnce(new Error("refused"));
    await expect(createPause(form({ what: "x" }))).rejects.toThrow();
    session.userId = null;
    await createPause(form({ what: "x" }));
    expect(queue).not.toHaveBeenCalled();
  });
});

describe("submitReadingNote", () => {
  const values = { bookId: "3", noteText: "A private thought about the book", bookTitle: "Sapiens" };
  it("queues reading_note_submitted with no text, no score and no book", async () => {
    await submitReadingNote(form(values));
    expect(queue.mock.calls).toEqual([["reading_note_submitted"]]);
    expect(JSON.stringify(queue.mock.calls)).not.toMatch(/private|Sapiens/);
  });
  it("still queues it when the assessment failed, because the note was saved", async () => {
    intelligenceFetch.mockRejectedValueOnce(new Error("model unavailable"));
    await submitReadingNote(form(values));
    expect(queue.mock.calls).toEqual([["reading_note_submitted"]]);
  });
  it("queues nothing when the note could not be saved", async () => {
    backendFetch.mockRejectedValueOnce(new Error("refused"));
    await expect(submitReadingNote(form(values))).rejects.toThrow();
    expect(queue).not.toHaveBeenCalled();
  });
});

describe("updateTaskStatus", () => {
  it("queues day_logged, source manual, when a task is ticked done", async () => {
    await updateTaskStatus(5, "DONE");
    expect(queue.mock.calls).toEqual([["day_logged", { source: "manual" }]]);
  });
  it("queues nothing for any other status, or when the backend refuses", async () => {
    await updateTaskStatus(5, "TODO");
    await updateTaskStatus(5, "IN_PROGRESS");
    backendFetch.mockRejectedValueOnce(new Error("refused"));
    await expect(updateTaskStatus(5, "DONE")).rejects.toThrow();
    expect(queue).not.toHaveBeenCalled();
  });
});
