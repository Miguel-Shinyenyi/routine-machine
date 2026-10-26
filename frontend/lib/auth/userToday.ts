import { cache } from "react";
import { authenticatedBackendFetch } from "@/lib/api";
import type { Profile } from "@/lib/types";
import { todayInZone } from "@/lib/userToday";
import { getSessionToken } from "./session";

// Today's date for the signed-in person, in their own time zone (Backlog P). Read once per request however many components ask. Signed out,
// or if the profile cannot be read, it is the date in UTC, so a page never fails because of it.
export const getUserToday = cache(async (): Promise<string> => {
  const token = await getSessionToken();
  if (!token) return todayInZone(null);
  const profile = await authenticatedBackendFetch<Profile>("/api/users/me/profile", token).catch(() => null);
  return todayInZone(profile?.timeZone);
});
