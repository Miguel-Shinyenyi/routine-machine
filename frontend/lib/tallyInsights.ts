import type { Patterns, SkipHeavyDay } from "./types";

const WEEKDAY_NAMES = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"];

export interface Insight {
  headline: string;
  detail: string;
}

// "Tuesdays are the hardest climb." in the design reference is a real insight computed from
// which weekday a user's steep (skip-heavy) days cluster on — this is the same computation,
// run on real skipHeavyDays instead of the reference's own hand-picked sample numbers.
export function steepDayInsight(skipHeavyDays: SkipHeavyDay[]): Insight {
  if (skipHeavyDays.length === 0) {
    return { headline: "No steep days recently.", detail: "Every day in this window stayed close to your usual pace." };
  }
  const countByWeekday = new Array(7).fill(0);
  for (const day of skipHeavyDays) {
    countByWeekday[new Date(`${day.date}T00:00:00Z`).getUTCDay()]++;
  }
  const maxCount = Math.max(...countByWeekday);
  const weekdayIndex = countByWeekday.indexOf(maxCount);
  const weekdayName = WEEKDAY_NAMES[weekdayIndex];
  return {
    headline: `${weekdayName}s are the hardest climb.`,
    detail: `${maxCount} of the ${skipHeavyDays.length} steep ${skipHeavyDays.length === 1 ? "day" : "days"} in this window fell on a ${weekdayName}. That is a pattern, not a failing.`,
  };
}

export function learningInsight(correlation: Patterns["topicRoutineCorrelation"]): Insight {
  const { avgRoutineCompletionsOnTopicLogDays: onDays, avgRoutineCompletionsOnOtherDays: offDays } = correlation;
  const headline =
    onDays === offDays
      ? "Learning days look like any other day."
      : onDays > offDays
        ? "On learning days you tend more."
        : "On learning days you tend less.";
  return {
    headline,
    detail: `Days with a learning note: ${onDays.toFixed(1)} routines tended. Days without: ${offDays.toFixed(1)}. A correlation, not proof of cause.`,
  };
}
