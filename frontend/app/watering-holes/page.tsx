import { backendFetch } from "@/lib/api";
import { sourceLabel } from "@/lib/sourceLabels";
import type { RoutineItem, WateringHoleStatus } from "@/lib/types";

// WateringHoleStatus.source is a MeasurementSource (push/pull sync status), a different enum
// from RoutineItemSource ("How each activity is counted" below) — same names mostly overlap,
// but "Health data" (a status card) and "Health Connect" (a source chip) are deliberately
// different labels for the same underlying integration, so this stays its own small map
// rather than reusing lib/sourceLabels's RoutineItemSource-keyed one.
const SOURCE_LABELS: Record<string, string> = {
  HUB: "UMWAYI hub",
  WAKATIME: "WakaTime",
  ACTIVITYWATCH: "ActivityWatch",
  GITHUB: "GitHub",
  RESCUETIME: "RescueTime",
  HEALTH: "Health data",
};

// Matching docs-reference/pages/watering-holes.html's coloured source marks, not an arbitrary
// new palette — each source keeps the same colour the reference assigns it.
const SOURCE_COLORS: Record<string, string> = {
  HUB: "#1d1d1f",
  WAKATIME: "#0066cc",
  ACTIVITYWATCH: "#2f5a72",
  GITHUB: "#6e6e73",
  RESCUETIME: "#3a3a3c",
  HEALTH: "#1b7332",
};

const HILLSIDE_FIELD_LABELS: Record<string, string> = {
  READING: "Reading",
  MOVEMENT: "Movement",
  DEEP_WORK: "Deep work",
  LEARNING: "Learning",
  SLEEP: "Sleep",
  WRITING: "Writing",
};

function formatTimestamp(iso: string | null): string {
  if (iso === null) return "Never";
  // Fixed, locale-independent formatting: a server component renders this once, so there is
  // no client-side re-render to drift from it, but a fixed format keeps it that way on purpose.
  return iso.slice(0, 16).replace("T", " ") + " UTC";
}

// Maps a watering hole's own MeasurementSource name onto the matching RoutineItemSource, the
// enum routine items actually carry, so a card can show which categories/fields its own data
// feeds — the reference shows this as a bottom chip ("Journaling, Writing, Reading" for the
// hub), generated here from real routine items rather than written by hand.
const MEASUREMENT_TO_ROUTINE_SOURCE: Record<string, string> = {
  HUB: "HUB",
  WAKATIME: "WAKATIME",
  ACTIVITYWATCH: "ACTIVITYWATCH",
  GITHUB: "GITHUB",
  RESCUETIME: "RESCUETIME",
};

function SourceCard({ status, feedsInto }: { status: WateringHoleStatus; feedsInto: string[] }) {
  const label = SOURCE_LABELS[status.source] ?? status.source;
  return (
    <div className="card flex flex-col gap-2 p-4">
      <div className="flex items-center gap-2.5">
        <span
          aria-hidden="true"
          className="flex h-9 w-9 shrink-0 items-center justify-center rounded-[9px] text-[14px] font-bold text-white"
          style={{ background: SOURCE_COLORS[status.source] ?? "#6e6e73" }}
        >
          {label.charAt(0)}
        </span>
        <h3 className="text-[16px] font-semibold">{label}</h3>
      </div>
      <span className={`chip self-start ${status.enabled ? "chip-tended" : "chip-tint"}`}>
        {status.enabled ? `Connected · checked ${formatTimestamp(status.lastActivityAt)}` : "Off"}
      </span>
      <p className="text-[14px] text-muted">{status.description}</p>
      {feedsInto.length > 0 && <span className="chip self-start">{feedsInto.join(", ")}</span>}
    </div>
  );
}

