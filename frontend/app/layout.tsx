import type { Metadata } from "next";
import { Shell } from "@/components/Shell";
import { backendFetch, intelligenceFetch } from "@/lib/api";
import { todayIsoDate } from "@/lib/schedule";
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
}> {
  const [tasks, tally, routineItems, wateringHoles] = await Promise.all([
    backendFetch<Task[]>(`/api/tasks?date=${todayIsoDate()}`).catch(() => null),
    intelligenceFetch<Tally>("/tally").catch(() => null),
    backendFetch<RoutineItem[]>("/api/routine-items").catch(() => null),
    backendFetch<WateringHoleStatus[]>("/api/watering-holes").catch(() => null),
  ]);
  return {
    doneCount: tasks ? tasks.filter((t) => t.status === "DONE").length : null,
    totalCount: tasks ? tasks.length : null,
    daysShownUp: tally ? tally.daysShownUpCount : null,
    // Same unowned filter Flock itself applies — the sidebar badge should match the page it
    // links to, not include Phase 8's hillside demo users' own items.
    routineCount: routineItems ? routineItems.filter((item) => item.userId == null).length : null,
    // Reader's notes is always-on but isn't in /api/watering-holes (it's not a toggleable
    // external sync) — +1 matches Watering holes' own page, which always shows it connected.
    connectedCount: wateringHoles ? wateringHoles.filter((s) => s.enabled).length + 1 : null,
  };
}

export default async function RootLayout({ children }: LayoutProps<"/">) {
  const shellContext = await fetchShellContext();

  return (
    <html lang="en" className="h-full antialiased">
      <head>
        <script dangerouslySetInnerHTML={{ __html: SET_THEME_BEFORE_PAINT }} />
      </head>
      <body className="min-h-full bg-canvas text-ink">
        <Shell {...shellContext}>{children}</Shell>
      </body>
    </html>
  );
}
