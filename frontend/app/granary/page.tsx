import { redirect } from "next/navigation";
import { backendFetch } from "@/lib/api";
import { createPause, decidePause, reflectPause, setSavingTargetAmount, storeSaving } from "@/lib/actions";
import { getSessionUserId } from "@/lib/auth/session";
import { MpesaImportForm } from "@/components/MpesaImportForm";
import type { GranaryMonthSummary, MpesaCategory, PauseDecision } from "@/lib/types";

const MPESA_CATEGORY_LABELS: Record<MpesaCategory, string> = {
  FOOD: "Food",
  TRANSPORT: "Transport",
  FAMILY_SUPPORT: "Family support",
  DATA_AND_AIRTIME: "Data and airtime",
  LEARNING: "Learning",
  BETTING: "Betting",
  OVERDRAFT_FEES: "Overdraft fees",
  OTHER: "Other",
};

const HEARD_FROM_OPTIONS = ["WhatsApp group", "X / TikTok", "A friend", "An advert"];

function formatAmount(amount: number, currency: string | null): string {
  return `${currency ?? "KSh"} ${amount.toLocaleString()}`;
}

function formatWhen(iso: string): string {
  return new Date(iso).toLocaleDateString("en-GB", { day: "numeric", month: "short" });
}

const OUTCOME_LABELS: Record<string, string> = {
  DECLINED: "Waited, declined",
  PROCEEDED: "Decided, proceeded",
};

