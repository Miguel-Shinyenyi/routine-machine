import { backendFetch } from "@/lib/api";
import type { RoutineItem, WateringHoleStatus } from "@/lib/types";

const SOURCE_LABELS: Record<string, string> = {
  HUB: "UMWAYI hub",
  WAKATIME: "WakaTime",
  ACTIVITYWATCH: "ActivityWatch",
  GITHUB: "GitHub",
  RESCUETIME: "RescueTime",
  HEALTH: "Health data",
};

function formatTimestamp(iso: string | null): string {
  if (iso === null) return "Never";
  // Fixed, locale-independent formatting: a server component renders this once, so there is
  // no client-side re-render to drift from it, but a fixed format keeps it that way on purpose.
  return iso.slice(0, 16).replace("T", " ") + " UTC";
}

export default async function WateringHolesPage() {
  const [statuses, routineItems] = await Promise.all([
    backendFetch<WateringHoleStatus[]>("/api/watering-holes"),
    backendFetch<RoutineItem[]>("/api/routine-items"),
  ]);

  const automaticItems = routineItems.filter((item) => item.source !== "MANUAL");

  return (
    <div className="flex flex-col gap-8">
      <h1 className="page-title">The watering holes.</h1>

      <section className="card p-4">
        <h2 className="section-title mb-3">Sources</h2>
        <div className="flex flex-col gap-2">
          {statuses.map((status) => (
            <div
              key={status.source}
              className="tile flex flex-col gap-1 p-3 sm:flex-row sm:items-center sm:justify-between"
            >
              <div className="flex flex-col gap-1">
                <div className="flex items-center gap-2">
                  <span className="text-[15px] font-medium">
                    {SOURCE_LABELS[status.source] ?? status.source}
                  </span>
                  <span className={`chip ${status.enabled ? "chip-tended" : "chip-private"}`}>
                    {status.enabled ? "On" : "Off"}
                  </span>
                </div>
                <p className="text-[13px] text-muted">{status.description}</p>
              </div>
              <span className="text-[13px] text-muted sm:text-right">
                Last activity: {formatTimestamp(status.lastActivityAt)}
              </span>
            </div>
          ))}
        </div>
      </section>

      <section className="card p-4">
        <h2 className="section-title mb-3">How each activity is counted</h2>
        {automaticItems.length === 0 ? (
          <p className="text-[13px] text-muted">
            No routine item is linked to an automatic source yet. Link one through Flock to see
            it listed here.
          </p>
        ) : (
          <table className="w-full text-left text-[14px]">
            <thead>
              <tr className="text-[13px] text-muted">
                <th className="py-1 pr-4 font-normal">Routine</th>
                <th className="py-1 pr-4 font-normal">Source</th>
                <th className="py-1 font-normal">Count rule</th>
              </tr>
            </thead>
            <tbody>
              {automaticItems.map((item) => (
                <tr key={item.id} className="border-t hairline">
                  <td className="py-2 pr-4">{item.name}</td>
                  <td className="py-2 pr-4">{SOURCE_LABELS[item.source] ?? item.source}</td>
                  <td className="py-2 text-muted">{item.countRule ?? "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </div>
  );
}
