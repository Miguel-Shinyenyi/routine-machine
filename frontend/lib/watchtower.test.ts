import { describe, expect, it } from "vitest";
import { ApiError } from "./api";
import { fetchMetrics, fetchServices, fetchSync, formatUtc, serviceTone, syncTone } from "./watchtower";

describe("fetchServices", () => {
  it("returns the services the backend lists", async () => {
    const services = await fetchServices(async () => [{ name: "backend", status: "UP" }] as never);
    expect(services).toEqual([{ name: "backend", status: "UP" }]);
  });

  it("returns null on a 404, which is how the backend says this person may not see the page", async () => {
    const services = await fetchServices(async () => {
      throw new ApiError(404, "");
    });
    expect(services).toBeNull();
  });

  it("does not hide any other failure as a 404", async () => {
    await expect(
      fetchServices(async () => {
        throw new ApiError(500, "boom");
      }),
    ).rejects.toThrow("boom");
    await expect(
      fetchServices(async () => {
        throw new Error("connection refused");
      }),
    ).rejects.toThrow("connection refused");
  });
});

describe("serviceTone", () => {
  it("names UP as good, DOWN as bad and anything else as neutral", () => {
    expect(serviceTone("UP")).toBe("good");
    expect(serviceTone("DOWN")).toBe("bad");
    expect(serviceTone("NOT_CONFIGURED")).toBe("neutral");
    expect(serviceTone("something new")).toBe("neutral");
  });
});

describe("fetchSync", () => {
  const answer = { available: true, sources: [] };

  it("returns what the backend answers", async () => {
    expect(await fetchSync(async () => answer as never)).toEqual(answer);
  });

  it("returns null on a 404 and rethrows every other failure", async () => {
    expect(
      await fetchSync(async () => {
        throw new ApiError(404, "");
      }),
    ).toBeNull();
    await expect(
      fetchSync(async () => {
        throw new ApiError(500, "boom");
      }),
    ).rejects.toThrow("boom");
  });

  it("asks for the sync path", async () => {
    let asked = "";
    await fetchSync(async (path) => {
      asked = path;
      return answer as never;
    });
    expect(asked).toBe("/api/watchtower/sync");
  });
});

describe("syncTone", () => {
  it("names ok as good, failing as bad and every other word as neutral", () => {
    expect(syncTone("ok")).toBe("good");
    expect(syncTone("failing")).toBe("bad");
    for (const word of ["off", "push only", "unknown", "never succeeded", "a new word"]) {
      expect(syncTone(word)).toBe("neutral");
    }
  });
});

describe("formatUtc", () => {
  it("writes a time to the minute in UTC, or Never when there is none", () => {
    expect(formatUtc("2026-10-25T11:00:59Z")).toBe("2026-10-25 11:00 UTC");
    expect(formatUtc("2026-10-25T11:00:59.123456+00:00")).toBe("2026-10-25 11:00 UTC");
    expect(formatUtc(null)).toBe("Never");
  });
});

describe("fetchMetrics", () => {
  it("asks for the chosen range and returns the answer", async () => {
    let asked = "";
    const answer = { available: true, range: "24h", charts: [] };
    const result = await fetchMetrics("24h", async (path) => {
      asked = path;
      return answer as never;
    });
    expect(asked).toBe("/api/watchtower/metrics?range=24h");
    expect(result).toEqual(answer);
  });

  it("returns null on a 404 and rethrows every other failure", async () => {
    expect(
      await fetchMetrics("6h", async () => {
        throw new ApiError(404, "");
      }),
    ).toBeNull();
    await expect(
      fetchMetrics("6h", async () => {
        throw new ApiError(500, "boom");
      }),
    ).rejects.toThrow("boom");
  });
});
