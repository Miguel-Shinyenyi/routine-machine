import type { ScheduleTemplate, Task } from "./types";

export function templatesNeedingMaterialization(
  templates: ScheduleTemplate[],
  existingTasks: Task[],
): ScheduleTemplate[] {
  const materializedTemplateIds = new Set(
    existingTasks
      .map((task) => task.scheduleTemplateId)
      .filter((id): id is number => id !== null),
  );
  return templates.filter((template) => !materializedTemplateIds.has(template.id));
}

const DAY_NAMES = ["SUNDAY", "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY"] as const;

// Parses the date as UTC midnight and reads the weekday back with getUTCDay(), so the result
// depends only on the calendar date itself, never on the server process's local timezone
// (which would otherwise risk rolling the date to the adjacent day for timezones west of UTC).
export function dayOfWeekName(isoDate: string): string {
  const [year, month, day] = isoDate.split("-").map(Number);
  const utcDate = new Date(Date.UTC(year, month - 1, day));
  return DAY_NAMES[utcDate.getUTCDay()];
}

export function todayIsoDate(): string {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}
