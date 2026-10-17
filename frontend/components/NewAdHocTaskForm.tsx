"use client";

import { createAdHocTask } from "@/lib/actions";
import { todayIsoDate } from "@/lib/schedule";
import type { RoutineItem } from "@/lib/types";

export function NewAdHocTaskForm({ routineItems }: { routineItems: RoutineItem[] }) {
  return (
    <form action={createAdHocTask} className="flex flex-wrap items-end gap-2 text-sm">
      <label className="flex flex-col gap-1">
        Date
        <input
          type="date"
          name="taskDate"
          defaultValue={todayIsoDate()}
          required
          className="rounded-[10px] border hairline bg-surface px-2 py-1"
        />
      </label>

      <label className="flex flex-col gap-1">
        Label
        <input name="label" required className="rounded-[10px] border hairline bg-surface px-2 py-1" />
      </label>

      <label className="flex flex-col gap-1">
        Routine item (optional)
        <select name="routineItemId" defaultValue="" className="rounded-[10px] border hairline bg-surface px-2 py-1">
          <option value="">None</option>
          {routineItems.map((item) => (
            <option key={item.id} value={item.id}>
              {item.name}
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
