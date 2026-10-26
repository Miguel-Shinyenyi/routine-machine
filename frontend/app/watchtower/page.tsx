import Link from "next/link";
import { notFound } from "next/navigation";
import { WatchtowerChart } from "@/components/charts/WatchtowerChart";
import { parseRange, type Range } from "@/lib/chart";
import {
  fetchLogs,
  formatUtcSeconds,
  levelTone,
  LOG_LEVELS,
  pageHref,
  parseLogFilters,
  safeHref,
  type LevelTone,
} from "@/lib/logs";
import { measurementSourceLabel } from "@/lib/sourceLabels";
import { fetchMetrics, fetchServices, fetchSync, formatUtc, serviceTone, syncTone, type Tone } from "@/lib/watchtower";

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

const LEVEL_CLASS: Record<LevelTone, string> = {
  bad: "chip-tint",
  warn: "chip-tint",
  plain: "",
};

const RANGE_LABELS: Record<Range, string> = { "1h": "1 hour", "6h": "6 hours", "24h": "24 hours" };

export default async function WatchtowerPage({
  searchParams,
}: {
  searchParams: Promise<{ [key: string]: string | string[] | undefined }>;
}) {
  const params = await searchParams;
  const range = parseRange(params.range);
  const { level, requestId, badId } = parseLogFilters({ level: params.level, id: params.id });
  const filters = { level, requestId };
  const [services, sync, metrics, logs] = await Promise.all([
    fetchServices(),
    fetchSync(),
    fetchMetrics(range),
    fetchLogs(range, filters),
  ]);
  if (services === null || sync === null || metrics === null || logs === null) {
    notFound();
  }

  return (
    <div className="flex flex-col gap-8">
      <div>
        <p className="text-[15px] font-semibold text-tint-text">Watchtower</p>
        <h1 className="page-title">Is everything running.</h1>
        <p className="mt-2 max-w-[62ch] text-[17px] text-muted">
          Only you can see this page.
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

      <section aria-labelledby="apidocs">
        <h2 id="apidocs" className="mb-2.5 px-1 text-[17px] font-semibold">
          API documentation
        </h2>
        <p className="px-1 text-[14px] text-muted">
          Both APIs, the backend and the intelligence service, in one read-only viewer. Only you can open it.{" "}
          {/* A plain link on purpose: Next's Link would prefetch it, and it is a route handler, not a page. */}
          <a href="/watchtower/docs" target="_blank" rel="noopener noreferrer" className="text-tint-text">
            Open the API documentation
          </a>
        </p>
      </section>

      <section aria-labelledby="charts">
        <div className="mb-2.5 flex flex-wrap items-center justify-between gap-2 px-1">
          <h2 id="charts" className="text-[17px] font-semibold">
            Charts
          </h2>
          <div role="group" aria-label="Time range" className="flex gap-1.5">
            {(Object.keys(RANGE_LABELS) as Range[]).map((option) => (
              <Link
                key={option}
                href={pageHref(option, filters)}
                aria-pressed={option === range}
                className={`chip ${option === range ? "chip-tended" : ""}`}
              >
                {RANGE_LABELS[option]}
              </Link>
            ))}
          </div>
        </div>
        {!metrics.available ? (
          <p className="px-1 text-[14px] text-muted">
            Prometheus is not answering, so there are no charts. The services and sync tables still load.
          </p>
        ) : (
          <div className="grid grid-cols-1 items-start gap-4 lg:grid-cols-2">
            {metrics.charts.map((chart) => (
              <WatchtowerChart key={chart.id} chart={chart} />
            ))}
          </div>
        )}
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

      <section aria-labelledby="logs">
        <h2 id="logs" className="mb-2.5 px-1 text-[17px] font-semibold">
          Logs
        </h2>
        <form method="get" action="/watchtower" className="mb-3 flex flex-wrap items-end gap-3 px-1">
          <input type="hidden" name="range" value={range} />
          <label className="flex flex-col gap-1 text-[12px] text-muted">
            Level
            <select name="level" defaultValue={level ?? ""} className="rounded-[10px] border hairline bg-surface px-2 py-1 text-[14px] text-ink">
              <option value="">Any</option>
              {LOG_LEVELS.map((option) => (
                <option key={option} value={option}>
                  {option}
                </option>
              ))}
            </select>
          </label>
          <label className="flex flex-col gap-1 text-[12px] text-muted">
            Request id or trace id
            <input
              name="id"
              defaultValue={requestId ?? ""}
              maxLength={64}
              autoComplete="off"
              className="w-[300px] max-w-full rounded-[10px] border hairline bg-surface px-2 py-1 text-[14px] text-ink"
            />
          </label>
          <button type="submit" className="btn-fill h-[34px] px-4 text-[14px]">
            Filter
          </button>
          {(level !== null || requestId !== null) && (
            <Link href={pageHref(range, { level: null, requestId: null })} className="text-[14px] text-tint-text">
              Clear
            </Link>
          )}
        </form>
        {badId && (
          <p className="mb-2 px-1 text-[14px] text-muted">
            That id was not used: ids have 1 to 64 letters, digits, dots, underscores or hyphens.
          </p>
        )}
        {!logs.available ? (
          <p className="px-1 text-[14px] text-muted">
            Loki is not answering, so there are no logs. The other tables still load.
          </p>
        ) : logs.entries.length === 0 ? (
          <p className="px-1 text-[14px] text-muted">No log lines match in this range.</p>
        ) : (
          <div className="overflow-x-auto rounded-[14px] bg-surface">
            <table className="w-full min-w-[820px] text-left text-[13px]">
              <thead>
                <tr className="text-[12px] font-medium text-muted" style={{ background: "#fafafc" }}>
                  <th className="h-[34px] px-3 font-medium">Time (UTC)</th>
                  <th className="h-[34px] px-3 font-medium">Service</th>
                  <th className="h-[34px] px-3 font-medium">Level</th>
                  <th className="h-[34px] px-3 font-medium">Message</th>
                  <th className="h-[34px] px-3 font-medium">Request</th>
                  <th className="h-[34px] px-3 font-medium">Trace</th>
                </tr>
              </thead>
              <tbody>
                {logs.entries.map((entry, index) => {
                  const traceHref = safeHref(entry.traceUrl);
                  return (
                    <tr key={`${entry.time}-${index}`} className="border-t hairline align-top">
                      <td className="whitespace-nowrap px-3 py-2 text-muted">{formatUtcSeconds(entry.time)}</td>
                      <td className="whitespace-nowrap px-3 py-2">{entry.service}</td>
                      <td className="px-3 py-2">
                        {entry.level !== null && (
                          <span className={`chip ${LEVEL_CLASS[levelTone(entry.level)]}`}>{entry.level}</span>
                        )}
                      </td>
                      <td className="break-words px-3 py-2">{entry.message}</td>
                      <td className="whitespace-nowrap px-3 py-2">
                        {entry.requestId !== null && (
                          <Link
                            href={pageHref(range, { level, requestId: entry.requestId })}
                            title="Show only this request"
                            className="text-tint-text"
                          >
                            {entry.requestId.slice(0, 8)}
                          </Link>
                        )}
                      </td>
                      <td className="whitespace-nowrap px-3 py-2">
                        {traceHref !== null && (
                          <a href={traceHref} target="_blank" rel="noopener noreferrer" className="text-tint-text">
                            Open
                          </a>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
        <p className="mt-2 px-1 text-[13px] text-muted">
          The newest 100 lines of the backend and the intelligence service in the range above. Lines carry a trace link
          only while tracing is on.
        </p>
      </section>
    </div>
  );
}
