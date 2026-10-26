import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
import { FAILURE_MODES, failureModeLabel, isFailureMode, weeklyCard } from "./failureModes";

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

describe("weeklyCard", () => {
  it("asks the question while this week has no answer", () => {
    expect(weeklyCard({ weekStart: "2026-10-26", answered: false, failureMode: null })).toEqual({ kind: "ask" });
  });
  it("shows the answer, and that it can be changed, once there is one", () => {
    expect(weeklyCard({ weekStart: "2026-10-26", answered: true, failureMode: "FORMING" })).toEqual({ kind: "answered", label: "Forming" });
  });
  it("shows nothing when the answer could not be read, so a failed call never blocks Today", () => {
    expect(weeklyCard(null)).toEqual({ kind: "hidden" });
  });
  it("treats an answer it does not recognise as not answered, rather than showing junk", () => {
    expect(weeklyCard({ weekStart: "2026-10-26", answered: true, failureMode: "LAZINESS" })).toEqual({ kind: "ask" });
  });
});
