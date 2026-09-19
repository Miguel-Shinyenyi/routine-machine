import { describe, expect, it } from "vitest";
import { dayOfWeekName, templatesNeedingMaterialization } from "./schedule";
import type { ScheduleTemplate, Task } from "./types";

function template(id: number, overrides: Partial<ScheduleTemplate> = {}): ScheduleTemplate {
  return {
    id,
    dayOfWeek: "MONDAY",
    targetType: "ROUTINE_ITEM",
    routineItemId: 1,
    targetDurationMinutes: 60,
    sortOrder: 1,
    label: "Weight lifting",
    ...overrides,
  };
}

function task(scheduleTemplateId: number | null): Task {
  return {
    id: 1,
    scheduleTemplateId,
    taskDate: "2026-09-21",
    label: "x",
    routineItemId: null,
    learningTopicId: null,
    status: "TODO",
    dailyLogId: null,
  };
}

describe("templatesNeedingMaterialization", () => {
  it("returns templates with no matching task yet", () => {
    const templates = [template(1), template(2)];
    const existing = [task(1)];

    expect(templatesNeedingMaterialization(templates, existing)).toEqual([template(2)]);
  });

  it("returns everything when there are no existing tasks", () => {
    const templates = [template(1), template(2)];

    expect(templatesNeedingMaterialization(templates, [])).toEqual(templates);
  });

  it("returns nothing once every template already has a task", () => {
    const templates = [template(1), template(2)];
    const existing = [task(1), task(2)];

    expect(templatesNeedingMaterialization(templates, existing)).toEqual([]);
  });

  it("ignores ad-hoc tasks (no scheduleTemplateId) when checking for materialization", () => {
    const templates = [template(1)];
    const existing = [task(null)];

    expect(templatesNeedingMaterialization(templates, existing)).toEqual([template(1)]);
  });
});

describe("dayOfWeekName", () => {
  it("computes the weekday from the calendar date alone, independent of server timezone", () => {
    // 2026-09-21 is a Monday.
    expect(dayOfWeekName("2026-09-21")).toBe("MONDAY");
    expect(dayOfWeekName("2026-09-26")).toBe("SATURDAY");
    expect(dayOfWeekName("2026-09-27")).toBe("SUNDAY");
  });
});
