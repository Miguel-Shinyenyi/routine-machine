import type { Range } from "./chart";
import type { WatchtowerLogs } from "./types";
import { backendFetch } from "./api";
import { getOrNull, type Fetcher } from "./watchtower";

// The levels the backend accepts (WatchtowerLogsService.LEVELS, which a backend test keeps equal to the Grafana
// dashboard's list). The page offers only these, and drops anything else from the address before it is sent.
export const LOG_LEVELS = ["INFO", "WARN", "WARNING", "ERROR", "CRITICAL", "DEBUG"];

// The backend's own pattern for a request id or a trace id: 1 to 64 letters, digits, dot, underscore or hyphen.
const SAFE_ID = /^[A-Za-z0-9._-]{1,64}$/;

export interface LogFilters {
  level: string | null;
  requestId: string | null;
}

type Param = string | string[] | undefined;

// Reads the level and the id from the page address. A value that is not allowed is dropped, never sent on, so the
// backend is not asked a question it would refuse. badId says an id was dropped, so the page can tell the owner.
export function parseLogFilters(params: { level?: Param; id?: Param }): LogFilters & { badId: boolean } {
  const level = typeof params.level === "string" && LOG_LEVELS.includes(params.level) ? params.level : null;
  const id = typeof params.id === "string" ? params.id : "";
  if (id === "") return { level, requestId: null, badId: false };
  return SAFE_ID.test(id) ? { level, requestId: id, badId: false } : { level, requestId: null, badId: true };
}

export function fetchLogs(
  range: Range,
  filters: LogFilters,
  fetcher: Fetcher = backendFetch,
): Promise<WatchtowerLogs | null> {
  const query = new URLSearchParams({ range });
  if (filters.level) query.set("level", filters.level);
  if (filters.requestId) query.set("requestId", filters.requestId);
  return getOrNull<WatchtowerLogs>(fetcher, `/api/watchtower/logs?${query.toString()}`);
}

// A trace link comes from the backend's configuration, but it is still shown only if it is a plain web address.
export function safeHref(url: string | null): string | null {
  return url !== null && /^https?:\/\//i.test(url) ? url : null;
}

// To the second, in UTC, with a fixed format that does not depend on the server's locale or time zone.
export function formatUtcSeconds(iso: string): string {
  return iso.slice(0, 19).replace("T", " ");
}

export type LevelTone = "bad" | "warn" | "plain";

export function levelTone(level: string | null): LevelTone {
  if (level === "ERROR" || level === "CRITICAL") return "bad";
  if (level === "WARN" || level === "WARNING") return "warn";
  return "plain";
}

// The address of the page for a range and a set of filters, so the range links and the "this request" links keep
// each other's settings. The values are already checked (parseLogFilters), and URLSearchParams encodes them anyway.
export function pageHref(range: Range, filters: LogFilters): string {
  const query = new URLSearchParams({ range });
  if (filters.level) query.set("level", filters.level);
  if (filters.requestId) query.set("id", filters.requestId);
  return `/watchtower?${query.toString()}`;
}
