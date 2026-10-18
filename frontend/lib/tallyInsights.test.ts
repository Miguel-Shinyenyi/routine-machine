import { describe, expect, it } from "vitest";
import { learningInsight, steepDayInsight } from "./tallyInsights";

describe("steepDayInsight", () => {
  it("names the weekday most steep days fall on", () => {
    const result = steepDayInsight([
      { date: "2026-10-06", completedCount: 1, totalItems: 5, skipRatio: 0.8 }, // Tuesday
      { date: "2026-10-13", completedCount: 1, totalItems: 5, skipRatio: 0.8 }, // Tuesday
      { date: "2026-10-08", completedCount: 1, totalItems: 5, skipRatio: 0.8 }, // Thursday
    ]);

    expect(result.headline).toBe("Tuesdays are the hardest climb.");
    expect(result.detail).toContain("2 of the 3 steep days");
    expect(result.detail).toContain("Tuesday");
  });

  it("handles no steep days without dividing by zero or crashing", () => {
    const result = steepDayInsight([]);

    expect(result.headline).toBe("No steep days recently.");
  });

  it("uses singular 'day' when there is exactly one steep day", () => {
    const result = steepDayInsight([{ date: "2026-10-06", completedCount: 1, totalItems: 5, skipRatio: 0.8 }]);

    expect(result.detail).toContain("1 of the 1 steep day ");
  });
});

describe("learningInsight", () => {
  it("says tend more when learning-day completions are higher", () => {
    const result = learningInsight({
      daysWithTopicLog: 3,
      daysWithoutTopicLog: 25,
      avgRoutineCompletionsOnTopicLogDays: 5.0,
      avgRoutineCompletionsOnOtherDays: 3.7,
    });

    expect(result.headline).toBe("On learning days you tend more.");
    expect(result.detail).toContain("5.0 routines tended");
    expect(result.detail).toContain("3.7");
  });

  it("says tend less when learning-day completions are lower", () => {
    const result = learningInsight({
      daysWithTopicLog: 3,
      daysWithoutTopicLog: 25,
      avgRoutineCompletionsOnTopicLogDays: 2.0,
      avgRoutineCompletionsOnOtherDays: 4.0,
    });

    expect(result.headline).toBe("On learning days you tend less.");
  });

  it("says days look the same when the averages tie exactly", () => {
    const result = learningInsight({
      daysWithTopicLog: 3,
      daysWithoutTopicLog: 25,
      avgRoutineCompletionsOnTopicLogDays: 4.0,
      avgRoutineCompletionsOnOtherDays: 4.0,
    });

    expect(result.headline).toBe("Learning days look like any other day.");
  });
});
