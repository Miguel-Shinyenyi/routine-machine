import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
import { FAILURE_MODES, PROMPT_DAYS, failureModeLabel, isFailureMode, isPromptDay, questionOpen, weeklyCard } from "./failureModes";

describe("FAILURE_MODES", () => {
  it("lists the eight modes of docs/understanding-first.md in order, then 'none this week' last", () => {
    expect(FAILURE_MODES.map((m) => m.value)).toEqual([
      "FORMING", "DISLODGING", "ASSUMPTION", "LOCATION", "ACHIEVEMENT", "PROGRESSION", "INTERRUPTION", "MISLEAD", "NONE",
    ]);
  });
  it("names each mode as the document does, with its one-line sign", () => {
    const doc = readFileSync("../docs/understanding-first.md", "utf8");
    for (const mode of FAILURE_MODES.filter((m) => m.value !== "NONE")) {
      expect(doc, mode.label).toContain(`. ${mode.label}.`);
      expect(mode.sign.length, mode.label).toBeGreaterThan(5);
    }
  });
  it("matches the values the backend accepts", () => {
    const java = readFileSync("../backend/src/main/java/com/routinemachine/core/domain/FailureMode.java", "utf8");
    const names = (/enum FailureMode \{([^}]*)\}/.exec(java)?.[1] ?? "").split(",").map((n) => n.trim()).filter(Boolean);
    expect(FAILURE_MODES.map((m) => m.value)).toEqual(names);
  });
  it("has no em dash", () => {
    for (const mode of FAILURE_MODES) expect(mode.label + mode.sign).not.toContain("—");
  });
});

describe("isFailureMode and failureModeLabel", () => {
  it("accepts only the listed values", () => {
    expect(isFailureMode("MISLEAD")).toBe(true);
    expect(isFailureMode("NONE")).toBe(true);
    for (const bad of ["mislead", "LAZINESS", "", null, undefined, 3]) expect(isFailureMode(bad), String(bad)).toBe(false);
  });
  it("gives the label for a value", () => {
    expect(failureModeLabel("PROGRESSION")).toBe("Progression");
    expect(failureModeLabel("NONE")).toBe("None this week");
  });
});

describe("PROMPT_DAYS and isPromptDay", () => {
  it("lists the seven days Monday first, as the backend's DayOfWeek names them", () => {
    expect(PROMPT_DAYS.map((d) => d.value)).toEqual(["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"]);
  });
  it("accepts only those names", () => {
    expect(isPromptDay("THURSDAY")).toBe(true);
    for (const bad of ["thursday", "FUNDAY", "", null, undefined, 4]) expect(isPromptDay(bad), String(bad)).toBe(false);
  });
});

describe("questionOpen", () => {
  // 2026-10-26 is a Monday.
  it("is open from the chosen day through Sunday, and closed before it", () => {
    expect(questionOpen("THURSDAY", "2026-10-26")).toBe(false); // Mon
    expect(questionOpen("THURSDAY", "2026-10-28")).toBe(false); // Wed
    expect(questionOpen("THURSDAY", "2026-10-29")).toBe(true); // Thu
    expect(questionOpen("THURSDAY", "2026-10-30")).toBe(true); // Fri
    expect(questionOpen("THURSDAY", "2026-11-01")).toBe(true); // Sun
  });
  it("starts the week again on the next Monday", () => {
    expect(questionOpen("THURSDAY", "2026-11-02")).toBe(false);
  });
  it("is open every day when the chosen day is Monday", () => {
    for (const d of ["2026-10-26", "2026-10-28", "2026-11-01"]) expect(questionOpen("MONDAY", d), d).toBe(true);
  });
  it("is open on a Sunday only when Sunday is chosen or earlier", () => {
    expect(questionOpen("SUNDAY", "2026-10-31")).toBe(false);
    expect(questionOpen("SUNDAY", "2026-11-01")).toBe(true);
  });
  it("falls back to open when it cannot tell: an unknown day name or a date that is not a date", () => {
    expect(questionOpen("FUNDAY", "2026-10-26")).toBe(true);
    expect(questionOpen("THURSDAY", "not a date")).toBe(true);
    expect(questionOpen(undefined, "2026-10-26")).toBe(true);
  });
});

describe("weeklyCard", () => {
  const open = { promptDay: "MONDAY", isoDate: "2026-10-26" };
  it("asks the question while this week has no answer", () => {
    expect(weeklyCard({ weekStart: "2026-10-26", answered: false, failureMode: null }, open)).toEqual({ kind: "ask" });
  });
  it("shows the answer, and that it can be changed, once there is one", () => {
    expect(weeklyCard({ weekStart: "2026-10-26", answered: true, failureMode: "FORMING" }, open)).toEqual({ kind: "answered", label: "Forming" });
  });
  it("shows nothing when the answer could not be read, so a failed call never blocks Today", () => {
    expect(weeklyCard(null, open)).toEqual({ kind: "hidden" });
  });
  it("treats an answer it does not recognise as not answered, rather than showing junk", () => {
    expect(weeklyCard({ weekStart: "2026-10-26", answered: true, failureMode: "LAZINESS" }, open)).toEqual({ kind: "ask" });
  });
  it("does not ask before the chosen day, but still shows an answer already given", () => {
    const early = { promptDay: "THURSDAY", isoDate: "2026-10-26" };
    expect(weeklyCard({ weekStart: "2026-10-26", answered: false, failureMode: null }, early)).toEqual({ kind: "hidden" });
    expect(weeklyCard({ weekStart: "2026-10-26", answered: true, failureMode: "MISLEAD" }, early)).toEqual({ kind: "answered", label: "Mislead" });
  });
  it("asks on the chosen day", () => {
    expect(weeklyCard({ weekStart: "2026-10-26", answered: false, failureMode: null }, { promptDay: "THURSDAY", isoDate: "2026-10-29" })).toEqual({ kind: "ask" });
  });
});
