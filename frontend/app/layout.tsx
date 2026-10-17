import type { Metadata } from "next";
import { Shell } from "@/components/Shell";
import { backendFetch, intelligenceFetch } from "@/lib/api";
import { todayIsoDate } from "@/lib/schedule";
import type { Tally, Task } from "@/lib/types";
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
async function fetchShellContext(): Promise<{ doneCount: number | null; totalCount: number | null; daysShownUp: number | null }> {
  const [tasks, tally] = await Promise.all([
    backendFetch<Task[]>(`/api/tasks?date=${todayIsoDate()}`).catch(() => null),
    intelligenceFetch<Tally>("/tally").catch(() => null),
  ]);
  return {
    doneCount: tasks ? tasks.filter((t) => t.status === "DONE").length : null,
    totalCount: tasks ? tasks.length : null,
    daysShownUp: tally ? tally.daysShownUpCount : null,
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
