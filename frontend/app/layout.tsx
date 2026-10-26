import type { Metadata } from "next";
import { AnalyticsConsent } from "@/components/AnalyticsConsent";
import { Shell } from "@/components/Shell";
import { analyticsConfigured } from "@/lib/analytics/consent";
import { readPendingEvents } from "@/lib/analytics/queue";
import { authenticatedBackendFetch, backendFetch, intelligenceFetch } from "@/lib/api";
import { todayIsoDate } from "@/lib/schedule";
import { getSessionToken, getSessionUserId } from "@/lib/auth/session";
import type { RoutineItem, Tally, Task, WateringHoleStatus } from "@/lib/types";
import "./globals.css";

export const metadata: Metadata = {
  title: "Routine Machine",
  description: "The Shepherd: daily routines, the path, the flock, and the tally",
};

// Runs before hydration so the saved theme applies before first paint, avoiding a flash of
// the wrong theme. Reads localStorage directly (try/catch: private browsing, blocked
// storage) rather than through React state, since nothing here can wait for a render.
const SET_THEME_BEFORE_PAINT = `
(function () {
  try {
    var theme = localStorage.getItem("theme");
    if (theme === "light" || theme === "dark") {
      document.documentElement.setAttribute("data-theme", theme);
    }
  } catch (e) {}
})();
`;

// Read-only, used only for the sidebar's "N of M" badge and "kept watch for N days" line —
// never materializeDay, which can create tasks as a side effect unsuitable for every page load.
// Both calls are caught, not awaited into a hard failure: the sidebar still renders, just
// without that one piece of context, if either service is briefly unreachable.
async function fetchShellContext(): Promise<{
  doneCount: number | null;
  totalCount: number | null;
  daysShownUp: number | null;
  routineCount: number | null;
  connectedCount: number | null;
  userEmail: string | null;
  demo: boolean;
}> {
  const sessionToken = await getSessionToken();
  const userId = await getSessionUserId();
  // Signed out (login, register): no personal data at all, so no personal calls either. A
  // signed-out visitor used to see the founder's own counts in this sidebar.
  const [tasks, tally, routineItems, wateringHoles, me] = await Promise.all([
    userId !== null
      ? backendFetch<Task[]>(`/api/tasks?date=${todayIsoDate()}`).catch(() => null)
      : Promise.resolve(null),
    userId !== null
      ? intelligenceFetch<Tally>("/tally").catch(() => null)
      : Promise.resolve(null),
    userId !== null
      ? backendFetch<RoutineItem[]>("/api/routine-items").catch(() => null)
      : Promise.resolve(null),
    // Owner-only integrations (docs/backend.md): anyone else gets every source "not connected".
    userId !== null
      ? backendFetch<WateringHoleStatus[]>("/api/watering-holes").catch(() => null)
      : Promise.resolve(null),
    sessionToken
      ? authenticatedBackendFetch<{ email: string; demo?: boolean }>("/api/users/me", sessionToken).catch(() => null)
      : Promise.resolve(null),
  ]);
  return {
    doneCount: tasks ? tasks.filter((t) => t.status === "DONE").length : null,
    totalCount: tasks ? tasks.length : null,
    daysShownUp: tally ? tally.daysShownUpCount : null,
    // This user's own routine items, the same list Flock shows.
    routineCount: routineItems ? routineItems.length : null,
    // Reader's notes is always-on but isn't in /api/watering-holes (it's not a toggleable
    // external sync) — +1 matches Watering holes' own page, which always shows it connected.
    connectedCount: wateringHoles ? wateringHoles.filter((s) => s.enabled).length + 1 : null,
    // null when logged out, or when the cookie's token has expired/been tampered with — the
    // sidebar treats both exactly like "not signed in", same as getSessionUserId elsewhere.
    userEmail: me ? me.email : null,
    // The one flagged sample account (staged Prompt 6): every page says so.
    demo: me ? me.demo === true : false,
  };
}

export default async function RootLayout({ children }: LayoutProps<"/">) {
  const shellContext = await fetchShellContext();

  // The signed-in person's internal id, for the analytics service to identify them by (and by nothing else). Null when signed out.
  // The demo account is never identified: a demonstration is not a person to count.
  const analyticsUserId = shellContext.demo ? null : await getSessionUserId();
  // Notes a Server Action left after it succeeded (lib/analytics/queue.ts), read again and checked here, then handed to the browser, which
  // sends them only if the visitor accepted. None unless analytics is configured.
  const analyticsOn = analyticsConfigured(process.env.POSTHOG_KEY, process.env.POSTHOG_HOST);
  const pendingEvents = await readPendingEvents();
  return (
    <html lang="en" className="h-full antialiased">
      <head>
        <script dangerouslySetInnerHTML={{ __html: SET_THEME_BEFORE_PAINT }} />
      </head>
      <body className="min-h-full bg-canvas text-ink">
        <Shell {...shellContext}>{children}</Shell>
        {/* Analytics (staged Prompt 5): only when the deployment has a key and a host, and only after the visitor accepts. */}
        {analyticsOn && (
          <AnalyticsConsent
            posthogKey={process.env.POSTHOG_KEY as string}
            posthogHost={process.env.POSTHOG_HOST as string}
            userId={analyticsUserId}
            pendingEvents={pendingEvents}
          />
        )}
      </body>
    </html>
  );
}
