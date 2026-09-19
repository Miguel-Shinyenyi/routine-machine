import { backendFetch, intelligenceFetch } from "./api";
import { dayOfWeekName, templatesNeedingMaterialization } from "./schedule";
import type { ScheduleTemplate, Suggestion, Task } from "./types";

// Called directly from the Board Server Component's render, not bound to a client form, so this
// is a plain server-side function rather than a "use server" Server Action. It decides, per the
// open question schedule.md left for implementation time, that the frontend (not the backend)
// orchestrates the cross-service call: resolving a LEARNING_SLOT template's topic for the day
// means asking the intelligence service, and the backend stays unaware that service exists.
export async function materializeDay(date: string): Promise<Task[]> {
  const day = dayOfWeekName(date);
  const [templates, existingTasks] = await Promise.all([
    backendFetch<ScheduleTemplate[]>(`/api/schedule-templates?dayOfWeek=${day}`),
    backendFetch<Task[]>(`/api/tasks?date=${date}`),
  ]);

  const toMaterialize = templatesNeedingMaterialization(templates, existingTasks);
  if (toMaterialize.length === 0) {
    return existingTasks;
  }

  const created: Task[] = [];
  for (const template of toMaterialize) {
    const body: Record<string, unknown> = { taskDate: date, scheduleTemplateId: template.id };

    if (template.targetType === "LEARNING_SLOT") {
      const suggestion = await intelligenceFetch<Suggestion>("/suggestion").catch(() => null);
      if (suggestion === null) {
        // No learning topics configured yet, or the intelligence service is unreachable: skip
        // this slot for today rather than failing the whole board. It'll be retried on the next
        // load once a topic exists to suggest.
        continue;
      }
      body.learningTopicId = suggestion.topic.id;
    }

    created.push(await backendFetch<Task>("/api/tasks", { method: "POST", body: JSON.stringify(body) }));
  }

  return [...existingTasks, ...created];
}
