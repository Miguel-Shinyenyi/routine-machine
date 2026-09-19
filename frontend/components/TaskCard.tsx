"use client";

import { updateTaskStatus } from "@/lib/actions";
import type { Task } from "@/lib/types";

const NEXT_STATUS: Record<Task["status"], Task["status"] | null> = {
  TODO: "IN_PROGRESS",
  IN_PROGRESS: "DONE",
  DONE: null,
};

const NEXT_LABEL: Record<Task["status"], string> = {
  TODO: "Start",
  IN_PROGRESS: "Mark done",
  DONE: "",
};

export function TaskCard({ task }: { task: Task }) {
  const next = NEXT_STATUS[task.status];

  return (
    <div className="rounded border border-black/10 p-3 dark:border-white/15">
      <p className="text-sm">{task.label}</p>
      <div className="mt-2 flex items-center gap-2">
        {task.status !== "TODO" && (
          <form action={updateTaskStatus.bind(null, task.id, "TODO")}>
            <button type="submit" className="text-xs text-black/50 hover:underline dark:text-white/50">
              Reset
            </button>
          </form>
        )}
        {next && (
          <form action={updateTaskStatus.bind(null, task.id, next)}>
            <button
              type="submit"
              className="rounded bg-black/90 px-2 py-1 text-xs text-white hover:bg-black dark:bg-white/90 dark:text-black"
            >
              {NEXT_LABEL[task.status]}
            </button>
          </form>
        )}
      </div>
    </div>
  );
}
