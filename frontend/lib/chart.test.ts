import { describe, expect, it } from "vitest";
import { clockLabel, formatValue, labelledEnds, nearestIndex, niceMax, parseRange, seriesExtent, splitAtGaps, stepOf } from "./chart";

describe("niceMax", () => {
  it("rounds up to 1, 2 or 5 times a power of ten, and never to zero", () => {
    expect(niceMax(0)).toBe(1);
    expect(niceMax(0.23)).toBe(0.5);
    expect(niceMax(1)).toBe(1);
    expect(niceMax(1.2)).toBe(2);
    expect(niceMax(3)).toBe(5);
    expect(niceMax(7)).toBe(10);
    expect(niceMax(130)).toBe(200);
  });
});

describe("seriesExtent", () => {
  it("finds the time span and the largest value over every series", () => {
    const extent = seriesExtent([
      { label: "a", points: [[100, 1], [200, 4]] },
      { label: "b", points: [[150, 9]] },
    ]);
    expect(extent).toEqual({ tMin: 100, tMax: 200, vMax: 9 });
  });

  it("is null when there is no point at all", () => {
    expect(seriesExtent([{ label: "a", points: [] }])).toBeNull();
    expect(seriesExtent([])).toBeNull();
  });
});

describe("nearestIndex", () => {
  it("snaps to the closest time, and the earlier one on a tie", () => {
    expect(nearestIndex([10, 20, 30], 12)).toBe(0);
    expect(nearestIndex([10, 20, 30], 26)).toBe(2);
    expect(nearestIndex([10, 20, 30], 15)).toBe(0);
    expect(nearestIndex([10, 20, 30], 999)).toBe(2);
  });
});

describe("formatValue", () => {
  it("writes seconds as milliseconds under a second", () => {
    expect(formatValue(0.0123, "s")).toBe("12 ms");
    expect(formatValue(1.5, "s")).toBe("1.5 s");
  });
  it("writes money, counts and compact thousands", () => {
    expect(formatValue(0.5, "USD")).toBe("$0.50");
    expect(formatValue(3, "failures")).toBe("3");
    expect(formatValue(2.5, "per hour")).toBe("2.5");
    expect(formatValue(1500, "per hour")).toBe("1.5K");
  });
});

describe("parseRange", () => {
  it("accepts the three ranges the backend allows and falls back to 6h for anything else", () => {
    expect(parseRange("1h")).toBe("1h");
    expect(parseRange("24h")).toBe("24h");
    expect(parseRange("9999d")).toBe("6h");
    expect(parseRange(undefined)).toBe("6h");
    expect(parseRange(["1h", "24h"])).toBe("6h");
  });
});

describe("clockLabel", () => {
  it("writes hours and minutes in UTC", () => {
    expect(clockLabel(1792916600)).toBe("08:23");
  });
});

describe("labelledEnds", () => {
  it("labels every line end when they are far enough apart", () => {
    expect(labelledEnds([10, 60, 120], 14)).toEqual([0, 1, 2]);
  });
  it("skips a label that would sit closer than the gap to the one above it, keeping the first", () => {
    expect(labelledEnds([50, 52, 120], 14)).toEqual([0, 2]);
  });
});

describe("stepOf", () => {
  it("is the smallest gap between two neighbouring points of any series", () => {
    expect(stepOf([{ label: "a", points: [[0, 1], [60, 1], [180, 1]] }, { label: "b", points: [[0, 1], [30, 1]] }])).toBe(30);
  });
  it("is 1 when no series has two points", () => {
    expect(stepOf([{ label: "a", points: [[0, 1]] }])).toBe(1);
  });
});

describe("splitAtGaps", () => {
  it("keeps points that follow each other as one run", () => {
    expect(splitAtGaps([[0, 1], [60, 2], [120, 3]], 60)).toEqual([[[0, 1], [60, 2], [120, 3]]]);
  });
  it("starts a new run where the time between two points is more than two steps, so no line crosses missing data", () => {
    expect(splitAtGaps([[0, 1], [60, 2], [600, 3], [660, 4]], 60)).toEqual([
      [[0, 1], [60, 2]],
      [[600, 3], [660, 4]],
    ]);
  });
  it("keeps a lone point as a run of its own, and returns no runs for no points", () => {
    expect(splitAtGaps([[0, 1], [600, 3]], 60)).toEqual([[[0, 1]], [[600, 3]]]);
    expect(splitAtGaps([], 60)).toEqual([]);
  });
});
