"use client";

import { createCurrentReading } from "@/lib/actions";

export function CurrentReadingForm() {
  return (
    <form action={createCurrentReading} className="flex items-end gap-2 text-sm">
      <label className="flex flex-col gap-1">
        New title
        <input name="title" required className="rounded border border-black/20 px-2 py-1 dark:border-white/20" />
      </label>
      <button type="submit" className="rounded bg-black/90 px-3 py-1 text-white dark:bg-white/90 dark:text-black">
        Update
      </button>
    </form>
  );
}
