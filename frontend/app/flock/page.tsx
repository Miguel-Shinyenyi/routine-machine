import { backendFetch, ApiError } from "@/lib/api";
import type { CurrentReading, Goal, LearningTopic, RoutineItem, ScheduleTemplate, Task } from "@/lib/types";
import { NewScheduleTemplateForm } from "@/components/NewScheduleTemplateForm";
import { NewAdHocTaskForm } from "@/components/NewAdHocTaskForm";
import { NewRoutineItemForm } from "@/components/NewRoutineItemForm";
import { NewLearningTopicForm } from "@/components/NewLearningTopicForm";
import { CurrentReadingForm } from "@/components/CurrentReadingForm";

const CATEGORIES = ["BODY", "MIND", "WORK", "HOME"] as const;
const CATEGORY_LABELS: Record<string, string> = { BODY: "Body", MIND: "Mind", WORK: "Work", HOME: "Home" };
const CATEGORY_COLORS: Record<string, string> = {
  BODY: "#2f5a72",
  MIND: "#9a5208",
  WORK: "#0066cc",
  HOME: "#c93400",
};
const HILLSIDE_FIELD_LABELS: Record<string, string> = {
  READING: "Reading",
  MOVEMENT: "Movement",
  DEEP_WORK: "Deep work",
  LEARNING: "Learning",
  SLEEP: "Sleep",
  WRITING: "Writing",
};

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
      <div>
        <p className="text-[15px] font-semibold text-tint-text">
          {routineItems.length} {routineItems.length === 1 ? "routine" : "routines"}, {goals.length}{" "}
          {goals.length === 1 ? "goal" : "goals"}
        </p>
        <h1 className="page-title">Your flock.</h1>
        <p className="mt-2 max-w-[62ch] text-[17px] text-muted">
          Every routine you keep, and exactly how it gets counted. A good shepherd knows each
          one by name.
        </p>
      </div>

      <section>
        <h2 className="section-title mb-3">Currently reading</h2>
        <p className="mb-2 text-[15px]">{currentReading ? currentReading.title : "Nothing logged yet"}</p>
        <CurrentReadingForm />
      </section>

      <section className="flex flex-col gap-6">
        <h2 className="section-title">Routine items</h2>
        {CATEGORIES.map((category) => {
          const items = routineItems.filter((item) => item.category === category);
          if (items.length === 0) return null;
          return (
            <div key={category}>
              <h3 className="mb-1.5 px-1 text-[15px] font-semibold">{CATEGORY_LABELS[category]}</h3>
              <div className="overflow-hidden rounded-[14px] bg-surface">
                {items.map((item, i) => (
                  <div
                    key={item.id}
                    className={`flex items-center gap-3 px-4 py-2.5 ${i === 0 ? "" : "border-t hairline"}`}
                  >
                    <span
                      aria-hidden="true"
                      className="flex h-8 w-8 shrink-0 items-center justify-center rounded-[8px] text-[13px] font-bold text-white"
                      style={{ background: CATEGORY_COLORS[category] }}
                    >
                      {item.name.charAt(0).toUpperCase()}
                    </span>
                    <div className="min-w-0 flex-1">
                      <p className="text-[15px] font-medium">{item.name}</p>
                      {item.countRule && <p className="text-[13px] text-muted">{item.countRule}</p>}
                    </div>
                    {item.source !== "MANUAL" && <span className="chip chip-tint">{item.source}</span>}
                    {item.hillsideField !== null && (
                      <span className="chip">{HILLSIDE_FIELD_LABELS[item.hillsideField]}</span>
                    )}
                    {item.isPrivate && <span className="chip chip-private">Private</span>}
                  </div>
                ))}
              </div>
            </div>
          );
        })}
        {uncategorised.length > 0 && (
          <div>
            <h3 className="mb-1.5 px-1 text-[15px] font-semibold">Uncategorised</h3>
            <div className="overflow-hidden rounded-[14px] bg-surface">
              {uncategorised.map((item, i) => (
                <div key={item.id} className={`px-4 py-2.5 text-[15px] ${i === 0 ? "" : "border-t hairline"}`}>
                  {item.name}
                </div>
              ))}
            </div>
          </div>
        )}
        {routineItems.length === 0 && <p className="text-[13px] text-muted">None yet</p>}
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
