import { notFound, redirect } from "next/navigation";
import { getSessionToken } from "@/lib/auth/session";

// The way in to the owner's API documentation (staged Prompt 4, part 4). It checks with the backend first, so that anyone
// who is not the owner gets the plain 404 and not a redirect that would show the page exists. The owner is sent to the
// Swagger UI with the address of its configuration, which the UI needs because it cannot guess it behind this route.
const BACKEND_URL = process.env.BACKEND_URL ?? "http://localhost:8080";

export async function GET() {
  const token = await getSessionToken();
  if (token === null) {
    notFound();
  }
  let check: Response;
  try {
    check = await fetch(`${BACKEND_URL}/v3/api-docs/swagger-config`, {
      headers: { Authorization: `Bearer ${token}` },
      cache: "no-store",
      redirect: "manual",
    });
  } catch {
    return new Response("The backend did not answer.", { status: 502 });
  }
  if (check.status === 404 || check.status === 401) {
    notFound();
  }
  if (!check.ok) {
    return new Response("The documentation is not available.", { status: 502 });
  }
  redirect("/watchtower/docs/ui/index.html?configUrl=/watchtower/docs/spec/config");
}
