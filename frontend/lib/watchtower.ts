import { ApiError, backendFetch } from "./api";
import type { WatchtowerService, WatchtowerSync } from "./types";

type Fetcher = <T>(path: string) => Promise<T>;

// The owner-only operations page (docs/observability.md, part 5). The backend answers 404 to everyone but the
// owner, signed in or not, so the page is not known to exist. A 404 here is therefore "show the 404 page", not an
// error. Any other failure (backend down, a 500) is a real error and is thrown, never turned into a 404.
async function getOrNull<T>(fetcher: Fetcher, path: string): Promise<T | null> {
  try {
    return await fetcher<T>(path);
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      return null;
    }
    throw error;
  }
}

export function fetchServices(fetcher: Fetcher = backendFetch): Promise<WatchtowerService[] | null> {
  return getOrNull<WatchtowerService[]>(fetcher, "/api/watchtower/services");
}

export function fetchSync(fetcher: Fetcher = backendFetch): Promise<WatchtowerSync | null> {
  return getOrNull<WatchtowerSync>(fetcher, "/api/watchtower/sync");
}

export type Tone = "good" | "bad" | "neutral";

// UP is good and DOWN is bad. NOT_CONFIGURED, and any status this page has not heard of, is neutral: a service the
// owner did not switch on is not an outage.
export function serviceTone(status: string): Tone {
  if (status === "UP") return "good";
  if (status === "DOWN") return "bad";
  return "neutral";
}

// ok is good and failing is bad. Everything else (switched off, push only, unknown, never succeeded) is neutral:
// those say what is not known or not wanted, not that something broke.
export function syncTone(result: string): Tone {
  if (result === "ok") return "good";
  if (result === "failing") return "bad";
  return "neutral";
}

// To the minute, in UTC, with a fixed format that does not depend on the server's locale or time zone. Null is a
// time that never happened.
export function formatUtc(iso: string | null): string {
  if (iso === null) return "Never";
  return iso.slice(0, 16).replace("T", " ") + " UTC";
}
