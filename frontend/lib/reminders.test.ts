import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
import { parseReminders, reminderFor } from "./reminders";

describe("parseReminders", () => {
  it("takes one reminder per line, trimmed", () => {
    expect(parseReminders("  First one.  \nSecond one.\n")).toEqual(["First one.", "Second one."]);
  });
  it("ignores blank lines and lines that start with #", () => {
    expect(parseReminders("# a note to the editor\n\nOnly this.\n   \n# another\n")).toEqual(["Only this."]);
  });
  it("copes with Windows line endings", () => {
    expect(parseReminders("One.\r\nTwo.\r\n")).toEqual(["One.", "Two."]);
  });
  it("gives an empty list for an empty file", () => {
    expect(parseReminders("")).toEqual([]);
  });
});

describe("reminderFor", () => {
  const list = ["a", "b", "c"];

  it("gives the same reminder for the same date, however often it is asked", () => {
    expect(reminderFor(list, "2026-10-26")).toBe(reminderFor(list, "2026-10-26"));
  });
  it("moves to the next reminder each day and wraps around after the last", () => {
    const first = list.indexOf(reminderFor(list, "2026-10-26") as string);
    expect(reminderFor(list, "2026-10-27")).toBe(list[(first + 1) % 3]);
    expect(reminderFor(list, "2026-10-28")).toBe(list[(first + 2) % 3]);
    expect(reminderFor(list, "2026-10-29")).toBe(list[first]);
  });
  it("steps one reminder across a month and a year boundary and a leap day", () => {
    for (const [day, next] of [["2026-10-31", "2026-11-01"], ["2026-12-31", "2027-01-01"], ["2028-02-28", "2028-02-29"], ["2028-02-29", "2028-03-01"]]) {
      const i = list.indexOf(reminderFor(list, day) as string);
      expect(reminderFor(list, next), `${day} to ${next}`).toBe(list[(i + 1) % 3]);
    }
  });
  it("shows every reminder once in a run of as many days as there are reminders", () => {
    const seen = new Set(["2026-10-26", "2026-10-27", "2026-10-28"].map((d) => reminderFor(list, d)));
    expect(seen.size).toBe(3);
  });
  it("gives null for no reminders or a date that is not a date", () => {
    expect(reminderFor([], "2026-10-26")).toBeNull();
    expect(reminderFor(list, "not a date")).toBeNull();
    expect(reminderFor(list, "2026-13-45")).toBeNull();
  });
});

describe("the shipped content file", () => {
  const reminders = parseReminders(readFileSync("content/reminders.txt", "utf8"));

  it("holds the ten reminders from the staged prompt, in order", () => {
    expect(reminders).toHaveLength(10);
    expect(reminders[0]).toBe("The little work you do each day is what matters.");
    expect(reminders[9]).toBe("Every action has consequences. Know how to undo it before you do it.");
  });
  it("has no empty or over-long reminder, and no em dash", () => {
    for (const text of reminders) {
      expect(text.length, text).toBeGreaterThan(10);
      expect(text.length, text).toBeLessThanOrEqual(200);
      expect(text, text).not.toContain("—");
    }
  });
});
