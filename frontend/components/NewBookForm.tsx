"use client";

import { createBook } from "@/lib/actions";

export function NewBookForm() {
  return (
    <form action={createBook} className="flex flex-wrap items-end gap-2 text-sm">
      <label className="flex flex-col gap-1">
        Title
        <input name="title" required className="rounded-[10px] border hairline bg-surface px-2 py-1" />
      </label>

      <label className="flex flex-col gap-1">
        Author
        <input name="author" className="rounded-[10px] border hairline bg-surface px-2 py-1" />
      </label>

      <label className="flex flex-col gap-1">
        Total chapters
        <input
          name="totalChapters"
          type="number"
          min={1}
          className="w-24 rounded-[10px] border hairline bg-surface px-2 py-1"
        />
      </label>

      <button type="submit" className="btn-fill px-4 py-1.5">
        Start this book
      </button>
    </form>
  );
}
