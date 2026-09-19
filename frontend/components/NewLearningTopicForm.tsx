"use client";

import { createLearningTopic } from "@/lib/actions";
import type { Goal } from "@/lib/types";

export function NewLearningTopicForm({ goals }: { goals: Goal[] }) {
  return (
    <form action={createLearningTopic} className="flex items-end gap-2 text-sm">
      <label className="flex flex-col gap-1">
        Name
        <input name="name" required className="rounded border border-black/20 px-2 py-1 dark:border-white/20" />
      </label>
      <label className="flex flex-col gap-1">
        Goal
        <select name="goalId" required className="rounded border border-black/20 px-2 py-1 dark:border-white/20">
          {goals.map((goal) => (
            <option key={goal.id} value={goal.id}>
              {goal.name}
            </option>
          ))}
        </select>
      </label>
      <button type="submit" className="rounded bg-black/90 px-3 py-1 text-white dark:bg-white/90 dark:text-black">
        Add
      </button>
    </form>
  );
}
