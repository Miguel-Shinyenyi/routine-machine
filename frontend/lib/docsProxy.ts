// The owner's API documentation (staged Prompt 4, part 4; docs/frontend.md). A browser cannot send a bearer token when it
// loads a page, so the frontend's /watchtower/docs route fetches a few backend addresses with the signed-in person's
// session and hands the answer on. The backend decides who the owner is (a plain 404 for everyone else); this file decides
// which backend addresses the route may touch at all. It is an allow-list, so nothing a visitor types in the address can
// reach another backend route with their session.

// One file name inside the Swagger UI directory: letters, digits, dot, underscore, hyphen, not starting with a dot, so no
// way up out of the directory and no hidden file.
const UI_FILE = /^[A-Za-z0-9][A-Za-z0-9._-]*$/;

const SPECS: Record<string, string> = {
  backend: "/v3/api-docs",
  intelligence: "/api/watchtower/intelligence-api-docs",
  config: "/v3/api-docs/swagger-config",
};

// The backend address for a path under /watchtower/docs, or null when the path is not one the route serves.
export function proxyTarget(segments: string[] | undefined): string | null {
  if (!segments || segments.length !== 2) return null;
  const [area, name] = segments;
  if (area === "spec" && Object.hasOwn(SPECS, name)) return SPECS[name];
  if (area === "ui" && UI_FILE.test(name)) return `/swagger-ui/${name}`;
  return null;
}

// The media types the Swagger UI's files and the specs come as. Anything else is sent as a download, so a stray type is
// never run by the browser.
const SAFE_TYPES = new Set([
  "text/html",
  "text/css",
  "application/javascript",
  "text/javascript",
  "application/json",
  "image/png",
  "image/svg+xml",
  "image/x-icon",
  "image/vnd.microsoft.icon",
]);

export function safeContentType(value: string | null): string {
  const base = (value ?? "").split(";")[0].trim().toLowerCase();
  return SAFE_TYPES.has(base) ? (value as string) : "application/octet-stream";
}

// The backend address of the Swagger UI's configuration. Its answer is the one the route rewrites (below).
export const CONFIG_TARGET = SPECS.config;

// springdoc builds oauth2RedirectUrl from the address the request came in on, which behind this route is the backend's
// internal host name (backend:8080). Nothing uses it (the UI is read-only and has no OAuth), so it is dropped and the
// internal name never reaches the browser. Text that is not JSON is returned as it came.
export function sanitizeSwaggerConfig(text: string): string {
  try {
    const config = JSON.parse(text);
    delete config.oauth2RedirectUrl;
    return JSON.stringify(config);
  } catch {
    return text;
  }
}
