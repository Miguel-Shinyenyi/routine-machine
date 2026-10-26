import Link from "next/link";
import { backendFetch } from "@/lib/api";
import { requireSessionUserId } from "@/lib/auth/session";
import { getUserToday } from "@/lib/auth/userToday";
import { formatWeekRange } from "@/lib/formatDate";
import { addDays, mondayOf, weekDates } from "@/lib/weekDates";
import type { ScheduleTemplate, Task } from "@/lib/types";

const DAYS = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"];
const DAY_ABBREV: Record<string, string> = {
  MONDAY: "Mon",
  TUESDAY: "Tue",
  WEDNESDAY: "Wed",
  THURSDAY: "Thu",
  FRIDAY: "Fri",
  SATURDAY: "Sat",
  SUNDAY: "Sun",
};
const STILL_WATERS = new Set(["SATURDAY", "SUNDAY"]);

function dayOfMonth(isoDate: string): number {
  return Number(isoDate.slice(8, 10));
}

type DotStatus = "ok" | "miss" | "now" | "ahead";

function statusDot(status: DotStatus) {
  const style =
    status === "ok"
      ? { background: "var(--tended-ink)", borderColor: "var(--tended-ink)" }
      : status === "now"
        ? { background: "var(--tint-fill)", borderColor: "var(--tint)" }
        : status === "miss"
          ? { borderColor: "var(--tint-text)", borderStyle: "dashed" as const }
          : { borderColor: "#c7c7cc" };
  return <i className="mt-0.5 h-4 w-4 shrink-0 rounded-full border-[1.8px]" style={style} aria-hidden="true" />;
}

