"use client";

import { updateTaskStatus } from "@/lib/actions";
import { sourceLabel } from "@/lib/sourceLabels";
import type { RoutineItem, Task } from "@/lib/types";

const NEXT_STATUS: Record<Task["status"], Task["status"] | null> = {
  TODO: "IN_PROGRESS",
  IN_PROGRESS: "DONE",
  DONE: null,
};

export function TaskCard({ task, routineItem }: { task: Task; routineItem?: RoutineItem }) {
  const next = NEXT_STATUS[task.status];
  const isDone = task.status === "DONE";
  const isAutoCounted = routineItem !== undefined && routineItem.source !== "MANUAL";

  return (
    <div className="flex items-center gap-3 border-b hairline px-1 py-3 last:border-b-0">
      <form action={next ? updateTaskStatus.bind(null, task.id, next) : undefined}>
        <button
          type="submit"
          disabled={!next}
          aria-pressed={isDone}
          aria-label={isDone ? `Mark not tended: ${task.label}` : `Mark tended: ${task.label}`}
          className="check-btn flex h-[30px] w-[30px] items-center justify-center border-2"
          style={{
            background: isDone ? "var(--tended-ink)" : "var(--surface)",
            borderColor: isDone ? "var(--tended-ink)" : "#c7c7cc",
          }}
        >
          {isDone && (
            <svg viewBox="0 0 16 16" width="16" height="16" fill="none" stroke="#fff" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <path d="M3.5 8.5l3 3 6-7" />
            </svg>
          )}
        </button>
      </form>

      <div className="flex-1">
        <p className="text-[15px]" style={{ color: isDone ? "var(--muted)" : "var(--ink)" }}>
          {task.label}
        </p>
        <div className="mt-1 flex items-center gap-2">
          {isAutoCounted && (
            <span className={`chip ${isDone ? "chip-tended" : "chip-tint"}`}>
              {routineItem && sourceLabel(routineItem.source)}
            </span>
          )}
          {routineItem?.isPrivate && <span className="chip chip-private">Private</span>}
          {!isAutoCounted && isDone && <span className="chip chip-tended">Tended</span>}
        </div>
      </div>

      {task.status === "IN_PROGRESS" && (
        <form action={updateTaskStatus.bind(null, task.id, "TODO")}>
          <button type="submit" className="text-[13px] text-muted hover:underline">
            Reset
          </button>
        </form>
      )}
      {next && (
        <form action={updateTaskStatus.bind(null, task.id, next)}>
          <button type="submit" className="btn-fill px-3 py-1 text-[13px]">
            {task.status === "TODO" ? "Start" : "Mark tended"}
          </button>
        </form>
      )}
    </div>
  );
}
