import { backendFetch, ApiError } from "@/lib/api";
import type { CurrentReading, Goal, LearningTopic, RoutineItem, ScheduleTemplate, Task } from "@/lib/types";
import { NewScheduleTemplateForm } from "@/components/NewScheduleTemplateForm";
import { NewAdHocTaskForm } from "@/components/NewAdHocTaskForm";
import { NewRoutineItemForm } from "@/components/NewRoutineItemForm";
import { NewLearningTopicForm } from "@/components/NewLearningTopicForm";
import { CurrentReadingForm } from "@/components/CurrentReadingForm";

const CATEGORIES = ["BODY", "MIND", "WORK", "HOME"] as const;

async function fetchCurrentReading(): Promise<CurrentReading | null> {
  try {
    return await backendFetch<CurrentReading>("/api/current-reading");
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) return null;
    throw error;
  }
}

export default async function FlockPage() {
  const [templates, routineItems, learningTopics, goals, allTasks, currentReading] = await Promise.all([
    backendFetch<ScheduleTemplate[]>("/api/schedule-templates"),
    backendFetch<RoutineItem[]>("/api/routine-items"),
    backendFetch<LearningTopic[]>("/api/learning-topics"),
    backendFetch<Goal[]>("/api/goals"),
    backendFetch<Task[]>("/api/tasks"),
    fetchCurrentReading(),
  ]);

  const adHocTasks = allTasks.filter((task) => task.scheduleTemplateId === null);
  const uncategorised = routineItems.filter((item) => item.category === null);

  return (
    <div className="flex flex-col gap-10">
      <h1 className="page-title">Your flock.</h1>

      <section>
        <h2 className="section-title mb-3">Currently reading</h2>
        <p className="mb-2 text-[15px]">{currentReading ? currentReading.title : "Nothing logged yet"}</p>
        <CurrentReadingForm />
      </section>

      <section>
        <h2 className="section-title mb-3">Routine items</h2>
        <div className="card mb-4 flex flex-col gap-4 p-4">
          {CATEGORIES.map((category) => {
            const items = routineItems.filter((item) => item.category === category);
            if (items.length === 0) return null;
            return (
              <div key={category}>
                <p className="mb-1 text-[13px] font-medium text-muted">
                  {category[0] + category.slice(1).toLowerCase()}
                </p>
                <ul className="flex flex-col gap-1 text-[15px]">
                  {items.map((item) => (
                    <li key={item.id} className="flex items-center gap-2 border-b hairline py-1">
                      <span className="flex-1">{item.name}</span>
                      {item.countRule && <span className="text-[13px] text-muted">{item.countRule}</span>}
                      {item.source !== "MANUAL" && <span className="chip chip-tint">{item.source}</span>}
                      {item.isPrivate && <span className="chip chip-private">Private</span>}
                    </li>
                  ))}
                </ul>
              </div>
            );
          })}
          {uncategorised.length > 0 && (
            <div>
              <p className="mb-1 text-[13px] font-medium text-muted">Uncategorised</p>
              <ul className="flex flex-col gap-1 text-[15px]">
                {uncategorised.map((item) => (
                  <li key={item.id} className="border-b hairline py-1">
                    {item.name}
                  </li>
                ))}
              </ul>
            </div>
          )}
          {routineItems.length === 0 && <p className="text-[13px] text-muted">None yet</p>}
        </div>
        <NewRoutineItemForm goals={goals} />
      </section>

      <section>
        <h2 className="section-title mb-3">Learning topics</h2>
        <ul className="card mb-4 flex flex-col gap-1 p-4 text-[15px]">
          {learningTopics.map((topic) => (
            <li key={topic.id}>
              {topic.name} <span className="text-[13px] text-muted">({topic.goalName})</span>
            </li>
          ))}
          {learningTopics.length === 0 && <li className="text-[13px] text-muted">None yet</li>}
        </ul>
        <NewLearningTopicForm goals={goals} />
      </section>

      <section>
        <h2 className="section-title mb-3">Schedule templates</h2>
        <ul className="card mb-4 flex flex-col gap-1 p-4 text-[15px]">
          {templates.map((template) => (
            <li key={template.id}>
              <span className="font-medium">{template.dayOfWeek}</span> &mdash; {template.label} (
              {template.targetType === "ROUTINE_ITEM" ? "routine" : "learning slot"},{" "}
              {template.targetDurationMinutes}min)
            </li>
          ))}
          {templates.length === 0 && <li className="text-[13px] text-muted">None yet</li>}
        </ul>
        <NewScheduleTemplateForm routineItems={routineItems} />
      </section>

      <section>
        <h2 className="section-title mb-3">Ad-hoc tasks</h2>
        <ul className="card mb-4 flex flex-col gap-1 p-4 text-[15px]">
          {adHocTasks.map((task) => (
            <li key={task.id}>
              {task.taskDate} &mdash; {task.label} ({task.status})
            </li>
          ))}
          {adHocTasks.length === 0 && <li className="text-[13px] text-muted">None yet</li>}
        </ul>
        <NewAdHocTaskForm routineItems={routineItems} />
      </section>
    </div>
  );
}
