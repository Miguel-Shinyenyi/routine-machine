import { describe, expect, it } from "vitest";
import { firstStep } from "./firstStep";

describe("firstStep", () => {
  it("tells a user with no routines to add one in the flock", () => {
    const step = firstStep({ routineItemCount: 0, scheduleTemplateCount: 0 });
    expect(step?.href).toBe("/flock");
    expect(step?.title).toMatch(/first routine/i);
  });

  it("tells a user with routines but no schedule to put one on the path", () => {
    const step = firstStep({ routineItemCount: 2, scheduleTemplateCount: 0 });
    expect(step?.href).toBe("/flock");
    expect(step?.title).toMatch(/path/i);
  });

  it("shows nothing once a routine is on the schedule", () => {
    expect(firstStep({ routineItemCount: 1, scheduleTemplateCount: 1 })).toBeNull();
  });

  it("asks for a routine before a schedule even if templates somehow exist", () => {
    expect(firstStep({ routineItemCount: 0, scheduleTemplateCount: 3 })?.title).toMatch(/first routine/i);
  });
});
