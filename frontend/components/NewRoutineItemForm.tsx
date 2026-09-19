"use client";

import { createRoutineItem } from "@/lib/actions";

export function NewRoutineItemForm() {
  return (
    <form action={createRoutineItem} className="flex items-end gap-2 text-sm">
      <label className="flex flex-col gap-1">
        Name
        <input name="name" required className="rounded border border-black/20 px-2 py-1 dark:border-white/20" />
      </label>
      <button type="submit" className="rounded bg-black/90 px-3 py-1 text-white dark:bg-white/90 dark:text-black">
        Add
      </button>
    </form>
  );
}
