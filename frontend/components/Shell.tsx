"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { ThemeToggle } from "./ThemeToggle";

// Icon paths copied from docs/design-reference/pages/*.html's inline nav SVGs (the canvas
// design export), not a new icon set invented here — see docs/frontend.md's decisions log.
const ICONS: Record<string, React.ReactNode> = {
  "/today": (
    <>
      <circle cx="12" cy="12" r="4" />
      <path d="M12 2v2M12 20v2M2 12h2M20 12h2M5 5l1.5 1.5M17.5 17.5L19 19M5 19l1.5-1.5M17.5 6.5L19 5" />
    </>
  ),
  "/path": <path d="M6 21c0-5 12-5 12-10S9 7 9 3" />,
  "/flock": (
    <>
      <path d="M7 16a3 3 0 0 1 0-6 4 4 0 0 1 7.5-1.5A3.5 3.5 0 0 1 17 16z" />
      <path d="M9 16v3M14 16v3" />
    </>
  ),
  "/tally": <path d="M5 5v14M9 5v14M13 5v14M17 5v14M3 15l18-6" />,
  "/reading": <path d="M4 19V5a2 2 0 0 1 2-2h13v16H6a2 2 0 0 0-2 2z" />,
  "/granary": (
    <>
      <path d="M8 3h8M9 3v3L6 10v9a2 2 0 0 0 2 2h8a2 2 0 0 0 2-2v-9l-3-4V3" />
      <path d="M6 13h12" />
    </>
  ),
  "/hillside": <path d="M2 19l7-10 4 5 3-3 6 8z" />,
  "/watering-holes": <path d="M12 3s6 7 6 11a6 6 0 0 1-12 0c0-4 6-11 6-11z" />,
};

const SIDEBAR_LINKS = [
  { href: "/today", label: "Today" },
  { href: "/path", label: "Path" },
  { href: "/flock", label: "Flock" },
  { href: "/tally", label: "Tally" },
  { href: "/reading", label: "Reading" },
  { href: "/granary", label: "Granary" },
  { href: "/hillside", label: "Hillside" },
  { href: "/watering-holes", label: "Watering holes" },
];

// Matches the updated design reference's phone tab bar exactly: Today, Reading, Granary,
// Tally, Hillside — Path moved off (its "opens from Today" cross-link isn't built, same
// already-flagged gap as Flock/Watering holes below), now that Granary actually exists.
const PHONE_LINKS = ["/today", "/reading", "/granary", "/tally", "/hillside"];

function NavIcon({ href }: { href: string }) {
  return (
    <svg
      viewBox="0 0 24 24"
      width="18"
      height="18"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      className="shrink-0"
    >
      {ICONS[href]}
    </svg>
  );
}

