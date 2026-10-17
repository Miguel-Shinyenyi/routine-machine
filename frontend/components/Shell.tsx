import Link from "next/link";

const LINKS = [
  { href: "/today", label: "Today" },
  { href: "/path", label: "Path" },
  { href: "/flock", label: "Flock" },
  { href: "/tally", label: "Tally" },
  { href: "/watering-holes", label: "Watering holes" },
];

export function Shell({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-full flex-col md:flex-row">
      <aside className="glass sticky top-0 hidden h-screen w-[220px] shrink-0 flex-col gap-1 border-r hairline px-4 py-6 md:flex">
        <span className="mb-6 px-2 text-[15px] font-semibold">Routine Machine</span>
        {LINKS.map((link) => (
          <Link
            key={link.href}
            href={link.href}
            className="rounded-[12px] px-3 py-2 text-[15px] text-ink hover:bg-fill"
          >
            {link.label}
          </Link>
        ))}
      </aside>

      <main className="mx-auto w-full max-w-[1100px] flex-1 px-5 pb-28 pt-6 md:pb-10">
        {children}
      </main>

      <nav className="glass fixed inset-x-4 bottom-4 flex items-center justify-around rounded-full border hairline py-2 shadow-none md:hidden">
        {LINKS.map((link) => (
          <Link
            key={link.href}
            href={link.href}
            className="min-w-[44px] px-2 py-1 text-center text-[13px] text-ink"
          >
            {link.label}
          </Link>
        ))}
      </nav>
    </div>
  );
}
