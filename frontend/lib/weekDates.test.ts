import { describe, expect, it } from "vitest";
import { addDays, mondayOf, weekDates } from "./weekDates";

describe("addDays", () => {
  it("adds positive days, crossing a month boundary", () => {
    expect(addDays("2026-10-30", 3)).toBe("2026-11-02");
  });

  it("subtracts days with a negative delta", () => {
    expect(addDays("2026-10-02", -3)).toBe("2026-09-29");
  });
});

describe("mondayOf", () => {
  it("returns the same date when it is already a Monday", () => {
    expect(mondayOf("2026-10-12")).toBe("2026-10-12");
  });

  it("returns the preceding Monday for a mid-week date", () => {
    expect(mondayOf("2026-10-15")).toBe("2026-10-12");
  });

  it("returns the preceding Monday for a Sunday, not the next one", () => {
    expect(mondayOf("2026-10-18")).toBe("2026-10-12");
  });
});

describe("weekDates", () => {
  it("returns all seven dates of the week starting at the given Monday", () => {
    expect(weekDates("2026-10-12")).toEqual([
      "2026-10-12",
      "2026-10-13",
      "2026-10-14",
      "2026-10-15",
      "2026-10-16",
      "2026-10-17",
      "2026-10-18",
    ]);
  });
});