export function Shell({
  children,
  doneCount,
  totalCount,
  daysShownUp,
  routineCount,
  connectedCount,
  userEmail,
}: {
  children: React.ReactNode;
  doneCount: number | null;
  totalCount: number | null;
  daysShownUp: number | null;
  routineCount: number | null;
  connectedCount: number | null;
  userEmail: string | null;
}) {
  const pathname = usePathname();
  const watchedFor =
    daysShownUp === null
      ? null
      : `The Shepherd has kept watch with you for ${daysShownUp} ${daysShownUp === 1 ? "day" : "days"}.`;

  // /login and /register are the one place in this app meant for someone who isn't signed in
  // yet, including a brand-new visitor — the nav (every link in it leads to data scoped to an
  // account that may not exist yet) and the "N of M tended" badges it carries would be
  // premature and, on phone, the floating tab bar would sit over a two-field form for no
  // reason. No Shell chrome at all on these two routes.
  if (pathname === "/login" || pathname === "/register") {
    return <main className="mx-auto w-full max-w-[1100px] px-5 py-6">{children}</main>;
  }

  // The cohort dashboard gets its own nav, matching the design reference: cohort.html's
  // sidebar (aria-label="Programme admin") is a completely separate nav from the personal
  // shepherd app's, not an entry added to it. Shown to whoever can reach the page at all —
  // an admin or a learner, the backend's own 403 already gates anyone else — since "learners
  // can read this exact page" in the reference means the same sidebar, not a second one.
  // No "Seats and invites"/"Reports"/"Privacy and data" links: those pages don't exist yet,
  // and a nav link to nothing would be worse than no nav link.
  const cohortMatch = pathname.match(/^\/cohort\/([^/]+)/);
  if (cohortMatch) {
    const cohortId = cohortMatch[1];
    return (
      <div className="flex min-h-full flex-col md:flex-row">
        <aside className="glass sticky top-0 hidden h-screen w-[220px] shrink-0 flex-col gap-1 border-r hairline px-3 py-5 md:flex">
          <Link
            href={`/cohort/${cohortId}`}
            className="mb-4 flex items-center gap-2.5 px-2.5 py-1.5 text-[15px] font-semibold text-ink"
          >
            <span
              className="flex h-[30px] w-[30px] shrink-0 items-center justify-center rounded-[8px]"
              style={{ background: "var(--tint)" }}
              aria-hidden="true"
            >
              <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="#1d1d1f" strokeWidth="2.4" strokeLinecap="round">
                <path d="M14 21V8a4 4 0 0 0-8 0" />
              </svg>
            </span>
            Routine Machine
          </Link>
          <Link
            href={`/cohort/${cohortId}`}
            aria-current="page"
            className="flex min-h-9 items-center gap-2.5 rounded-[8px] px-2.5 py-1.5 text-[14px] text-ink"
            style={{ background: "rgba(233,162,59,.2)", fontWeight: 600 }}
          >
            <svg
              viewBox="0 0 24 24"
              width="18"
              height="18"
              fill="none"
              stroke="var(--tint-text)"
              strokeWidth="1.8"
              strokeLinecap="round"
              strokeLinejoin="round"
              aria-hidden="true"
              className="shrink-0"
            >
              <path d="M4 19h16M7 16V9M12 16V5M17 16v-4" />
            </svg>
            Cohort {cohortId}
          </Link>
          <div className="mt-auto flex flex-col gap-2 pt-4">
            <Link href="/today" className="flex min-h-9 items-center gap-2.5 rounded-[8px] px-2.5 py-1.5 text-[14px] text-ink hover:bg-fill">
              My own Today
            </Link>
            <p className="px-2.5 text-[12px] text-muted">
              Admins see cohort totals only. Learners can read this exact page.
            </p>
            <ThemeToggle />
          </div>
        </aside>

        <main className="mx-auto w-full max-w-[1100px] flex-1 px-5 pb-10 pt-6">{children}</main>
      </div>
    );
  }

  return (
    <div className="flex min-h-full flex-col md:flex-row">
      <aside className="glass sticky top-0 hidden h-screen w-[220px] shrink-0 flex-col gap-1 border-r hairline px-3 py-5 md:flex">
        <Link href="/today" className="mb-4 flex items-center gap-2.5 px-2.5 py-1.5 text-[15px] font-semibold text-ink">
          <span
            className="flex h-[30px] w-[30px] shrink-0 items-center justify-center rounded-[8px]"
            style={{ background: "var(--tint)" }}
            aria-hidden="true"
          >
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="#1d1d1f" strokeWidth="2.4" strokeLinecap="round">
              <path d="M14 21V8a4 4 0 0 0-8 0" />
            </svg>
          </span>
          Routine Machine
        </Link>
        {SIDEBAR_LINKS.map((link) => {
          const isActive = pathname === link.href;
          return (
            <Link
              key={link.href}
              href={link.href}
              aria-current={isActive ? "page" : undefined}
              className="flex min-h-9 items-center gap-2.5 rounded-[8px] px-2.5 py-1.5 text-[14px] text-ink hover:bg-fill"
              style={isActive ? { background: "rgba(233,162,59,.2)", fontWeight: 600 } : undefined}
            >
              <span style={{ color: "var(--tint-text)" }}>
                <NavIcon href={link.href} />
              </span>
              {link.label}
              {link.href === "/today" && doneCount !== null && totalCount !== null && (
                <small className="ml-auto text-[12px] font-medium text-muted">
                  {doneCount} of {totalCount}
                </small>
              )}
              {link.href === "/flock" && routineCount !== null && (
                <small className="ml-auto text-[12px] font-medium text-muted">{routineCount}</small>
              )}
              {link.href === "/watering-holes" && connectedCount !== null && (
                <small className="ml-auto text-[12px] font-medium text-muted">{connectedCount} on</small>
              )}
            </Link>
          );
        })}
        <div className="mt-auto flex flex-col gap-2 pt-4">
          {watchedFor && <p className="px-2.5 text-[12px] text-muted">{watchedFor}</p>}
          {userEmail ? (
            <Link
              href="/profile"
              aria-current={pathname === "/profile" ? "page" : undefined}
              className="flex min-h-9 items-center gap-2.5 rounded-[8px] px-2.5 py-1.5 text-[14px] text-ink hover:bg-fill"
              style={pathname === "/profile" ? { background: "rgba(233,162,59,.2)", fontWeight: 600 } : undefined}
              title={userEmail}
            >
              <span
                aria-hidden="true"
                className="flex h-[18px] w-[18px] shrink-0 items-center justify-center rounded-full text-[10px] font-bold text-white"
                style={{ background: "#6b4226" }}
              >
                {userEmail.charAt(0).toUpperCase()}
              </span>
              Profile
            </Link>
          ) : (
            <Link href="/login" className="px-2.5 text-[12px] font-medium text-tint-text hover:underline">
              Sign in
            </Link>
          )}
          <ThemeToggle />
        </div>
      </aside>

      <main className="mx-auto w-full max-w-[1100px] flex-1 px-5 pb-28 pt-6 md:pb-10">
        {children}
      </main>

      <nav className="glass fixed inset-x-4 bottom-4 flex items-center justify-around rounded-full border hairline py-2 shadow-none md:hidden">
        {PHONE_LINKS.map((href) => {
          const label = SIDEBAR_LINKS.find((l) => l.href === href)?.label ?? href;
          const isActive = pathname === href;
          return (
            <Link
              key={href}
              href={href}
              aria-current={isActive ? "page" : undefined}
              className="flex min-w-[44px] flex-col items-center gap-0.5 px-2 py-1 text-center text-[11px]"
              style={{ color: isActive ? "var(--tint-text)" : "var(--ink)" }}
            >
              <NavIcon href={href} />
              {label}
            </Link>
          );
        })}
      </nav>
    </div>
  );
}
