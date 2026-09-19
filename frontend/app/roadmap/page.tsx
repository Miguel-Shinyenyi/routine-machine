import { backendFetch } from "@/lib/api";
import type { ScheduleTemplate } from "@/lib/types";

const DAYS = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"];

export default async function RoadmapPage() {
  const templates = await backendFetch<ScheduleTemplate[]>("/api/schedule-templates");

  return (
    <div>
      <h1 className="mb-4 text-lg font-semibold">Roadmap</h1>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-7">
        {DAYS.map((day) => {
          const dayTemplates = templates
            .filter((template) => template.dayOfWeek === day)
            .sort((a, b) => a.sortOrder - b.sortOrder);

          return (
            <div key={day}>
              <h2 className="mb-2 text-xs font-medium text-black/60 dark:text-white/60">{day}</h2>
              <div className="flex flex-col gap-2">
                {dayTemplates.map((template) => (
                  <div key={template.id} className="rounded border border-black/10 p-2 text-xs dark:border-white/15">
                    {template.label}
                    <div className="text-black/50 dark:text-white/50">{template.targetDurationMinutes}min</div>
                  </div>
                ))}
                {dayTemplates.length === 0 && (
                  <p className="text-xs text-black/40 dark:text-white/40">Nothing scheduled</p>
                )}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
