// The time zone the browser reports at sign-up, or null when it is missing or not a real zone.
// The backend does not check a zone's name and breaks on a bad one, so only a valid one passes.
export function registrationTimeZone(raw: unknown): string | null {
  if (typeof raw !== "string") return null;
  const zone = raw.trim();
  if (zone === "" || zone.length > 64) return null;
  try {
    new Intl.DateTimeFormat("en", { timeZone: zone });
    return zone;
  } catch {
    return null;
  }
}
