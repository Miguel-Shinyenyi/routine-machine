import type { RoutineItemCategory } from "./types";

// Icon paths copied from docs/design-reference/pages/flock.html's per-routine icons. The
// reference hand-picks one icon per specific sample routine name, which a real app can't do
// for an arbitrary user-typed name — so this matches on a handful of common keywords (the
// same routines this project's own seed data and most people's flocks actually use) and
// falls back to a generic per-category icon otherwise, rather than a plain initial letter.
const NAME_ICONS: Record<string, React.ReactNode> = {
  sleep: <path d="M20 14A8 8 0 1 1 10 4a6 6 0 0 0 10 10z" />,
  lifting: <path d="M6 8v8M18 8v8M3 10v4M21 10v4M6 12h12" />,
  weight: <path d="M6 8v8M18 8v8M3 10v4M21 10v4M6 12h12" />,
  exercise: <path d="M6 8v8M18 8v8M3 10v4M21 10v4M6 12h12" />,
  walk: (
    <>
      <circle cx="13" cy="4" r="2" />
      <path d="M9 21l2-7 3 3v5M7 12l3-4 4 1 3 3" />
    </>
  ),
  skat: <path d="M4 14h14l2-4M6 18a2 2 0 1 0 0 .1M16 18a2 2 0 1 0 0 .1" />,
  shower: <path d="M12 3s5 6 5 10a5 5 0 0 1-10 0c0-4 5-10 5-10z" />,
  wake: <path d="M12 3s5 6 5 10a5 5 0 0 1-10 0c0-4 5-10 5-10z" />,
  reading: <path d="M4 19V5a2 2 0 0 1 2-2h13v16H6a2 2 0 0 0-2 2z" />,
  learning: (
    <>
      <path d="M3 8l9-4 9 4-9 4zM7 10v5c3 2 7 2 10 0v-5" />
    </>
  ),
  journal: <path d="M4 20h4L19 9l-4-4L4 16z" />,
  writ: <path d="M6 3h9l4 4v14H6zM9 12h7M9 16h7" />,
  article: <path d="M6 3h9l4 4v14H6zM9 12h7M9 16h7" />,
  "deep work": <path d="M8 9l-4 3 4 3M16 9l4 3-4 3" />,
  "build routine machine": <path d="M8 9l-4 3 4 3M16 9l4 3-4 3" />,
  "paid work": <><rect x="4" y="7" width="16" height="12" rx="2" /><path d="M9 7V5h6v2" /></>,
  cook: <path d="M5 11h14v3a7 7 0 0 1-14 0zM9 4v4M12 3v5M15 4v4" />,
  clean: <path d="M4 21l6-6M14 3l7 7-7 4-4-4z" />,
  financ: <path d="M4 7h16v12H4zM4 11h16M8 15h3" />,
  "check-in": <path d="M12 20s-7-4.5-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.5-7 10-7 10z" />,
  "mum ": <path d="M12 20s-7-4.5-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.5-7 10-7 10z" />,
};

const CATEGORY_FALLBACK_ICONS: Record<RoutineItemCategory, React.ReactNode> = {
  BODY: <path d="M6 8v8M18 8v8M3 10v4M21 10v4M6 12h12" />,
  MIND: <path d="M4 19V5a2 2 0 0 1 2-2h13v16H6a2 2 0 0 0-2 2z" />,
  WORK: <><rect x="4" y="7" width="16" height="12" rx="2" /><path d="M9 7V5h6v2" /></>,
  HOME: <path d="M4 21l6-6M14 3l7 7-7 4-4-4z" />,
};

export function routineIcon(name: string, category: RoutineItemCategory | null): React.ReactNode {
  const lower = name.toLowerCase();
  for (const [keyword, icon] of Object.entries(NAME_ICONS)) {
    if (lower.includes(keyword)) return icon;
  }
  if (category !== null) return CATEGORY_FALLBACK_ICONS[category];
  return <circle cx="12" cy="12" r="8" />;
}
