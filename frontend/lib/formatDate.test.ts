import { describe, expect, it } from "vitest";
import { formatEyebrowDate, formatWeekRange } from "./formatDate";

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

// Backlog V: the Path's week label. A Monday and the Sunday six days later.
describe("formatWeekRange", () => {
  it("keeps the short form when the week sits inside one month", () => {
    expect(formatWeekRange("2026-10-19")).toBe("19 to 25 October");
    expect(formatWeekRange("2026-10-05")).toBe("5 to 11 October");
  });
  it("names both months when the week crosses a month, which used to read '26 to 1 October'", () => {
    expect(formatWeekRange("2026-10-26")).toBe("26 October to 1 November");
    expect(formatWeekRange("2026-03-30")).toBe("30 March to 5 April");
  });
  it("names both years when the week crosses new year", () => {
    expect(formatWeekRange("2026-12-28")).toBe("28 December 2026 to 3 January 2027");
  });
  it("handles a leap day inside the week and a week that ends on the last day of the month", () => {
    expect(formatWeekRange("2028-02-28")).toBe("28 February to 5 March");
    expect(formatWeekRange("2026-08-25")).toBe("25 to 31 August");
  });
  it("reads the week after a month boundary as one month again", () => {
    expect(formatWeekRange("2026-11-02")).toBe("2 to 8 November");
  });
});

