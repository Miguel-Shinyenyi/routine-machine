import { ApiError, backendFetch } from "./api";
import type { WatchtowerService } from "./types";

// The owner-only operations page (docs/observability.md, part 5). The backend answers 404 to everyone but the
// owner, signed in or not, so the page is not known to exist. A 404 here is therefore "show the 404 page", not an
// error. Any other failure (backend down, a 500) is a real error and is thrown, never turned into a 404.
export async function fetchServices(
  fetcher: <T>(path: string) => Promise<T> = backendFetch,
): Promise<WatchtowerService[] | null> {
  try {
    return await fetcher<WatchtowerService[]>("/api/watchtower/services");
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      return null;
    }
    throw error;
  }
}

export type Tone = "good" | "bad" | "neutral";

// UP is good and DOWN is bad. NOT_CONFIGURED, and any status this page has not heard of, is neutral: a service the
// owner did not switch on is not an outage.
export function serviceTone(status: string): Tone {
  if (status === "UP") return "good";
  if (status === "DOWN") return "bad";
  return "neutral";
}
