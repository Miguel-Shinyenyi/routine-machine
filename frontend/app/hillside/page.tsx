import Link from "next/link";
import { authenticatedBackendFetch, backendFetch } from "@/lib/api";
import { setHillsideOptIn } from "@/lib/actions";
import { getSessionToken } from "@/lib/auth/session";
import type { CurrentUser, Hillside } from "@/lib/types";

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

  const sessionToken = await getSessionToken();
  const me = sessionToken
    ? await authenticatedBackendFetch<CurrentUser>("/api/users/me", sessionToken).catch(() => null)
    : null;

  // A real session replaces the old demo-mode ?asUserId= param entirely once one exists —
  // nobody should be able to see another person's band just by editing a query string. The
  // param still works signed out, so the page stays checkable without an account.
  const rawAsUserId = searchParams.asUserId;
  const demoAsUserId = typeof rawAsUserId === "string" ? rawAsUserId : undefined;
  const asUserId = me ? String(me.id) : demoAsUserId;

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
          {me && (
            <form action={setHillsideOptIn} className="mt-2">
              <input type="hidden" name="optIn" value={me.hillsideOptIn ? "false" : "true"} />
              <button type="submit" className="text-[13px] font-medium text-tint-text hover:underline">
                {me.hillsideOptIn ? "Leave the hillside" : "Join the hillside"}
              </button>
            </form>
          )}
        </div>
        <div className="inline-flex gap-0.5 rounded-[9px] p-0.5" style={{ background: "var(--fill)" }}>
          {[7, 28].map((w) => (
            <Link
              key={w}
              href={buildHref(field, w, demoAsUserId)}
              aria-pressed={windowDays === w}
              className="rounded-[7px] px-3.5 py-1.5 text-[13px] font-medium text-ink"
              style={windowDays === w ? { background: "var(--surface)", fontWeight: 600, boxShadow: "0 3px 8px rgba(0,0,0,.12), 0 3px 1px rgba(0,0,0,.04)" } : undefined}
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
            href={buildHref(f.value, windowDays, demoAsUserId)}
            aria-pressed={field === f.value}
            className="rounded-[7px] px-3.5 py-1.5 text-[13px] font-medium text-ink"
            style={field === f.value ? { background: "var(--surface)", fontWeight: 600, boxShadow: "0 3px 8px rgba(0,0,0,.12), 0 3px 1px rgba(0,0,0,.04)" } : undefined}
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
          ) : me && !me.hillsideOptIn ? (
            <>
              <h2 className="text-[20px] font-semibold">You haven&rsquo;t joined the hillside.</h2>
              <p className="text-[15px] text-muted">
                {hillside.totalParticipants} people show up here. Join above to see where
                you&rsquo;d stand &mdash; your own private routines stay out either way.
              </p>
            </>
          ) : me ? (
            <>
              <h2 className="text-[20px] font-semibold">Not in this field yet.</h2>
              <p className="text-[15px] text-muted">
                {hillside.totalParticipants} people show up here. Tend a routine in this field
                to see where you&rsquo;d stand.
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
                <Link href="/login" className="text-tint-text hover:underline">
                  Sign in
                </Link>{" "}
                to see your own band, or pass <code>?asUserId=</code> in the URL to preview a
                specific person&rsquo;s.
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
                  className="w-full"
                  style={{
                    height: `${Math.round((count / maxHistogram) * 110)}px`,
                    borderRadius: "6px 6px 2px 2px",
                    background: days === hillside.yourDaysShownUp ? "var(--tint)" : "#d2d2d7",
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
                {hillside.neighbors.map((row, i) => {
                  // An honest, data-derived stand-in for the reference's own "Ahead by a
                  // day"/"Close behind" captions, which are hand-picked for its sample rows
                  // rather than computed from anything — this compares each neighbour's real
                  // days-shown-up against yours instead of inventing similar-sounding text.
                  const diff = hillside.yourDaysShownUp === null ? null : row.daysShownUp - hillside.yourDaysShownUp;
                  const caption =
                    row.isYou || diff === null
                      ? null
                      : diff === 0
                        ? "Same pace as you"
                        : diff > 0
                          ? `${diff} ${diff === 1 ? "day" : "days"} ahead of you`
                          : `${-diff} ${-diff === 1 ? "day" : "days"} behind you`;
                  return (
                    <div
                      key={row.pseudonym}
                      className={`grid items-center gap-3 px-4 py-2 ${i === 0 ? "" : "border-t hairline"}`}
                      style={{
                        gridTemplateColumns: "44px minmax(150px,1.3fr) minmax(130px,1fr) minmax(140px,1.3fr)",
                        background: row.isYou ? "var(--tint-fill)" : undefined,
                      }}
                    >
                      <span className="text-[15px] font-semibold text-muted" style={{ fontVariantNumeric: "tabular-nums" }}>
                        {row.rank}
                      </span>
                      <span className="min-w-0">
                        <span className="block text-[14px] font-semibold">
                          {row.pseudonym}
                          {row.isYou && " (you)"}
                        </span>
                        {caption && <span className="text-[12px] text-muted">{caption}</span>}
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
                                  : "#e8e8ed",
                            }}
                          />
                        ))}
                      </span>
                      <span className="text-[13px] text-muted">
                        {row.daysShownUp} of {windowDays}
                      </span>
                    </div>
                  );
                })}
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
                <span className="text-muted">
                  Private routines stay out. Leave the hillside any time and your rows go with
                  you.
                </span>
              </span>
            </div>
          </div>
        </aside>
      </div>
    </div>
  );
}
