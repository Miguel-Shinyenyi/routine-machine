import Link from "next/link";

const LINKS = [
  { href: "/board", label: "Board" },
  { href: "/backlog", label: "Backlog" },
  { href: "/roadmap", label: "Roadmap" },
  { href: "/reports", label: "Reports" },
];

export function NavBar() {
  return (
    <nav className="border-b border-black/10 dark:border-white/15">
      <div className="mx-auto flex max-w-4xl items-center gap-6 px-4 py-3">
        <span className="font-semibold">Routine Machine</span>
        <div className="flex gap-4 text-sm">
          {LINKS.map((link) => (
            <Link key={link.href} href={link.href} className="hover:underline">
              {link.label}
            </Link>
          ))}
        </div>
      </div>
    </nav>
  );
}
