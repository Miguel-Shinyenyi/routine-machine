import { describe, expect, it } from "vitest";
import { proxyTarget, safeContentType, sanitizeSwaggerConfig } from "./docsProxy";

// The owner's API documentation (staged Prompt 4, part 4). The frontend adds the owner's session to a few backend
// addresses and nothing else. Which addresses is decided here, by an allow-list, so no path a visitor types can reach any
// other backend route with that session.

describe("proxyTarget", () => {
  it("maps the three specs to their backend addresses", () => {
    expect(proxyTarget(["spec", "backend"])).toBe("/v3/api-docs");
    expect(proxyTarget(["spec", "intelligence"])).toBe("/api/watchtower/intelligence-api-docs");
    expect(proxyTarget(["spec", "config"])).toBe("/v3/api-docs/swagger-config");
  });

  it("maps a Swagger UI file to the backend's UI directory", () => {
    expect(proxyTarget(["ui", "index.html"])).toBe("/swagger-ui/index.html");
    expect(proxyTarget(["ui", "swagger-ui-bundle.js"])).toBe("/swagger-ui/swagger-ui-bundle.js");
    expect(proxyTarget(["ui", "swagger-ui.css"])).toBe("/swagger-ui/swagger-ui.css");
    expect(proxyTarget(["ui", "favicon-32x32.png"])).toBe("/swagger-ui/favicon-32x32.png");
  });

  it("refuses a spec name that is not on the list", () => {
    expect(proxyTarget(["spec", "other"])).toBeNull();
    expect(proxyTarget(["spec", "Backend"])).toBeNull();
    expect(proxyTarget(["spec"])).toBeNull();
  });

  it("refuses any way out of the UI directory", () => {
    for (const file of ["..", ".", ".hidden", "a/b", "a\\b", "..%2f..%2fapi", "../../api/users/me", "a b", "", "x;y", "x?y", "x#y"]) {
      expect(proxyTarget(["ui", file]), file).toBeNull();
    }
  });

  it("refuses a path of the wrong shape or an area the proxy does not serve", () => {
    expect(proxyTarget(undefined)).toBeNull();
    expect(proxyTarget([])).toBeNull();
    expect(proxyTarget(["ui"])).toBeNull();
    expect(proxyTarget(["ui", "index.html", "extra"])).toBeNull();
    expect(proxyTarget(["spec", "backend", "extra"])).toBeNull();
    expect(proxyTarget(["api", "watchtower", "services"])).toBeNull();
    expect(proxyTarget(["actuator", "health"])).toBeNull();
    expect(proxyTarget(["UI", "index.html"])).toBeNull();
  });
});

describe("safeContentType", () => {
  it("keeps the types the Swagger UI needs, with their charset", () => {
    expect(safeContentType("text/html;charset=UTF-8")).toBe("text/html;charset=UTF-8");
    expect(safeContentType("text/css")).toBe("text/css");
    expect(safeContentType("application/javascript")).toBe("application/javascript");
    expect(safeContentType("application/json")).toBe("application/json");
    expect(safeContentType("image/png")).toBe("image/png");
  });

  it("turns anything else into a download, so a stray type is never run by the browser", () => {
    expect(safeContentType("application/x-shockwave-flash")).toBe("application/octet-stream");
    expect(safeContentType("text/xml")).toBe("application/octet-stream");
    expect(safeContentType("")).toBe("application/octet-stream");
    expect(safeContentType(null)).toBe("application/octet-stream");
  });
});

describe("sanitizeSwaggerConfig", () => {
  it("drops the OAuth redirect address, which springdoc builds from the backend's internal host name", () => {
    const config = JSON.stringify({
      configUrl: "/watchtower/docs/spec/config",
      oauth2RedirectUrl: "http://backend:8080/watchtower/docs/ui/oauth2-redirect.html",
      supportedSubmitMethods: [],
      urls: [{ name: "Backend", url: "/watchtower/docs/spec/backend" }],
    });
    const cleaned = JSON.parse(sanitizeSwaggerConfig(config));
    expect(cleaned).not.toHaveProperty("oauth2RedirectUrl");
    expect(JSON.stringify(cleaned)).not.toContain("backend:8080");
  });

  it("keeps everything else, including the read-only setting", () => {
    const config = JSON.stringify({ configUrl: "/x", supportedSubmitMethods: [], urls: [{ name: "A", url: "/a" }] });
    expect(JSON.parse(sanitizeSwaggerConfig(config))).toEqual({ configUrl: "/x", supportedSubmitMethods: [], urls: [{ name: "A", url: "/a" }] });
  });

  it("returns text that is not JSON as it came, rather than failing", () => {
    expect(sanitizeSwaggerConfig("not json")).toBe("not json");
  });
});
