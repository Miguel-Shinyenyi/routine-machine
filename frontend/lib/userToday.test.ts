import { describe, expect, it } from "vitest";
import { todayInZone } from "./userToday";

// Backlog P: "today" is the person's day, in the person's time zone, not the server's.
describe("todayInZone", () => {
  // 22:30 UTC on Sunday 25 October 2026.
  const lateSundayUtc = new Date("2026-10-25T22:30:00Z");

  it("is already Monday in Nairobi (UTC+3) when it is late Sunday in UTC", () => {
    expect(todayInZone("Africa/Nairobi", lateSundayUtc)).toBe("2026-10-26");
  });
  it("is still Sunday in UTC and in Los Angeles at that moment", () => {
    expect(todayInZone("UTC", lateSundayUtc)).toBe("2026-10-25");
    expect(todayInZone("America/Los_Angeles", lateSundayUtc)).toBe("2026-10-25");
  });
  it("is the next day far east of Greenwich a minute before UTC midnight (UTC+14)", () => {
    expect(todayInZone("Pacific/Kiritimati", new Date("2026-10-25T23:59:59Z"))).toBe("2026-10-26");
  });
  it("is the previous day far west of Greenwich just after UTC midnight", () => {
    expect(todayInZone("America/Los_Angeles", new Date("2026-10-26T00:00:01Z"))).toBe("2026-10-25");
  });
  it("follows daylight saving: London is on GMT, not BST, from 01:00 UTC on 25 Oct 2026", () => {
    expect(todayInZone("Europe/London", new Date("2026-10-25T00:30:00Z"))).toBe("2026-10-25"); // 01:30 BST
    expect(todayInZone("Europe/London", new Date("2026-10-25T23:30:00Z"))).toBe("2026-10-25"); // 23:30 GMT
    expect(todayInZone("Europe/London", new Date("2026-10-26T00:00:00Z"))).toBe("2026-10-26");
  });
  it("pads the month and the day", () => {
    expect(todayInZone("UTC", new Date("2027-01-05T12:00:00Z"))).toBe("2027-01-05");
  });
  it("uses UTC when there is no zone, or the zone is not a real one, rather than failing a page", () => {
    expect(todayInZone(null, lateSundayUtc)).toBe("2026-10-25");
    expect(todayInZone(undefined, lateSundayUtc)).toBe("2026-10-25");
    expect(todayInZone("Mars/Base", lateSundayUtc)).toBe("2026-10-25");
    expect(todayInZone("", lateSundayUtc)).toBe("2026-10-25");
  });
  it("gives the same answer as the server's own clock when the zone is the server's zone", () => {
    const now = new Date("2026-06-15T10:00:00Z");
    expect(todayInZone("UTC", now)).toBe("2026-06-15");
  });
});
