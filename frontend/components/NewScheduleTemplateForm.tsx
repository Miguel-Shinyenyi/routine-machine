"use client";

import { useState } from "react";
import { createScheduleTemplate } from "@/lib/actions";
import type { RoutineItem } from "@/lib/types";

const DAYS = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"];

export function NewScheduleTemplateForm({ routineItems }: { routineItems: RoutineItem[] }) {
  const [targetType, setTargetType] = useState<"ROUTINE_ITEM" | "LEARNING_SLOT">("ROUTINE_ITEM");

  return (
    <form action={createScheduleTemplate} className="flex flex-wrap items-end gap-2 text-sm">
      <label className="flex flex-col gap-1">
        Day
        <select name="dayOfWeek" required className="rounded border border-black/20 px-2 py-1 dark:border-white/20">
          {DAYS.map((day) => (
            <option key={day} value={day}>
              {day}
            </option>
          ))}
        </select>
      </label>

      <label className="flex flex-col gap-1">
        Type
        <select
          name="targetType"
          value={targetType}
          onChange={(e) => setTargetType(e.target.value as "ROUTINE_ITEM" | "LEARNING_SLOT")}
          className="rounded border border-black/20 px-2 py-1 dark:border-white/20"
        >
          <option value="ROUTINE_ITEM">Routine item</option>
          <option value="LEARNING_SLOT">Learning slot</option>
        </select>
      </label>

      {targetType === "ROUTINE_ITEM" && (
        <label className="flex flex-col gap-1">
          Routine item
          <select name="routineItemId" required className="rounded border border-black/20 px-2 py-1 dark:border-white/20">
            {routineItems.map((item) => (
              <option key={item.id} value={item.id}>
                {item.name}
              </option>
            ))}
          </select>
        </label>
      )}

      <label className="flex flex-col gap-1">
        Duration (min)
        <input
          type="number"
          name="targetDurationMinutes"
          min={1}
          defaultValue={30}
          required
          className="w-20 rounded border border-black/20 px-2 py-1 dark:border-white/20"
        />
      </label>

      <label className="flex flex-col gap-1">
        Order
        <input
          type="number"
          name="sortOrder"
          defaultValue={1}
          required
          className="w-16 rounded border border-black/20 px-2 py-1 dark:border-white/20"
        />
      </label>

      <label className="flex flex-col gap-1">
        Label
        <input name="label" required className="rounded border border-black/20 px-2 py-1 dark:border-white/20" />
      </label>

      <button type="submit" className="rounded bg-black/90 px-3 py-1 text-white dark:bg-white/90 dark:text-black">
        Add
      </button>
    </form>
  );
}