export default async function WateringHolesPage() {
  const [statuses, routineItems] = await Promise.all([
    backendFetch<WateringHoleStatus[]>("/api/watering-holes"),
    backendFetch<RoutineItem[]>("/api/routine-items"),
  ]);

  const connected = statuses.filter((s) => s.enabled);
  const notConnected = statuses.filter((s) => !s.enabled);
  // Same reasoning as Flock: exclude Phase 8's hillside demo users' own items, this table is
  // this single-user app's own "how my activity gets counted" list, not a multi-user view.
  // Unlike an earlier version, this now includes manually-tapped items too — the reference's
  // own table does (shown with "Tapped" as the main source), not just automatic ones.
  const countedItems = routineItems.filter((item) => item.userId == null);

  function feedsInto(measurementSource: string): string[] {
    const routineSource = MEASUREMENT_TO_ROUTINE_SOURCE[measurementSource];
    const fields = countedItems
      .filter((item) => item.source === routineSource && item.hillsideField !== null)
      .map((item) => HILLSIDE_FIELD_LABELS[item.hillsideField as string] ?? item.hillsideField);
    return Array.from(new Set(fields));
  }

  return (
    <div className="flex flex-col gap-8">
      <div>
        <p className="text-[15px] font-semibold text-tint-text">Watering holes</p>
        <h1 className="page-title">Where the records come from.</h1>
        <p className="mt-2 max-w-[62ch] text-[17px] text-muted">
          The flock drinks from the tools you already use. Whatever a tool can see, you never
          have to type. Whatever it can&rsquo;t, you tap once.
        </p>
      </div>

      <section aria-labelledby="conn">
        <h2 id="conn" className="mb-2.5 px-1 text-[17px] font-semibold">
          Connected and waiting
        </h2>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {/* Reader's notes has no on/off state in /api/watering-holes — it's not an external
              sync to enable, it's this app's own reading-notes feature, always on. */}
          <div className="card flex flex-col gap-2 p-4">
            <div className="flex items-center gap-2.5">
              <span
                aria-hidden="true"
                className="flex h-9 w-9 shrink-0 items-center justify-center rounded-[9px] text-[14px] font-bold text-white"
                style={{ background: "#9a5208" }}
              >
                R
              </span>
              <h3 className="text-[16px] font-semibold">Reader&rsquo;s notes</h3>
            </div>
            <span className="chip chip-tended self-start">Built in</span>
            <p className="text-[14px] text-muted">
              Your notes, read by the Shepherd to place you in the book and check they are yours.
            </p>
            <span className="chip self-start">Reading</span>
          </div>
          {connected.map((status) => (
            <SourceCard key={status.source} status={status} feedsInto={feedsInto(status.source)} />
          ))}
        </div>
      </section>

      {notConnected.length > 0 && (
        <section aria-labelledby="more">
          <h2 id="more" className="mb-2.5 px-1 text-[17px] font-semibold">
            Not connected yet
          </h2>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {notConnected.map((status) => (
              <SourceCard key={status.source} status={status} feedsInto={feedsInto(status.source)} />
            ))}
          </div>
        </section>
      )}

      <section aria-labelledby="how">
        <h2 id="how" className="mb-2.5 px-1 text-[17px] font-semibold">
          How each activity is counted
        </h2>
        {countedItems.length === 0 ? (
          <p className="px-1 text-[13px] text-muted">
            No routine item exists yet. Add one through Flock to see it listed here.
          </p>
        ) : (
          <div className="overflow-x-auto rounded-[14px] bg-surface">
            <table className="w-full min-w-[760px] text-left text-[14px]">
              <thead>
                <tr className="text-[12px] font-medium text-muted" style={{ background: "#fafafc" }}>
                  <th className="h-[34px] px-4 font-medium">Activity</th>
                  <th className="h-[34px] px-4 font-medium">A day counts when</th>
                  <th className="h-[34px] px-4 font-medium">Main source</th>
                  <th className="h-[34px] px-4 font-medium">Backup</th>
                  <th className="h-[34px] px-4 font-medium">Hillside</th>
                </tr>
              </thead>
              <tbody>
                {countedItems.map((item) => (
                  <tr key={item.id} className="border-t hairline">
                    <td className="py-2 px-4">{item.name}</td>
                    <td className="py-2 px-4 text-muted">{item.countRule ?? "—"}</td>
                    <td className="py-2 px-4">{sourceLabel(item.source)}</td>
                    <td className="py-2 px-4 text-muted">&mdash;</td>
                    <td className="py-2 px-4">
                      {item.hillsideField !== null
                        ? HILLSIDE_FIELD_LABELS[item.hillsideField]
                        : item.isPrivate
                          ? "Private"
                          : "—"}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <p className="mt-2 px-1 text-[13px] text-muted">
          Generated from your own routine items, not a fixed list &mdash; link a new one through
          Flock and it appears here.
        </p>
      </section>

      <div className="card p-4">
        <h2 className="text-[17px] font-semibold">Still to work out together.</h2>
        <ul className="mt-2.5 flex flex-col gap-1.5 pl-5 text-[15px]" style={{ listStyleType: "disc" }}>
          <li>Whether to opt into the hillside yet &mdash; the setting exists, but there&rsquo;s no page to turn it on from.</li>
          <li>Whether a tapped routine should ever reach the hillside, or stay private for good.</li>
          <li>Pseudonym rotation: the hillside name you&rsquo;re given today is permanent, not seasonal yet.</li>
          <li>Path doesn&rsquo;t show how long a tended task actually took, only its target duration.</li>
        </ul>
      </div>
    </div>
  );
}
