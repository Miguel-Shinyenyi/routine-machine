import { backendFetch } from "@/lib/api";
import type { ScheduleTemplate } from "@/lib/types";

const DAYS = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"];
const STILL_WATERS = new Set(["SATURDAY", "SUNDAY"]);

export default async function PathPage() {
  const templates = await backendFetch<ScheduleTemplate[]>("/api/schedule-templates");
  const durationsNotSet = templates.some((t) => !t.targetDurationMinutes || t.targetDurationMinutes <= 0);

  return (
    <div>
      <h1 className="page-title mb-6">The path.</h1>

      {durationsNotSet && (
        <div className="tile mb-4 p-4 text-[13px] text-muted">
          Durations are not set yet. See the open question in docs/schedule.md &mdash; enter real
          durations through Flock&rsquo;s schedule-template form.
        </div>
      )}

      <div className="grid grid-cols-1 gap-3 overflow-x-auto sm:grid-cols-7">
        {DAYS.map((day) => {
          const dayTemplates = templates
            .filter((template) => template.dayOfWeek === day)
            .sort((a, b) => a.sortOrder - b.sortOrder);
          const stillWaters = STILL_WATERS.has(day);

          return (
            <div
              key={day}
              className="card p-3"
              style={stillWaters ? { background: "var(--private-fill)" } : undefined}
            >
              <h2 className="mb-2 text-[13px] font-medium text-muted">{day[0] + day.slice(1).toLowerCase()}</h2>
              <div className="flex flex-col gap-2">
                {dayTemplates.map((template) => (
                  <div key={template.id} className="tile p-2 text-[13px]">
                    <p>{template.label}</p>
                    <p className="text-muted">
                      {template.targetDurationMinutes > 0 ? `${template.targetDurationMinutes}min` : "No duration"}
                    </p>
                  </div>
                ))}
                {dayTemplates.length === 0 && <p className="text-[13px] text-muted">Nothing scheduled</p>}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
