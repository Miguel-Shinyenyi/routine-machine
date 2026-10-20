import { ApiError, backendFetch, intelligenceFetch } from "./api";
import { withUserId } from "./scope";
import { dayOfWeekName, templatesNeedingMaterialization } from "./schedule";
import type { ScheduleTemplate, Suggestion, Task } from "./types";

// Called directly from the Board Server Component's render, not bound to a client form, so this
// is a plain server-side function rather than a "use server" Server Action. It decides, per the
// open question schedule.md left for implementation time, that the frontend (not the backend)
// orchestrates the cross-service call: resolving a LEARNING_SLOT template's topic for the day
// means asking the intelligence service, and the backend stays unaware that service exists.
// Per-user scoping (docs/backend.md): reads the user's own templates and tasks, and creates the
// day's tasks as theirs.
export async function materializeDay(date: string, userId: number): Promise<Task[]> {
  const day = dayOfWeekName(date);
  const [templates, existingTasks] = await Promise.all([
    backendFetch<ScheduleTemplate[]>(withUserId(`/api/schedule-templates?dayOfWeek=${day}`, userId)),
    backendFetch<Task[]>(withUserId(`/api/tasks?date=${date}`, userId)),
  ]);

  const toMaterialize = templatesNeedingMaterialization(templates, existingTasks);
  if (toMaterialize.length === 0) {
    return existingTasks;
  }

  const created: Task[] = [];
  for (const template of toMaterialize) {
    const body: Record<string, unknown> = { taskDate: date, scheduleTemplateId: template.id, userId };

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

    try {
      created.push(await backendFetch<Task>("/api/tasks", { method: "POST", body: JSON.stringify(body) }));
    } catch (error) {
      // A suggested topic that is not this user's own (the intelligence service is not yet
      // scoped per user) is rejected by the backend: skip the slot, same as no suggestion.
      if (error instanceof ApiError && error.status === 400) continue;
      throw error;
    }
  }

  return [...existingTasks, ...created];
}
