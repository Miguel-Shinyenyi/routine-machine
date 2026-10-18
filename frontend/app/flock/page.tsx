import { backendFetch } from "@/lib/api";
import { sourceLabel } from "@/lib/sourceLabels";
import { routineIcon } from "@/lib/routineIcons";
import type { Goal, LearningTopic, RoutineItem, ScheduleTemplate, Task } from "@/lib/types";
import { NewScheduleTemplateForm } from "@/components/NewScheduleTemplateForm";
import { NewAdHocTaskForm } from "@/components/NewAdHocTaskForm";
import { NewRoutineItemForm } from "@/components/NewRoutineItemForm";
import { NewLearningTopicForm } from "@/components/NewLearningTopicForm";

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

// Matches docs/design-reference/pages/flock.html's .row grid exactly: name, counted-by,
// watering hole, hillside field, chevron.
const ROW_GRID = "minmax(150px,1.2fr) minmax(180px,1.6fr) minmax(120px,1fr) minmax(110px,.9fr) 16px";

export default async function FlockPage() {
  const [templates, allRoutineItems, learningTopics, goals, allTasks] = await Promise.all([
    backendFetch<ScheduleTemplate[]>("/api/schedule-templates"),
    backendFetch<RoutineItem[]>("/api/routine-items"),
    backendFetch<LearningTopic[]>("/api/learning-topics"),
    backendFetch<Goal[]>("/api/goals"),
    backendFetch<Task[]>("/api/tasks"),
  ]);

  // GET /api/routine-items with no userId returns every legacy row AND every demo user's own
  // items (Phase 8's hillside seed data) in one flat list — Flock is this single-user app's
  // own management page, not a multi-user view, so it only ever shows the unowned (userId
  // null) items, same as this page's single-user world before Phase 8 existed.
  const routineItems = allRoutineItems.filter((item) => item.userId == null);
  const adHocTasks = allTasks.filter((task) => task.scheduleTemplateId === null);
  const uncategorised = routineItems.filter((item) => item.category === null);

  return (
    <div className="flex flex-col gap-10">
      <div className="flex flex-wrap items-start justify-between gap-5">
        <div className="min-w-0 flex-1 basis-[420px]">
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
        <div className="flex flex-wrap gap-2">
          <a
            href="#schedule-templates"
            className="rounded-full px-4 py-2 text-[14px] font-semibold"
            style={{ background: "var(--fill)", color: "var(--tint-text)" }}
          >
            Edit the path
          </a>
          <a href="#add-routine" className="btn-fill rounded-full px-4 py-2 text-[14px] font-semibold">
            Add to the flock
          </a>
        </div>
      </div>

      <section className="flex flex-col gap-6">
        <h2 className="section-title">Routine items</h2>
        {CATEGORIES.map((category) => {
          const items = routineItems.filter((item) => item.category === category);
          if (items.length === 0) return null;
          return (
            <div key={category}>
              <h3 className="mb-1.5 px-1 text-[15px] font-semibold">{CATEGORY_LABELS[category]}</h3>
              <div className="overflow-x-auto rounded-[14px]">
                <div className="min-w-[760px] overflow-hidden rounded-[14px] bg-surface">
                  <div
                    className="grid min-h-[34px] items-center gap-3.5 px-4 text-[12px] font-medium text-muted"
                    style={{ gridTemplateColumns: ROW_GRID, background: "#fafafc" }}
                  >
                    <span>Routine</span>
                    <span>Counted by</span>
                    <span>Watering hole</span>
                    <span>On the hillside</span>
                    <span />
                  </div>
                  {items.map((item, i) => (
                    <div
                      key={item.id}
                      className={`grid min-h-[60px] items-center gap-3.5 px-4 py-2.5 ${i === 0 ? "" : "border-t hairline"}`}
                      style={{ gridTemplateColumns: ROW_GRID }}
                    >
                      <span className="flex items-center gap-2.5 font-semibold">
                        <span
                          aria-hidden="true"
                          className="flex h-7 w-7 shrink-0 items-center justify-center rounded-[7px]"
                          style={{ background: CATEGORY_COLORS[category] }}
                        >
                          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="#fff" strokeWidth="2" strokeLinecap="round" aria-hidden="true">
                            {routineIcon(item.name, item.category)}
                          </svg>
                        </span>
                        {item.name}
                      </span>
                      <span className="text-[14px]">
                        {item.countRule ?? "—"}
                        {item.description && <span className="block text-[12px] text-muted">{item.description}</span>}
                      </span>
                      <span>
                        {item.source !== "MANUAL" && <span className="chip chip-tint">{sourceLabel(item.source)}</span>}
                        {item.source === "MANUAL" && <span className="chip">Tapped</span>}
                      </span>
                      <span>
                        {item.hillsideField !== null ? (
                          <span className="chip">{HILLSIDE_FIELD_LABELS[item.hillsideField]}</span>
                        ) : item.isPrivate ? (
                          <span className="chip chip-private">Private</span>
                        ) : null}
                      </span>
                      <span className="text-[#c7c7cc]" aria-hidden="true">
                        &rsaquo;
                      </span>
                    </div>
                  ))}
                </div>
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
        <div className="card p-4">
          <h2 className="text-[17px] font-semibold">Private stays private.</h2>
          <p className="mt-1.5 text-[14px] text-muted">
            Routines marked Private are counted in your tally and never leave it. Only the
            fields you choose reach the hillside, and only as a pseudonym.
          </p>
        </div>
        <div id="add-routine">
          <NewRoutineItemForm goals={goals} />
        </div>
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

      <section id="schedule-templates">
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
