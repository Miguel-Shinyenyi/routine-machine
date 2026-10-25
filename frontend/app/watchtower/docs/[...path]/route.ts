import type { NextRequest } from "next/server";
import { notFound } from "next/navigation";
import { getSessionToken } from "@/lib/auth/session";
import { CONFIG_TARGET, proxyTarget, safeContentType, sanitizeSwaggerConfig } from "@/lib/docsProxy";

// The owner's API documentation (staged Prompt 4, part 4; docs/frontend.md). GET only: no other method is exported, so
// Next answers the rest with 405. It adds the signed-in person's session to the few backend addresses lib/docsProxy.ts
// allows and hands the answer on. The backend checks that the person is the owner; to everyone else, and to a signed-out
// visitor, this is a plain 404, the same as the rest of /watchtower.
const BACKEND_URL = process.env.BACKEND_URL ?? "http://localhost:8080";

export async function GET(_request: NextRequest, context: { params: Promise<{ path: string[] }> }) {
  const { path } = await context.params;
  const target = proxyTarget(path);
  const token = await getSessionToken();
  if (target === null || token === null) {
    notFound();
  }

  let upstream: Response;
  try {
    upstream = await fetch(`${BACKEND_URL}${target}`, {
      headers: { Authorization: `Bearer ${token}` },
      cache: "no-store",
      redirect: "manual",
    });
  } catch {
    return new Response("The backend did not answer.", { status: 502 });
  }
  // 404 is the backend's way of saying "not the owner"; 401 is an expired or malformed token. Neither says the page exists.
  if (upstream.status === 404 || upstream.status === 401) {
    notFound();
  }

  // Only the type of the body is passed on, never the backend's other headers or any cookie. The UI's configuration is
  // read and tidied first (lib/docsProxy.ts, sanitizeSwaggerConfig); everything else streams through as it is.
  const body = target === CONFIG_TARGET && upstream.ok ? sanitizeSwaggerConfig(await upstream.text()) : upstream.body;
  return new Response(body, {
    status: upstream.status,
    headers: {
      "Content-Type": safeContentType(upstream.headers.get("content-type")),
      "Cache-Control": "no-store",
      "X-Content-Type-Options": "nosniff",
    },
  });
}
