"use client";

import { createRoutineItem } from "@/lib/actions";
import type { Goal } from "@/lib/types";

const CATEGORIES = ["BODY", "MIND", "WORK", "HOME"] as const;

export function NewRoutineItemForm({ goals }: { goals: Goal[] }) {
  return (
    <form action={createRoutineItem} className="flex flex-wrap items-end gap-2 text-sm">
      <label className="flex flex-col gap-1">
        Name
        <input name="name" required className="rounded-[10px] border hairline bg-surface px-2 py-1" />
      </label>

      <label className="flex flex-col gap-1">
        Category
        <select name="category" defaultValue="" className="rounded-[10px] border hairline bg-surface px-2 py-1">
          <option value="">Uncategorised</option>
          {CATEGORIES.map((category) => (
            <option key={category} value={category}>
              {category}
            </option>
          ))}
        </select>
      </label>

      <label className="flex flex-col gap-1">
        Goal (optional, for hours roll-up)
        <select name="goalId" defaultValue="" className="rounded-[10px] border hairline bg-surface px-2 py-1">
          <option value="">None</option>
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