export default async function PathPage(props: PageProps<"/path">) {
  await requireSessionUserId();
  const searchParams = await props.searchParams;
  const rawMonday = searchParams.monday;
  const today = await getUserToday();
  const monday = typeof rawMonday === "string" ? mondayOf(rawMonday) : mondayOf(today);
  const dates = weekDates(monday);

  const [templates, ...tasksByDay] = await Promise.all([
    backendFetch<ScheduleTemplate[]>("/api/schedule-templates"),
    ...dates.map((date) => backendFetch<Task[]>(`/api/tasks?date=${date}`)),
  ]);
  const durationsNotSet = templates.some((t) => !t.targetDurationMinutes || t.targetDurationMinutes <= 0);

  // "This week so far": only days that have already happened count toward tended/wandered —
  // a day that hasn't arrived yet hasn't wandered, it's just ahead (see statusDot's "ahead").
  let tended = 0;
  let wandered = 0;
  let daysShownUp = 0;
  let daysElapsed = 0;
  for (let i = 0; i < DAYS.length; i++) {
    const date = dates[i];
    if (date > today) {
      continue;
    }
    daysElapsed++;
    const dayTemplates = templates.filter((t) => t.dayOfWeek === DAYS[i]);
    const dayTasks = tasksByDay[i];
    let tendedToday = 0;
    for (const template of dayTemplates) {
      const matchingTask = dayTasks.find((t) => t.scheduleTemplateId === template.id);
      if (matchingTask?.status === "DONE") {
        tended++;
        tendedToday++;
      } else if (date < today) {
        wandered++;
      }
    }
    if (tendedToday > 0) {
      daysShownUp++;
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-end justify-between gap-5">
        <div className="min-w-0 flex-1 basis-[420px]">
          <p className="text-[15px] font-semibold text-tint-text">{formatWeekRange(monday)}</p>
          <h1 className="page-title">The path.</h1>
          <p className="mt-2 max-w-[62ch] text-[17px] text-muted">
            The same walk every week, on purpose. Order matters more than the clock. The
            Shepherd will not move it until weeks of records say it should.
          </p>
        </div>
        <div className="inline-flex gap-0.5 rounded-[9px] p-0.5" style={{ background: "var(--fill)" }}>
          <Link href={`/path?monday=${addDays(monday, -7)}`} className="rounded-[7px] px-3.5 py-1.5 text-[13px] font-medium text-ink">
            Last week
          </Link>
          <Link
            href={`/path?monday=${mondayOf(today)}`}
            aria-pressed={monday === mondayOf(today)}
            className="rounded-[7px] px-3.5 py-1.5 text-[13px] font-medium text-ink"
            style={monday === mondayOf(today) ? { background: "var(--surface)", fontWeight: 600, boxShadow: "0 3px 8px rgba(0,0,0,.12), 0 3px 1px rgba(0,0,0,.04)" } : undefined}
          >
            This week
          </Link>
          <Link href={`/path?monday=${addDays(monday, 7)}`} className="rounded-[7px] px-3.5 py-1.5 text-[13px] font-medium text-ink">
            Next week
          </Link>
        </div>
      </div>

      {templates.length === 0 && (
        <p className="card p-4 text-[15px]">
          Your path is empty. <Link href="/flock#schedule-templates" className="text-tint-text hover:underline">Put a routine on it in the Flock</Link>{" "}
          and it appears here, day by day.
        </p>
      )}

      <div className="flex flex-wrap items-center gap-4 text-[13px] text-muted">
        <span className="flex items-center gap-1.5">{statusDot("ok")}Tended</span>
        <span className="flex items-center gap-1.5">{statusDot("now")}Today, still open</span>
        <span className="flex items-center gap-1.5">{statusDot("miss")}Wandered, no penalty</span>
        <span className="flex items-center gap-1.5">{statusDot("ahead")}Ahead</span>
      </div>

      <div className="overflow-x-auto pb-1">
        <div className="grid gap-2.5" style={{ gridTemplateColumns: "repeat(7, minmax(0, 1fr))", minWidth: "980px" }}>
          {DAYS.map((day, i) => {
            const date = dates[i];
            const isToday = date === today;
            const isPast = date < today;
            const stillWaters = STILL_WATERS.has(day);
            const dayTemplates = templates
              .filter((t) => t.dayOfWeek === day)
              .sort((a, b) => a.sortOrder - b.sortOrder);
            const dayTasks = tasksByDay[i];

            return (
              <section
                key={day}
                className="card flex flex-col gap-2 p-3"
                style={{
                  background: stillWaters ? "var(--private-fill)" : "var(--surface)",
                  outline: isToday ? "2px solid var(--tint)" : undefined,
                  outlineOffset: isToday ? "-2px" : undefined,
                }}
                aria-label={day[0] + day.slice(1).toLowerCase()}
              >
                <div className="flex items-baseline justify-between px-0.5 pb-1 text-[13px] text-muted">
                  <strong className="text-[15px] font-semibold text-ink">{DAY_ABBREV[day]}</strong>
                  <span>{dayOfMonth(date)}</span>
                </div>
                {stillWaters && (
                  <p className="px-0.5 -mt-1 mb-0.5 text-[12px] font-semibold" style={{ color: "var(--private-ink)" }}>
                    Still waters
                  </p>
                )}
                {dayTemplates.length === 0 && <p className="text-[13px] text-muted">Nothing scheduled</p>}
                {dayTemplates.map((template) => {
                  const matchingTask = dayTasks.find((t) => t.scheduleTemplateId === template.id);
                  const done = matchingTask?.status === "DONE";
                  const status: DotStatus = done ? "ok" : isToday ? "now" : isPast ? "miss" : "ahead";
                  return (
                    <div key={template.id} className="tile flex items-start gap-2 p-2 text-[13px]">
                      {statusDot(status)}
                      <div className="min-w-0">
                        <b className="block font-semibold">{template.label}</b>
                        {template.targetDurationMinutes > 0 && (
                          <span className="text-[12px] text-muted">{template.targetDurationMinutes} min</span>
                        )}
                      </div>
                    </div>
                  );
                })}
              </section>
            );
          })}
        </div>
      </div>

      <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
        <div className="card p-4">
          <h2 className="section-title mb-2">This week so far.</h2>
          <p className="text-[13px] text-muted">
            {daysElapsed === 0
              ? "The week just started."
              : `${tended} tended, ${wandered} wandered, ${daysShownUp} of ${daysElapsed} ${daysElapsed === 1 ? "day" : "days"} shown up. The wandered ones carry no penalty. They show you where the path is steep.`}
          </p>
        </div>
        {durationsNotSet && (
          <div className="card p-4">
            <h2 className="section-title mb-2">Durations are not set yet.</h2>
            <p className="text-[13px] text-muted">
              The schedule was designed without target minutes per block. Set them once, from
              your records, not from ambition.
            </p>
            <Link href="/flock" className="mt-2 inline-block text-[13px] font-medium text-tint-text">
              Set durations in Flock &rsaquo;
            </Link>
          </div>
        )}
      </div>
    </div>
  );
}
