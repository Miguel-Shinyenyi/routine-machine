import { describe, expect, it } from "vitest";
import { withUserId } from "./scope";

describe("withUserId", () => {
  it("adds the user to a path with no query", () => {
    expect(withUserId("/api/books", 7)).toBe("/api/books?userId=7");
  });

  it("adds the user after an existing query", () => {
    expect(withUserId("/api/tasks?date=2026-10-21", 7)).toBe("/api/tasks?date=2026-10-21&userId=7");
  });

  it("keeps a trailing fragment-free path with several parameters intact", () => {
    expect(withUserId("/api/reading-notes?bookId=3&x=y", 12)).toBe("/api/reading-notes?bookId=3&x=y&userId=12");
  });

  it("replaces a userId already on the path instead of adding a second one", () => {
    expect(withUserId("/api/goals?userId=99", 7)).toBe("/api/goals?userId=7");
    expect(withUserId("/api/goals?a=1&userId=99&b=2", 7)).toBe("/api/goals?a=1&b=2&userId=7");
  });
});
