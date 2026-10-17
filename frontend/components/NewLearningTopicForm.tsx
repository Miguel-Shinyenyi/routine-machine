"use client";

import { createLearningTopic } from "@/lib/actions";
import type { Goal } from "@/lib/types";

export function NewLearningTopicForm({ goals }: { goals: Goal[] }) {
  return (
    <form action={createLearningTopic} className="flex items-end gap-2 text-sm">
      <label className="flex flex-col gap-1">
        Name
        <input name="name" required className="rounded-[10px] border hairline bg-surface px-2 py-1" />
      </label>
      <label className="flex flex-col gap-1">
        Goal
        <select name="goalId" required className="rounded-[10px] border hairline bg-surface px-2 py-1">
          {goals.map((goal) => (
            <option key={goal.id} value={goal.id}>
              {goal.name}
            </option>
          ))}
        </select>
      </label>
      <button type="submit" className="btn-fill px-4 py-1.5">
        Add
      </button>
    </form>
  );
}
