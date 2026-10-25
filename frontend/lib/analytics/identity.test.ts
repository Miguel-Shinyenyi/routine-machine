import { describe, expect, it } from "vitest";
import { distinctIdFor } from "./identity";

// Staged Prompt 5, part 2: people are identified by their INTERNAL USER ID only, never an email or a name. The one place that
// decides what an identifier may look like is distinctIdFor, so anything that is not a positive whole number is refused.
describe("distinctIdFor", () => {
  it("turns an internal user id into its text", () => {
    expect(distinctIdFor(1)).toBe("1");
    expect(distinctIdFor(34)).toBe("34");
    expect(distinctIdFor(9007199254740991)).toBe("9007199254740991");
  });
  it("refuses anything that is not a positive whole number, so an email, a name or a made-up value can never become an identifier", () => {
    for (const value of [null, undefined, 0, -1, 1.5, NaN, Infinity, "7", "miguel@example.com", "Miguel", "", {}, [], true, 9007199254740993]) {
      expect(distinctIdFor(value as unknown as number), String(value)).toBeNull();
    }
  });
});
