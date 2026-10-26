import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
import { chooseReminder, missedRecently, parseReminderFile, parseReminders, reminderFor } from "./reminders";

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

describe("parseReminderFile", () => {
  it("splits the daily reminders from the ones for after missed days", () => {
    const parsed = parseReminderFile("Daily one.\nDaily two.\n[after missed days]\nStronger one.\n");
    expect(parsed).toEqual({ daily: ["Daily one.", "Daily two."], stronger: ["Stronger one."] });
  });
  it("keeps the heading itself out of both lists, whatever its case or spacing", () => {
    const parsed = parseReminderFile("A.\n  [After Missed Days]  \nB.\n");
    expect(parsed).toEqual({ daily: ["A."], stronger: ["B."] });
  });
  it("has no stronger reminders when the file has no such section, and parseReminders is the daily list only", () => {
    expect(parseReminderFile("Only daily.\n").stronger).toEqual([]);
    expect(parseReminders("A.\n[after missed days]\nB.\n")).toEqual(["A."]);
  });
});

describe("missedRecently", () => {
  const day = (date: string, shownUp: boolean) => ({ date, shownUp });
  const week = [
    day("2026-10-20", true), day("2026-10-21", true), day("2026-10-22", false), day("2026-10-23", false), day("2026-10-24", false),
    day("2026-10-25", false),
  ];

  it("is true when the person has shown up before and not in any of the last three days", () => {
    expect(missedRecently(week, "2026-10-26")).toBe(true);
  });
  it("is false if any of the last three days counted", () => {
    expect(missedRecently([...week.slice(0, 5), day("2026-10-25", true)], "2026-10-26")).toBe(false);
    expect(missedRecently([...week.slice(0, 3), day("2026-10-23", true), day("2026-10-24", false), day("2026-10-25", false)], "2026-10-26")).toBe(false);
  });
  it("is false for someone who has never shown up, so a new account is never told it has missed days", () => {
    expect(missedRecently(week.map((d) => day(d.date, false)), "2026-10-26")).toBe(false);
  });
  it("is false when the last three days are not all in the record", () => {
    expect(missedRecently([day("2026-10-20", true), day("2026-10-25", false)], "2026-10-26")).toBe(false);
  });
  it("looks only at days before the date shown, not on or after it", () => {
    expect(missedRecently([...week, day("2026-10-26", true)], "2026-10-26")).toBe(true);
  });
  it("gives false for a date that is not a date", () => {
    expect(missedRecently(week, "nope")).toBe(false);
  });
});

describe("chooseReminder", () => {
  const daily = ["d1", "d2"];
  const stronger = ["s1", "s2"];

  it("gives the daily reminder when no days were missed", () => {
    expect(chooseReminder({ daily, stronger, isoDate: "2026-10-26", missed: false })).toBe(reminderFor(daily, "2026-10-26"));
  });
  it("gives a stronger one, rotated by date, when days were missed", () => {
    expect(stronger).toContain(chooseReminder({ daily, stronger, isoDate: "2026-10-26", missed: true }));
    expect(chooseReminder({ daily, stronger, isoDate: "2026-10-26", missed: true })).not.toBe(
      chooseReminder({ daily, stronger, isoDate: "2026-10-27", missed: true }),
    );
  });
  it("falls back to the daily reminder when there are no stronger ones", () => {
    expect(chooseReminder({ daily, stronger: [], isoDate: "2026-10-26", missed: true })).toBe(reminderFor(daily, "2026-10-26"));
  });
  it("gives a stronger one even when the daily list is empty", () => {
    expect(chooseReminder({ daily: [], stronger, isoDate: "2026-10-26", missed: true })).not.toBeNull();
    expect(chooseReminder({ daily: [], stronger: [], isoDate: "2026-10-26", missed: true })).toBeNull();
  });
});

describe("the shipped file's stronger section", () => {
  const { daily, stronger } = parseReminderFile(readFileSync("content/reminders.txt", "utf8"));

  it("leaves the ten daily reminders alone and adds a few for after missed days", () => {
    expect(daily).toHaveLength(10);
    expect(stronger.length).toBeGreaterThanOrEqual(2);
  });
  it("never pressures: no shame or guilt words, no em dash, and each one is short", () => {
    for (const text of stronger) {
      expect(text, text).not.toMatch(/\b(lazy|failure|failed|behind|catch up now|should have|guilt|ashamed)\b/i);
      expect(text, text).not.toContain("\u2014");
      expect(text.length, text).toBeLessThanOrEqual(200);
    }
  });
});
