import Link from "next/link";
import { backendFetch } from "@/lib/api";
import type { Hillside } from "@/lib/types";

const FIELDS: { value: string | null; label: string }[] = [
  { value: null, label: "All fields" },
  { value: "READING", label: "Reading" },
  { value: "MOVEMENT", label: "Movement" },
  { value: "DEEP_WORK", label: "Deep work" },
  { value: "LEARNING", label: "Learning" },
  { value: "SLEEP", label: "Sleep" },
  { value: "WRITING", label: "Writing" },
];

function buildQuery(field: string | null, windowDays: number, asUserId: string | undefined): string {
  const params = new URLSearchParams();
  if (field !== null) params.set("field", field);
  params.set("windowDays", String(windowDays));
  if (asUserId) params.set("asUserId", asUserId);
  return params.toString();
}

function buildHref(field: string | null, windowDays: number, asUserId: string | undefined): string {
  return `/hillside?${buildQuery(field, windowDays, asUserId)}`;
}

export default async function HillsidePage(props: PageProps<"/hillside">) {
  const searchParams = await props.searchParams;
  const rawField = searchParams.field;
  const field = typeof rawField === "string" ? rawField : null;
  const rawWindow = searchParams.windowDays;
  const windowDays = rawWindow === "28" ? 28 : 7;
  const rawAsUserId = searchParams.asUserId;
  const asUserId = typeof rawAsUserId === "string" ? rawAsUserId : undefined;

  const hillside = await backendFetch<Hillside>(`/api/hillside?${buildQuery(field, windowDays, asUserId)}`);
  const maxHistogram = Math.max(1, ...hillside.histogram);

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-end justify-between gap-5">
        <div className="min-w-0 flex-1 basis-[420px]">
          <p className="text-[15px] font-semibold text-tint-text">The hillside &middot; anonymous</p>
          <h1 className="page-title">Other flocks, walking the same way.</h1>
          <p className="mt-2 max-w-[62ch] text-[17px] text-muted">
            Ranked by how many days people show up, never by who did the most. A 20-minute walk
            and a marathon count as the same day.
          </p>
        </div>
        <div className="inline-flex gap-0.5 rounded-[9px] p-0.5" style={{ background: "var(--fill)" }}>
          {[7, 28].map((w) => (
            <Link
              key={w}
              href={buildHref(field, w, asUserId)}
              aria-pressed={windowDays === w}
              className="rounded-[7px] px-3.5 py-1.5 text-[13px] font-medium"
              style={windowDays === w ? { background: "var(--surface)", fontWeight: 600 } : undefined}
            >
              Last {w} days
            </Link>
          ))}
        </div>
      </div>

      <div className="inline-flex max-w-full flex-wrap gap-0.5 self-start rounded-[9px] p-0.5" style={{ background: "var(--fill)" }}>
        {FIELDS.map((f) => (
          <Link
            key={f.label}
            href={buildHref(f.value, windowDays, asUserId)}
            aria-pressed={field === f.value}
            className="rounded-[7px] px-3.5 py-1.5 text-[13px] font-medium"
            style={field === f.value ? { background: "var(--surface)", fontWeight: 600 } : undefined}
          >
            {f.label}
          </Link>
        ))}
      </div>

      <div className="flex flex-wrap items-stretch gap-5">
        <section className="card flex min-w-0 flex-[1_1_340px] flex-col gap-1.5 p-5">
          <span className="text-[13px] text-muted">
            {FIELDS.find((f) => f.value === field)?.label} &middot; last {windowDays} days
          </span>
          {hillside.band !== null ? (
            <>
              <h2 className="text-[28px] font-semibold tracking-tight">{hillside.band}</h2>
              <p className="text-[15px] text-muted">
                {hillside.yourRank} of {hillside.totalParticipants} on this field.
              </p>
              <p className="mt-1.5 text-[15px]">
                You showed up on {hillside.yourDaysShownUp} of {windowDays} days.
              </p>
            </>
          ) : (
            <>
              <h2 className="text-[20px] font-semibold">Not in this field yet.</h2>
              <p className="text-[15px] text-muted">
                {hillside.totalParticipants} people show up here. Tend a routine in this field
                to see where you&rsquo;d stand.
              </p>
              <p className="mt-1.5 text-[13px] text-muted">
                Demo mode: no login yet, so pass <code>?asUserId=</code> in the URL to see a
                specific person&rsquo;s band.
              </p>
            </>
          )}
        </section>

        <section className="card min-w-0 flex-[2_1_420px] p-5">
          <div className="flex flex-wrap items-baseline justify-between gap-2">
            <h2 className="text-[17px] font-semibold">How the flock spreads out</h2>
            <span className="text-[13px] text-muted">People by days shown up &middot; you in amber</span>
          </div>
          <div
            className="mt-2.5 flex items-end gap-2.5"
            style={{ height: "150px" }}
            role="img"
            aria-label={`Distribution of people by days shown up${hillside.yourDaysShownUp !== null ? `; you are at ${hillside.yourDaysShownUp} days` : ""}`}
          >
            {hillside.histogram.map((count, days) => (
              <div key={days} className="flex min-w-0 flex-1 flex-col items-center gap-1.5">
                <div
                  className="w-full rounded-t-[6px]"
                  style={{
                    height: `${Math.round((count / maxHistogram) * 110)}px`,
                    background: days === hillside.yourDaysShownUp ? "var(--tint)" : "var(--fill)",
                  }}
                />
                <span className="text-[12px] text-muted">{days}</span>
              </div>
            ))}
          </div>
        </section>
      </div>

      <div className="flex flex-wrap items-start gap-5">
        <section className="min-w-0 flex-[999_1_520px]">
          <div className="mb-2 flex flex-wrap items-baseline justify-between gap-2 px-1">
            <h2 className="text-[17px] font-semibold">Walking near you</h2>
            <span className="text-[13px] text-muted">Three ahead, three behind. There is no top-ten page.</span>
          </div>
          {hillside.neighbors.length === 0 ? (
            <p className="px-1 text-[13px] text-muted">No neighbours to show yet.</p>
          ) : (
            <div className="overflow-x-auto rounded-[14px] bg-surface">
              <div style={{ minWidth: "560px" }}>
                {hillside.neighbors.map((row, i) => (
                  <div
                    key={row.pseudonym}
                    className={`flex items-center gap-3 px-4 py-2 ${i === 0 ? "" : "border-t hairline"}`}
                    style={row.isYou ? { background: "var(--tint-fill)" } : undefined}
                  >
                    <span className="min-w-0 flex-1 text-[14px] font-semibold">
                      {row.pseudonym}
                      {row.isYou && " (you)"}
                    </span>
                    <span
                      className="flex gap-1"
                      role="img"
                      aria-label={`${row.daysShownUp} of ${windowDays} days`}
                    >
                      {Array.from({ length: Math.min(7, windowDays) }).map((_, dotIndex) => (
                        <i
                          key={dotIndex}
                          className="h-3 w-3 rounded-full"
                          style={{
                            background:
                              dotIndex < Math.round((row.daysShownUp / windowDays) * Math.min(7, windowDays))
                                ? row.isYou
                                  ? "var(--tint-text)"
                                  : "var(--ink)"
                                : "var(--fill)",
                          }}
                        />
                      ))}
                    </span>
                    <span className="text-[13px] text-muted">
                      {row.daysShownUp} of {windowDays}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          )}
        </section>

        <aside className="card min-w-0 flex-[1_1_300px] p-5" aria-labelledby="rules">
          <h2 id="rules" className="mb-2 text-[17px] font-semibold">
            Why this hillside is calm.
          </h2>
          <div className="flex flex-col gap-2.5 text-[14px]">
            <div className="flex gap-2.5">
              <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" aria-hidden="true" className="mt-0.5 shrink-0 text-muted">
                <path d="M5 5v14M9 5v14M13 5v14M17 5v14" />
              </svg>
              <span>
                <b className="block font-semibold">Days, not volume.</b>
                <span className="text-muted">
                  Each day has a cap. Bingeing a whole book on Sunday counts as one day.
                </span>
              </span>
            </div>
            <div className="flex gap-2.5 border-t hairline pt-2.5">
              <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" aria-hidden="true" className="mt-0.5 shrink-0 text-muted">
                <circle cx="12" cy="9" r="4" />
                <path d="M4 21c1.5-4 4.5-6 8-6s6.5 2 8 6" />
              </svg>
              <span>
                <b className="block font-semibold">No names, no faces.</b>
                <span className="text-muted">
                  Pseudonyms are derived from an id, never a name or email. No followers, no
                  profiles, no messages.
                </span>
              </span>
            </div>
            <div className="flex gap-2.5 border-t hairline pt-2.5">
              <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" aria-hidden="true" className="mt-0.5 shrink-0 text-muted">
                <path d="M12 3s6 7 6 11a6 6 0 0 1-12 0c0-4 6-11 6-11z" />
              </svg>
              <span>
                <b className="block font-semibold">Only what a source can confirm.</b>
                <span className="text-muted">
                  Health data, WakaTime, ActivityWatch, counted reader&rsquo;s notes. Taps never
                  reach the hillside.
                </span>
              </span>
            </div>
            <div className="flex gap-2.5 border-t hairline pt-2.5">
              <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" aria-hidden="true" className="mt-0.5 shrink-0 text-muted">
                <path d="M3 12h18M12 3v18" />
              </svg>
              <span>
                <b className="block font-semibold">You choose the fields.</b>
                <span className="text-muted">Private routines stay out, always.</span>
              </span>
            </div>
          </div>
        </aside>
      </div>
    </div>
  );
}
