import type { RoutineItemSource } from "./types";

// A routine item's source is stored and returned by the API as a raw enum name (WAKATIME,
// READER_NOTES, ...). The design reference always shows a human label ("WakaTime", "Reader's
// note") instead, never the enum itself, so every page that shows a source chip reads it from
// here rather than printing routineItem.source directly.
export const SOURCE_LABELS: Record<RoutineItemSource, string> = {
  MANUAL: "Tapped",
  HUB: "UMWAYI hub",
  WAKATIME: "WakaTime",
  ACTIVITYWATCH: "ActivityWatch",
  HEALTH: "Health Connect",
  READER_NOTES: "Reader's note",
  GITHUB: "GitHub",
  STRAVA: "Strava",
  RESCUETIME: "RescueTime",
};

export function sourceLabel(source: RoutineItemSource | string): string {
  return SOURCE_LABELS[source as RoutineItemSource] ?? source;
}

// A watering hole's own MeasurementSource (the push/pull status cards, and the owner's sync table), a different
// enum from RoutineItemSource above. The names mostly overlap, but "Health data" (a status card) and "Health
// Connect" (a routine item's source chip) are deliberately different labels for the same integration.
const MEASUREMENT_SOURCE_LABELS: Record<string, string> = {
  HUB: "UMWAYI hub",
  WAKATIME: "WakaTime",
  ACTIVITYWATCH: "ActivityWatch",
  GITHUB: "GitHub",
  RESCUETIME: "RescueTime",
  HEALTH: "Health data",
};

export function measurementSourceLabel(source: string): string {
  return MEASUREMENT_SOURCE_LABELS[source] ?? source;
}
