import { cookies } from "next/headers";
import { signSessionToken, verifySessionToken } from "./jwt";

const SESSION_COOKIE = "session";
const SESSION_MAX_AGE_SECONDS = 60 * 60 * 24 * 7; // matches the JWT's own 7-day exp

export async function createSession(userId: number): Promise<void> {
  const store = await cookies();
  store.set(SESSION_COOKIE, signSessionToken(userId), {
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "lax",
    path: "/",
    maxAge: SESSION_MAX_AGE_SECONDS,
  });
}

export async function destroySession(): Promise<void> {
  const store = await cookies();
  store.delete(SESSION_COOKIE);
}

// Returns the logged-in user's id, or null if there's no session, an expired one, or a
// tampered cookie — every caller treats all three the same way (not logged in).
export async function getSessionUserId(): Promise<number | null> {
  const store = await cookies();
  const token = store.get(SESSION_COOKIE)?.value;
  if (!token) return null;
  return verifySessionToken(token);
}

// The session cookie itself, for the one place this needs to ride along as a bearer token on
// an authenticated backend call (GET /api/users/me) — everywhere else reads getSessionUserId.
export async function getSessionToken(): Promise<string | null> {
  const store = await cookies();
  return store.get(SESSION_COOKIE)?.value ?? null;
}
