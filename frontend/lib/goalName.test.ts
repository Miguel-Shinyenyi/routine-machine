import { describe, expect, it } from "vitest";
import { goalNameProblem } from "./goalName";

describe("goalNameProblem", () => {
  it("accepts a new name", () => {
    expect(goalNameProblem("Learn Rust", ["Read daily"])).toBeNull();
  });
  it("says so when the person already has a goal with that name", () => {
    expect(goalNameProblem("Read daily", ["Read daily"])).toMatch(/already/i);
  });
  it("ignores spaces around the name, as the action trims it", () => {
    expect(goalNameProblem("  Read daily ", ["Read daily"])).toMatch(/already/i);
  });
  it("says nothing for an empty box", () => {
    expect(goalNameProblem("   ", ["Read daily"])).toBeNull();
  });
});
