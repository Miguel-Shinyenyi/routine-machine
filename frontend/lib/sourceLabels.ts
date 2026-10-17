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
