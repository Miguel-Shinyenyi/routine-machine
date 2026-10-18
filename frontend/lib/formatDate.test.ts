import { describe, expect, it } from "vitest";
import { formatEyebrowDate } from "./formatDate";

describe("formatEyebrowDate", () => {
  it("formats an ISO date as weekday, day, and month", () => {
    expect(formatEyebrowDate("2026-10-16")).toBe("Friday, 16 October");
  });

  it("does not shift the date across a server timezone boundary", () => {
    // 2026-01-01 is a Thursday; a naive `new Date("2026-01-01")` parsed in a UTC-negative
    // timezone can shift to December 31st if not handled as UTC throughout.
    expect(formatEyebrowDate("2026-01-01")).toBe("Thursday, 1 January");
  });
});
