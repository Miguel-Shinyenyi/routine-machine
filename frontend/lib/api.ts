// Server-side only: no auth exists in this project (single user, no login), so these are plain
// fetch wrappers, unlike settlement-engine's frontend which layers a bearer token on top.
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

async function request<T>(baseUrl: string, path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(`${baseUrl}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
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

export function intelligenceFetch<T>(path: string, init?: RequestInit): Promise<T> {
  return request<T>(INTELLIGENCE_SERVICE_URL, path, init);
}
