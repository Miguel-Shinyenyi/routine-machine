// Server-side only. Most of this app still talks to the backend unauthenticated (single-user
// demo mode, or the demo-mode asUserId/userId query params — see docs/backend.md); real
// sessions exist now (lib/auth/session.ts) but only one endpoint currently requires one, via
// authenticatedBackendFetch below.
const BACKEND_URL = process.env.BACKEND_URL ?? "http://localhost:8080";
const INTELLIGENCE_SERVICE_URL = process.env.INTELLIGENCE_SERVICE_URL ?? "http://localhost:8000";

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
  }
}

// The signed-in person's session token as a bearer header, or nothing when there is no session
// (the login and register calls, or code running outside a request). Routes that act for a user
// read who it is from this token, never from the request. Passed first, so a caller's own
// Authorization header still wins.
async function sessionAuthorization(): Promise<Record<string, string>> {
  try {
    const { getSessionToken } = await import("./auth/session");
    const token = await getSessionToken();
    return token ? { Authorization: `Bearer ${token}` } : {};
  } catch {
    return {};
  }
}

async function request<T>(baseUrl: string, path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(`${baseUrl}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...(baseUrl === BACKEND_URL ? await sessionAuthorization() : {}),
      ...(init.headers ?? {}),
    },
    // Always fetch fresh: task/log state changes on nearly every request, and this is a
    // single-user personal tool where staleness would be actively confusing, not a performance
    // problem worth caching around.
    cache: "no-store",
  });

  if (!response.ok) {
    const body = await response.text();
    throw new ApiError(response.status, body || response.statusText);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return response.json() as Promise<T>;
}

export function backendFetch<T>(path: string, init?: RequestInit): Promise<T> {
  return request<T>(BACKEND_URL, path, init);
}

// For the one call that needs the session's own token as a bearer header (GET
// /api/users/me) — see lib/auth/session.ts. Everything else in this app still talks to the
// backend unauthenticated, matching the demo-mode asUserId pattern documented in backend.md.
export function authenticatedBackendFetch<T>(path: string, token: string, init: RequestInit = {}): Promise<T> {
  return request<T>(BACKEND_URL, path, {
    ...init,
    headers: { Authorization: `Bearer ${token}`, ...(init.headers ?? {}) },
  });
}

export function intelligenceFetch<T>(path: string, init?: RequestInit): Promise<T> {
  return request<T>(INTELLIGENCE_SERVICE_URL, path, init);
}

// Phase 11: M-Pesa statement upload (lib/actions.ts::importMpesaStatement). No
// "Content-Type: application/json" here — fetch sets multipart/form-data with the right
// boundary itself from the FormData body; forcing a Content-Type header would break it.
export async function backendFetchMultipart<T>(path: string, formData: FormData): Promise<T> {
  const response = await fetch(`${BACKEND_URL}${path}`, {
    method: "POST",
    body: formData,
    headers: await sessionAuthorization(),
    cache: "no-store",
  });
  if (!response.ok) {
    const body = await response.text();
    throw new ApiError(response.status, body || response.statusText);
  }
  return response.json() as Promise<T>;
}
