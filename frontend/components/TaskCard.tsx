"use client";

import { updateTaskStatus } from "@/lib/actions";
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
          aria-label={isDone ? "Tended" : "Mark tended"}
          className="check-btn flex h-7 w-7 items-center justify-center border hairline text-sm"
          style={{
            background: isDone ? "var(--tended-fill)" : "transparent",
            color: isDone ? "var(--tended-ink)" : "var(--muted)",
          }}
        >
          {isDone ? "✓" : ""}
        </button>
      </form>

      <div className="flex-1">
        <p className="text-[15px]" style={{ color: isDone ? "var(--muted)" : "var(--ink)" }}>
          {task.label}
        </p>
        <div className="mt-1 flex items-center gap-2">
          {isAutoCounted && <span className="chip chip-tint">{routineItem?.source}</span>}
          {routineItem?.isPrivate && <span className="chip chip-private">Private</span>}
          {isDone && <span className="chip chip-tended">Tended</span>}
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
