"use client";

import { updateTaskStatus } from "@/lib/actions";
import { sourceLabel } from "@/lib/sourceLabels";
import type { RoutineItem, Task } from "@/lib/types";

// The design reference's check is a single binary toggle (done/not done), no in-progress
// state shown anywhere in it. This app's Task still has a real TODO/IN_PROGRESS/DONE status
// (Path and the board rely on it), but Today's own check button only ever asks for the two
// ends of that chain — untended to tended, or back — matching the reference exactly rather
// than exposing a three-step flow the design never shows.
export function TaskCard({ task, routineItem }: { task: Task; routineItem?: RoutineItem }) {
  const isDone = task.status === "DONE";
  const toggleTo = isDone ? "TODO" : "DONE";
  const isAutoCounted = routineItem !== undefined && routineItem.source !== "MANUAL";

  return (
    <div className="flex items-center gap-3 border-b hairline px-1 py-3 last:border-b-0">
      <form action={updateTaskStatus.bind(null, task.id, toggleTo)}>
        <button
          type="submit"
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
    </div>
  );
}
