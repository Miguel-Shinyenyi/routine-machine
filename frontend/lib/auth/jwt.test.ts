import { beforeEach, describe, expect, it } from "vitest";
import { signSessionToken, verifySessionToken } from "./jwt";

describe("session JWT", () => {
  beforeEach(() => {
    process.env.AUTH_JWT_SECRET = "test-only-secret-not-for-real-use-0123456789";
  });

  it("round-trips a user id through sign and verify", () => {
    const token = signSessionToken(42);
    expect(verifySessionToken(token)).toBe(42);
  });

  it("rejects a token once its expiry has passed", () => {
    const issuedAt = 1_000_000;
    const token = signSessionToken(42, issuedAt);
    const sevenDaysAndOneSecondLater = issuedAt + 60 * 60 * 24 * 7 + 1;

    expect(verifySessionToken(token, sevenDaysAndOneSecondLater)).toBeNull();
  });

  it("accepts a token right up to the moment it expires", () => {
    const issuedAt = 1_000_000;
    const token = signSessionToken(42, issuedAt);
    const oneSecondBeforeExpiry = issuedAt + 60 * 60 * 24 * 7 - 1;

    expect(verifySessionToken(token, oneSecondBeforeExpiry)).toBe(42);
  });

  it("rejects a token signed with a different secret", () => {
    const token = signSessionToken(42);
    process.env.AUTH_JWT_SECRET = "a-completely-different-secret-0123456789ab";

    expect(verifySessionToken(token)).toBeNull();
  });

  it("rejects a token with a tampered payload", () => {
    const token = signSessionToken(42);
    const [header, , signature] = token.split(".");
    const tamperedPayload = Buffer.from(JSON.stringify({ sub: "99", exp: 9_999_999_999 }))
      .toString("base64")
      .replace(/\+/g, "-")
      .replace(/\//g, "_")
      .replace(/=+$/, "");
    const tampered = `${header}.${tamperedPayload}.${signature}`;

    expect(verifySessionToken(tampered)).toBeNull();
  });

  it("rejects garbage input without throwing", () => {
    expect(verifySessionToken("not-a-jwt")).toBeNull();
    expect(verifySessionToken("")).toBeNull();
    expect(verifySessionToken("a.b.c.d")).toBeNull();
  });
});
