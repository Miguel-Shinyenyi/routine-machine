"use client";

import { createGoal } from "@/lib/actions";

export function NewGoalForm() {
  return (
    <form action={createGoal} className="flex flex-wrap items-end gap-2 text-sm">
      <label className="flex flex-col gap-1">
        Goal
        <input name="name" required placeholder="Learn Rust" className="rounded-[10px] border hairline bg-surface px-2 py-1" />
      </label>
      <label className="flex flex-col gap-1">
        Why it matters (optional)
        <input name="description" className="rounded-[10px] border hairline bg-surface px-2 py-1" />
      </label>
      <button type="submit" className="btn-fill px-4 py-1.5">
        Add
      </button>
    </form>
  );
}
