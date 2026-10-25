import { describe, expect, it } from "vitest";
import { ApiError } from "./api";
import { fetchLogs, formatUtcSeconds, levelTone, pageHref, parseLogFilters, safeHref } from "./logs";

describe("parseLogFilters", () => {
  it("takes a level from the fixed list and an id of plain characters", () => {
    expect(parseLogFilters({ level: "ERROR", id: "abc-123_x.y" })).toEqual({ level: "ERROR", requestId: "abc-123_x.y", badId: false });
  });

  it("drops a level that is not in the list instead of sending it", () => {
    expect(parseLogFilters({ level: "error" }).level).toBeNull();
    expect(parseLogFilters({ level: 'ERROR"} or {x="' }).level).toBeNull();
    expect(parseLogFilters({ level: ["ERROR", "WARN"] }).level).toBeNull();
  });

  it("drops an id with other characters or over 64 characters, and says it did", () => {
    for (const bad of ['a"b', "a b", "a|b", "x".repeat(65), "a/b"]) {
      expect(parseLogFilters({ id: bad })).toEqual({ level: null, requestId: null, badId: true });
    }
  });

  it("treats an empty box as no filter, not as a bad id", () => {
    expect(parseLogFilters({ id: "", level: "" })).toEqual({ level: null, requestId: null, badId: false });
    expect(parseLogFilters({})).toEqual({ level: null, requestId: null, badId: false });
  });
});

describe("fetchLogs", () => {
  const answer = { available: true, range: "1h", entries: [] };

  it("asks for the range and leaves out a filter that is not set", async () => {
    let asked = "";
    await fetchLogs("6h", { level: null, requestId: null }, async (path) => {
      asked = path;
      return answer as never;
    });
    expect(asked).toBe("/api/watchtower/logs?range=6h");
  });

  it("adds the level and the id when set", async () => {
    let asked = "";
    await fetchLogs("1h", { level: "WARN", requestId: "abc.1" }, async (path) => {
      asked = path;
      return answer as never;
    });
    expect(asked).toBe("/api/watchtower/logs?range=1h&level=WARN&requestId=abc.1");
  });

  it("returns null on a 404 and rethrows every other failure", async () => {
    expect(
      await fetchLogs("1h", { level: null, requestId: null }, async () => {
        throw new ApiError(404, "");
      }),
    ).toBeNull();
    await expect(
      fetchLogs("1h", { level: null, requestId: null }, async () => {
        throw new ApiError(500, "boom");
      }),
    ).rejects.toThrow("boom");
  });
});

describe("safeHref", () => {
  it("lets only http and https addresses through", () => {
    expect(safeHref("http://localhost:16686/trace/abc")).toBe("http://localhost:16686/trace/abc");
    expect(safeHref("https://jaeger.example/trace/abc")).toBe("https://jaeger.example/trace/abc");
    expect(safeHref("javascript:alert(1)")).toBeNull();
    expect(safeHref("data:text/html,x")).toBeNull();
    expect(safeHref("//evil.example")).toBeNull();
    expect(safeHref(null)).toBeNull();
  });
});

describe("formatUtcSeconds", () => {
  it("writes the time to the second in UTC", () => {
    expect(formatUtcSeconds("2026-10-25T08:23:23.123456Z")).toBe("2026-10-25 08:23:23");
    expect(formatUtcSeconds("2026-10-25T08:23:23Z")).toBe("2026-10-25 08:23:23");
  });
});

describe("levelTone", () => {
  it("marks errors as bad, warnings as a warning and everything else as plain", () => {
    expect(levelTone("ERROR")).toBe("bad");
    expect(levelTone("CRITICAL")).toBe("bad");
    expect(levelTone("WARN")).toBe("warn");
    expect(levelTone("WARNING")).toBe("warn");
    expect(levelTone("INFO")).toBe("plain");
    expect(levelTone("DEBUG")).toBe("plain");
  });
});

describe("pageHref", () => {
  it("writes the range and only the filters that are set", () => {
    expect(pageHref("6h", { level: null, requestId: null })).toBe("/watchtower?range=6h");
    expect(pageHref("1h", { level: "ERROR", requestId: "abc.1" })).toBe("/watchtower?range=1h&level=ERROR&id=abc.1");
  });
});
