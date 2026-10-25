import { notFound } from "next/navigation";
import { measurementSourceLabel } from "@/lib/sourceLabels";
import { fetchServices, fetchSync, formatUtc, serviceTone, syncTone, type Tone } from "@/lib/watchtower";

// Owner-only (docs/observability.md, part 5). No redirect to /login and no link in the sidebar: to anyone but the
// owner this page does not exist, so a signed-out visitor and a signed-in stranger both get the plain 404 page.
// The backend decides who the owner is; this page only passes its answer on.

const TONE_CLASS: Record<Tone, string> = {
  good: "chip-tended",
  bad: "chip-tint",
  neutral: "",
};

const TONE_WORD: Record<Tone, string> = {
  good: "Up",
  bad: "Down",
  neutral: "Not configured",
};

// "Failures in a row" is null for a source that has not run since the backend started, which is not the same as zero.
function failuresInARow(value: number | null): string {
  return value === null ? "—" : String(value);
}

export default async function WatchtowerPage() {
  const [services, sync] = await Promise.all([fetchServices(), fetchSync()]);
  if (services === null || sync === null) {
    notFound();
  }

  return (
    <div className="flex flex-col gap-8">
      <div>
        <p className="text-[15px] font-semibold text-tint-text">Watchtower</p>
        <h1 className="page-title">Is everything running.</h1>
        <p className="mt-2 max-w-[62ch] text-[17px] text-muted">
          Only you can see this page. Health, metrics, logs and sync status will sit here.
        </p>
      </div>

      <section aria-labelledby="services">
        <h2 id="services" className="mb-2.5 px-1 text-[17px] font-semibold">
          Services
        </h2>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {services.map((service) => {
            const tone = serviceTone(service.status);
            return (
              <div key={service.name} className="card flex flex-col gap-2 p-4">
                <h3 className="text-[16px] font-semibold">{service.name}</h3>
                <span className={`chip self-start ${TONE_CLASS[tone]}`}>{TONE_WORD[tone]}</span>
              </div>
            );
          })}
        </div>
      </section>

      <section aria-labelledby="sync">
        <h2 id="sync" className="mb-2.5 px-1 text-[17px] font-semibold">
          Sync sources
        </h2>
        {!sync.available && (
          <p className="mb-2.5 px-1 text-[14px] text-muted">
            Prometheus is not answering, so whether each source is running is unknown. The switches and the newest data
            below still come from the database.
          </p>
        )}
        <div className="overflow-x-auto rounded-[14px] bg-surface">
          <table className="w-full min-w-[720px] text-left text-[14px]">
            <thead>
              <tr className="text-[12px] font-medium text-muted" style={{ background: "#fafafc" }}>
                <th className="h-[34px] px-4 font-medium">Source</th>
                <th className="h-[34px] px-4 font-medium">Result</th>
                <th className="h-[34px] px-4 font-medium">Failures in a row</th>
                <th className="h-[34px] px-4 font-medium">Last success</th>
                <th className="h-[34px] px-4 font-medium">Newest data</th>
                <th className="h-[34px] px-4 font-medium">Last 24 hours</th>
              </tr>
            </thead>
            <tbody>
              {sync.sources.map((source) => (
                <tr key={source.source} className="border-t hairline">
                  <td className="px-4 py-2">{measurementSourceLabel(source.source)}</td>
                  <td className="px-4 py-2">
                    <span className={`chip ${TONE_CLASS[syncTone(source.result)]}`}>{source.result}</span>
                  </td>
                  <td className="px-4 py-2">{failuresInARow(source.consecutiveFailures)}</td>
                  <td className="px-4 py-2 text-muted">{formatUtc(source.lastSuccessAt)}</td>
                  <td className="px-4 py-2 text-muted">{formatUtc(source.lastActivityAt)}</td>
                  <td className="px-4 py-2 text-muted">
                    {source.successes24h} ok, {source.failures24h} failed
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <p className="mt-2 px-1 text-[13px] text-muted">
          Failures in a row start again at zero when the backend restarts. Last success and the 24-hour counts do not.
          Newest data is when a source last wrote something, not when it last ran.
        </p>
      </section>
    </div>
  );
}