export default async function GranaryPage() {
  const userId = await getSessionUserId();
  if (userId === null) {
    redirect("/login");
  }

  const [summary, pauses, mpesaCategories] = await Promise.all([
    backendFetch<GranaryMonthSummary>(`/api/granary/summary?userId=${userId}`),
    backendFetch<PauseDecision[]>(`/api/granary/pauses?userId=${userId}`),
    backendFetch<Partial<Record<MpesaCategory, number>>>(`/api/granary/mpesa-categories?userId=${userId}`).catch(
      () => ({}),
    ),
  ]);
  const mpesaCategoryEntries = Object.entries(mpesaCategories) as [MpesaCategory, number][];

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-end justify-between gap-5">
        <div className="min-w-0 flex-1 basis-[420px]">
          <p className="text-[15px] font-semibold text-tint-text">The granary</p>
          <h1 className="page-title">Store a little. Decide slowly.</h1>
          <p className="mt-2 max-w-[62ch] text-[17px] text-muted">
            Hype costs money as well as time. The granary keeps your small savings visible and
            puts a pause between you and the next big promise.
          </p>
        </div>
        <div className="flex flex-wrap items-start gap-2">
          <MpesaImportForm />
          {summary.savingTargetAmount !== null && (
            <form action={storeSaving}>
              <button type="submit" className="btn-fill rounded-full px-5 py-2.5 text-[15px] font-semibold">
                Store {formatAmount(summary.savingTargetAmount, summary.currency)} today
              </button>
            </form>
          )}
        </div>
      </div>

      {summary.savingTargetAmount === null && (
        <section className="card flex flex-col gap-2 p-5">
          <h2 className="text-[17px] font-semibold">Set a daily amount.</h2>
          <p className="text-[14px] text-muted">
            Pick a small amount you can set aside most days. You can change it any time.
          </p>
          <form action={setSavingTargetAmount} className="flex items-center gap-2">
            <input type="number" name="amount" min={1} placeholder="50" className="field w-[120px]" required />
            <button type="submit" className="btn-fill rounded-full px-4 py-2 text-[14px] font-semibold">
              Set amount
            </button>
          </form>
        </section>
      )}

      <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
        <div className="card flex flex-col gap-1 p-4">
          <span className="text-[13px] text-muted">Stored this month</span>
          <span className="text-[22px] font-semibold">
            {formatAmount(summary.storedThisMonth, summary.currency)}{" "}
            <small className="text-[14px] font-normal text-muted">of {formatAmount(summary.targetThisMonth, summary.currency)}</small>
          </span>
          <div className="mt-1 h-1.5 overflow-hidden rounded-[4px]" style={{ background: "#E8E8ED" }}>
            <div
              className="h-full rounded-[4px]"
              style={{
                width: `${summary.targetThisMonth === 0 ? 0 : Math.min(100, Math.round((summary.storedThisMonth / summary.targetThisMonth) * 100))}%`,
                background: "var(--tint)",
              }}
            />
          </div>
        </div>
        <div className="card flex flex-col gap-1 p-4">
          <span className="text-[13px] text-muted">Days you stored something</span>
          <span className="text-[22px] font-semibold">
            {summary.daysStored} <small className="text-[14px] font-normal text-muted">of {summary.elapsedDays}</small>
          </span>
          <span className="text-[12px] text-muted">Missing a day takes nothing out</span>
        </div>
        <div className="card flex flex-col gap-1 p-4">
          <span className="text-[13px] text-muted">Pauses kept</span>
          <span className="text-[22px] font-semibold">{pauses.filter((p) => p.outcome === "DECLINED").length}</span>
          <span className="text-[12px] text-muted">Decisions you slept on before paying</span>
        </div>
        <div className="card flex flex-col gap-1 p-4">
          <span className="text-[13px] text-muted">Stored since you started</span>
          <span className="text-[22px] font-semibold">{formatAmount(summary.totalStoredEver, summary.currency)}</span>
          <span className="text-[12px] text-muted">In your own money market fund, not here</span>
        </div>
      </div>

      <section className="card flex flex-col gap-3 p-5" aria-labelledby="jar">
        <div className="flex flex-wrap items-baseline justify-between gap-2">
          <h2 id="jar" className="text-[17px] font-semibold">
            One grain a day.
          </h2>
          <span className="text-[14px] text-muted">
            Each mark is a day you put something aside. Dashed: you didn&rsquo;t, and that&rsquo;s fine.
          </span>
        </div>
        <div
          className="flex flex-wrap gap-1"
          role="img"
          aria-label={`${summary.daysStored} of ${summary.elapsedDays} days with a saving this month`}
        >
          {summary.jar.map((stored, i) => (
            <i
              key={i}
              className="h-[22px] w-[14px] rounded-[4px]"
              style={stored ? { background: "var(--tint)" } : { background: "transparent", border: "1.5px dashed #AEAEB2" }}
            />
          ))}
        </div>
      </section>

      <div className="flex flex-wrap items-start gap-6">
        <section className="flex min-w-0 flex-[999_1_560px] flex-col gap-3" aria-labelledby="pause">
          <div className="flex flex-wrap items-baseline justify-between gap-2">
            <h2 id="pause" className="text-[17px] font-semibold">
              The pause.
            </h2>
            <span className="text-[14px] text-muted">Write it down, wait a day, look back in a month.</span>
          </div>
          <form action={createPause} className="card grid grid-cols-1 gap-3 p-5 sm:grid-cols-2">
            <label className="flex flex-col gap-1.5 text-[13px] font-semibold">
              What is it?
              <input name="what" required className="field" placeholder="A token presale a friend shared" />
            </label>
            <label className="flex flex-col gap-1.5 text-[13px] font-semibold">
              How much?
              <input name="amount" className="field" placeholder="KSh 5,000" />
            </label>
            <label className="flex flex-col gap-1.5 text-[13px] font-semibold sm:col-span-2">
              Why now?
              <input name="why" className="field" placeholder="Everyone in the group says it will 10x" />
            </label>
            <div className="flex flex-col gap-1.5 text-[13px] font-semibold sm:col-span-2">
              Where did you hear it?
              <div className="pick" role="group" aria-label="Where you heard it">
                {HEARD_FROM_OPTIONS.map((option) => (
                  <label key={option} className="font-normal">
                    <input type="radio" name="heardFrom" value={option} />
                    {option}
                  </label>
                ))}
              </div>
            </div>
            <div className="flex flex-wrap items-center gap-3 sm:col-span-2">
              <button type="submit" className="btn-fill rounded-full px-4 py-2 text-[14px] font-semibold">
                Wait 24 hours
              </button>
              <button
                type="submit"
                className="rounded-full px-4 py-2 text-[14px] font-semibold"
                style={{ background: "var(--fill)" }}
              >
                I&rsquo;ve already decided
              </button>
              <span className="text-[13px] text-muted">
                The Shepherd will ask you again tomorrow, and once more in 30 days.
              </span>
            </div>
          </form>

          {pauses.length > 0 && (
            <div className="overflow-x-auto rounded-[14px] bg-surface">
              <table className="w-full text-[14px]" style={{ minWidth: "640px" }}>
                <thead>
                  <tr className="text-left text-[12px] text-muted" style={{ background: "#fafafc" }}>
                    <th className="px-4 py-2 font-medium">Decision</th>
                    <th className="px-4 py-2 font-medium">Amount</th>
                    <th className="px-4 py-2 font-medium">What you did</th>
                    <th className="px-4 py-2 font-medium">Looking back</th>
                  </tr>
                </thead>
                <tbody>
                  {pauses.map((pause, i) => (
                    <tr key={pause.id} className={i === 0 ? "" : "border-t hairline"}>
                      <td className="px-4 py-2">
                        <strong className="block font-semibold">{pause.what}</strong>
                        {pause.heardFrom && <span className="text-[12px] text-muted">{pause.heardFrom}</span>}
                      </td>
                      <td className="px-4 py-2">{pause.amount ?? "—"}</td>
                      <td className="px-4 py-2">
                        {pause.outcome ? (
                          <span className="chip chip-tended">{OUTCOME_LABELS[pause.outcome]}</span>
                        ) : (
                          // Decide at any time, not only once the 24-hour mark has passed —
                          // "I've already decided" (the create form's other button) needs
                          // somewhere to land immediately, and nothing stops an earlier, real
                          // decision either.
                          <div className="flex flex-wrap items-center gap-2">
                            <form action={decidePause}>
                              <input type="hidden" name="pauseId" value={pause.id} />
                              <input type="hidden" name="outcome" value="DECLINED" />
                              <button type="submit" className="chip chip-tended">
                                Decline
                              </button>
                            </form>
                            <form action={decidePause}>
                              <input type="hidden" name="pauseId" value={pause.id} />
                              <input type="hidden" name="outcome" value="PROCEEDED" />
                              <button type="submit" className="chip">
                                Proceed
                              </button>
                            </form>
                            {!pause.dueForTwentyFourHourFollowUp && (
                              <span className="text-[12px] text-muted">Waiting &middot; {formatWhen(pause.followUp24hAt)}</span>
                            )}
                          </div>
                        )}
                      </td>
                      <td className="px-4 py-2 text-muted">
                        {pause.reflectionThirtyDay ? (
                          pause.reflectionThirtyDay
                        ) : pause.dueForThirtyDayFollowUp ? (
                          <form action={reflectPause} className="flex flex-wrap items-center gap-2">
                            <input type="hidden" name="pauseId" value={pause.id} />
                            <input name="reflection" placeholder="30 days on…" className="field w-[160px]" required />
                            <button type="submit" className="text-[13px] font-medium text-tint-text hover:underline">
                              Save
                            </button>
                          </form>
                        ) : (
                          `Review on ${formatWhen(pause.followUp30dAt)}`
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>

        <aside className="flex min-w-0 flex-[1_1_300px] flex-col gap-4">
          {mpesaCategoryEntries.length > 0 && (
            <section className="card flex flex-col gap-1 p-5">
              <h2 className="text-[17px] font-semibold">Where it went</h2>
              <p className="text-[13px] text-muted">
                From your M-Pesa statement. Only category totals are kept; the statement is
                deleted after import.
              </p>
              <div className="mt-2 flex flex-col gap-2">
                {mpesaCategoryEntries
                  .sort(([, a], [, b]) => b - a)
                  .map(([category, amount]) => {
                    const max = Math.max(...mpesaCategoryEntries.map(([, v]) => v), 1);
                    return (
                      <div key={category} className="grid grid-cols-[1fr_1.4fr_auto] items-center gap-3 text-[14px]">
                        <span>{MPESA_CATEGORY_LABELS[category]}</span>
                        <span className="h-[10px] overflow-hidden rounded-[5px]" style={{ background: "#E8E8ED" }}>
                          <span
                            className="block h-full rounded-[5px]"
                            style={{ width: `${Math.round((amount / max) * 100)}%`, background: "var(--ink)" }}
                          />
                        </span>
                        <span className="text-right">{formatAmount(amount, summary.currency)}</span>
                      </div>
                    );
                  })}
              </div>
            </section>
          )}
          <section className="card flex flex-col gap-2 p-5">
            <h2 className="text-[17px] font-semibold">Where the granary drinks from</h2>
            <ul className="flex flex-col gap-1.5 text-[14px]">
              <li>
                <strong className="font-semibold">M-Pesa statement</strong>{" "}
                <span className="text-muted">&middot; the PDF from your M-Pesa app, imported monthly</span>
              </li>
              <li>
                <strong className="font-semibold">Your money market fund</strong>{" "}
                <span className="text-muted">&middot; balance typed in or read from its statement</span>
              </li>
              <li>
                <strong className="font-semibold">Bank statement</strong> <span className="text-muted">&middot; CSV export</span>
              </li>
              <li>
                <strong className="font-semibold">A tap</strong> <span className="text-muted">&middot; &ldquo;I put {summary.currency ?? "KSh"} 50 aside&rdquo;</span>
              </li>
            </ul>
          </section>
          <section className="card p-5">
            <span className="chip" style={{ background: "var(--fill)" }}>
              Private
            </span>
            <p className="mt-2 text-[14px] text-muted">
              Money never reaches the hillside or your programme. Routine Machine never holds
              or moves your money and does not give investment advice.
            </p>
          </section>
        </aside>
      </div>
    </div>
  );
}
