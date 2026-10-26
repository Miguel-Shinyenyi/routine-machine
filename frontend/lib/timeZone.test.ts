import { describe, expect, it } from "vitest";
import { registrationTimeZone } from "./timeZone";

describe("registrationTimeZone", () => {
  it("keeps a real zone name", () => {
    expect(registrationTimeZone("Africa/Nairobi")).toBe("Africa/Nairobi");
  });

  it("trims spaces", () => {
    expect(registrationTimeZone("  Europe/London ")).toBe("Europe/London");
  });

  it("drops a name that is not a zone, so a bad value never reaches the backend", () => {
    expect(registrationTimeZone("Mars/Base")).toBeNull();
  });

  it("drops empty, missing and non-text values", () => {
    expect(registrationTimeZone("")).toBeNull();
    expect(registrationTimeZone(null)).toBeNull();
    expect(registrationTimeZone(42)).toBeNull();
  });

  it("drops a very long value", () => {
    expect(registrationTimeZone("A".repeat(65))).toBeNull();
  });
});
