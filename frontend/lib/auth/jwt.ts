import { createHmac, timingSafeEqual } from "node:crypto";

// Mints and verifies the same HS256 JWT shape the backend's GET /api/users/me and
// NimbusJwtDecoder.withSecretKey(...) expect (see docs/backend.md's decisions log): a plain
// {sub, exp} payload, signed with the shared AUTH_JWT_SECRET. This is the real version of
// what the earlier design-reference stub-data script minted by hand for the dev database;
// Next.js is the one place this project's docs always said would own the session and mint
// this token, not a workaround.
const SESSION_TTL_SECONDS = 60 * 60 * 24 * 7; // 7 days

function secret(): string {
  const value = process.env.AUTH_JWT_SECRET;
  if (!value) {
    throw new Error("AUTH_JWT_SECRET is not set — the session cookie can't be signed or verified without it");
  }
  return value;
}

function base64url(input: Buffer): string {
  return input.toString("base64").replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function base64urlDecode(input: string): Buffer {
  const padded = input.replace(/-/g, "+").replace(/_/g, "/").padEnd(input.length + ((4 - (input.length % 4)) % 4), "=");
  return Buffer.from(padded, "base64");
}

function sign(signingInput: string): string {
  return base64url(createHmac("sha256", secret()).update(signingInput).digest());
}

export function signSessionToken(userId: number, nowSeconds: number = Math.floor(Date.now() / 1000)): string {
  const header = base64url(Buffer.from(JSON.stringify({ alg: "HS256", typ: "JWT" })));
  const payload = base64url(
    Buffer.from(JSON.stringify({ sub: String(userId), iat: nowSeconds, exp: nowSeconds + SESSION_TTL_SECONDS })),
  );
  const signingInput = `${header}.${payload}`;
  return `${signingInput}.${sign(signingInput)}`;
}

// Returns the user id if the token's signature and expiry both check out, null otherwise —
// never throws, since every caller treats "not logged in" and "bad token" the same way.
export function verifySessionToken(token: string, nowSeconds: number = Math.floor(Date.now() / 1000)): number | null {
  const parts = token.split(".");
  if (parts.length !== 3) return null;
  const [header, payload, signature] = parts;

  const expectedSignature = sign(`${header}.${payload}`);
  const a = Buffer.from(signature);
  const b = Buffer.from(expectedSignature);
  if (a.length !== b.length || !timingSafeEqual(a, b)) return null;

  let claims: { sub?: string; exp?: number };
  try {
    claims = JSON.parse(base64urlDecode(payload).toString("utf8"));
  } catch {
    return null;
  }

  if (typeof claims.exp !== "number" || claims.exp <= nowSeconds) return null;
  if (typeof claims.sub !== "string" || !/^\d+$/.test(claims.sub)) return null;

  return Number(claims.sub);
}
