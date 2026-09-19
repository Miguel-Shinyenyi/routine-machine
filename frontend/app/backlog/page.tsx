import { backendFetch, ApiError } from "@/lib/api";
import type { CurrentReading, Goal, LearningTopic, RoutineItem, ScheduleTemplate, Task } from "@/lib/types";
import { NewScheduleTemplateForm } from "@/components/NewScheduleTemplateForm";
import { NewAdHocTaskForm } from "@/components/NewAdHocTaskForm";
import { NewRoutineItemForm } from "@/components/NewRoutineItemForm";
import { NewLearningTopicForm } from "@/components/NewLearningTopicForm";
import { CurrentReadingForm } from "@/components/CurrentReadingForm";

async function fetchCurrentReading(): Promise<CurrentReading | null> {
  try {
    return await backendFetch<CurrentReading>("/api/current-reading");
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) return null;
    throw error;
  }
}

export default async function BacklogPage() {
  const [templates, routineItems, learningTopics, goals, allTasks, currentReading] = await Promise.all([
    backendFetch<ScheduleTemplate[]>("/api/schedule-templates"),
    backendFetch<RoutineItem[]>("/api/routine-items"),
    backendFetch<LearningTopic[]>("/api/learning-topics"),
    backendFetch<Goal[]>("/api/goals"),
    backendFetch<Task[]>("/api/tasks"),
    fetchCurrentReading(),
  ]);

  const adHocTasks = allTasks.filter((task) => task.scheduleTemplateId === null);

  return (
    <div className="flex flex-col gap-10">
      <section>
        <h1 className="mb-3 text-lg font-semibold">Currently reading</h1>
        <p className="mb-2 text-sm">{currentReading ? currentReading.title : "Nothing logged yet"}</p>
        <CurrentReadingForm />
      </section>

      <section>
        <h1 className="mb-3 text-lg font-semibold">Routine items</h1>
        <ul className="mb-4 flex flex-col gap-1 text-sm">
          {routineItems.map((item) => (
            <li key={item.id}>{item.name}</li>
          ))}
          {routineItems.length === 0 && <li className="text-black/50 dark:text-white/50">None yet</li>}
        </ul>
        <NewRoutineItemForm />
      </section>

      <section>
        <h1 className="mb-3 text-lg font-semibold">Learning topics</h1>
        <ul className="mb-4 flex flex-col gap-1 text-sm">
          {learningTopics.map((topic) => (
            <li key={topic.id}>
              {topic.name} <span className="text-black/50 dark:text-white/50">({topic.goalName})</span>
            </li>
          ))}
          {learningTopics.length === 0 && <li className="text-black/50 dark:text-white/50">None yet</li>}
        </ul>
        <NewLearningTopicForm goals={goals} />
      </section>

      <section>
        <h1 className="mb-3 text-lg font-semibold">Schedule templates</h1>
        <ul className="mb-4 flex flex-col gap-1 text-sm">
          {templates.map((template) => (
            <li key={template.id}>
              <span className="font-medium">{template.dayOfWeek}</span> — {template.label} (
              {template.targetType === "ROUTINE_ITEM" ? "routine" : "learning slot"},{" "}
              {template.targetDurationMinutes}min)
            </li>
          ))}
          {templates.length === 0 && <li className="text-black/50 dark:text-white/50">None yet</li>}
        </ul>
        <NewScheduleTemplateForm routineItems={routineItems} />
      </section>

      <section>
        <h1 className="mb-3 text-lg font-semibold">Ad-hoc tasks</h1>
        <ul className="mb-4 flex flex-col gap-1 text-sm">
          {adHocTasks.map((task) => (
            <li key={task.id}>
              {task.taskDate} — {task.label} ({task.status})
            </li>
          ))}
          {adHocTasks.length === 0 && <li className="text-black/50 dark:text-white/50">None yet</li>}
        </ul>
        <NewAdHocTaskForm routineItems={routineItems} />
      </section>
    </div>
  );
}
