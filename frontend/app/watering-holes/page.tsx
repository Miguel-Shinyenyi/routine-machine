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

function formatTimestamp(iso: string | null): string {
  if (iso === null) return "Never";
  // Fixed, locale-independent formatting: a server component renders this once, so there is
  // no client-side re-render to drift from it, but a fixed format keeps it that way on purpose.
  return iso.slice(0, 16).replace("T", " ") + " UTC";
}

function SourceCard({ status }: { status: WateringHoleStatus }) {
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
  const automaticItems = routineItems.filter((item) => item.source !== "MANUAL" && item.userId == null);

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

      {connected.length > 0 && (
        <section aria-labelledby="conn">
          <h2 id="conn" className="mb-2.5 px-1 text-[17px] font-semibold">
            Connected and waiting
          </h2>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {connected.map((status) => (
              <SourceCard key={status.source} status={status} />
            ))}
          </div>
        </section>
      )}

      {notConnected.length > 0 && (
        <section aria-labelledby="more">
          <h2 id="more" className="mb-2.5 px-1 text-[17px] font-semibold">
            Not connected yet
          </h2>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {notConnected.map((status) => (
              <SourceCard key={status.source} status={status} />
            ))}
          </div>
        </section>
      )}

      <section aria-labelledby="how">
        <h2 id="how" className="mb-2.5 px-1 text-[17px] font-semibold">
          How each activity is counted
        </h2>
        {automaticItems.length === 0 ? (
          <p className="px-1 text-[13px] text-muted">
            No routine item is linked to an automatic source yet. Link one through Flock to see
            it listed here.
          </p>
        ) : (
          <div className="overflow-x-auto rounded-[14px] bg-surface">
            <table className="w-full text-left text-[14px]">
              <thead>
                <tr className="text-[13px] text-muted">
                  <th className="py-2 px-4 font-normal">Routine</th>
                  <th className="py-2 px-4 font-normal">Source</th>
                  <th className="py-2 px-4 font-normal">Count rule</th>
                </tr>
              </thead>
              <tbody>
                {automaticItems.map((item) => (
                  <tr key={item.id} className="border-t hairline">
                    <td className="py-2 px-4">{item.name}</td>
                    <td className="py-2 px-4">{sourceLabel(item.source)}</td>
                    <td className="py-2 px-4 text-muted">{item.countRule ?? "—"}</td>
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
    </div>
  );
}
