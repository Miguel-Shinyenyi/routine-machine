import type { Metadata } from "next";
import { Shell } from "@/components/Shell";
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

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="en" className="h-full antialiased">
      <head>
        <script dangerouslySetInnerHTML={{ __html: SET_THEME_BEFORE_PAINT }} />
      </head>
      <body className="min-h-full bg-canvas text-ink">
        <Shell>{children}</Shell>
      </body>
    </html>
  );
}
