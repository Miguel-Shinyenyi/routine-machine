"use client";

import { createCurrentReading } from "@/lib/actions";

export function CurrentReadingForm() {
  return (
    <form action={createCurrentReading} className="flex items-end gap-2 text-sm">
      <label className="flex flex-col gap-1">
        New title
        <input name="title" required className="rounded-[10px] border hairline bg-surface px-2 py-1" />
      </label>
      <button type="submit" className="btn-fill px-4 py-1.5">
        Update
      </button>
    </form>
  );
}
