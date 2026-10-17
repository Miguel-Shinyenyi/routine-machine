"use client";

import { submitReadingNote } from "@/lib/actions";
import type { Book } from "@/lib/types";

export function ReadingNoteForm({ book }: { book: Book }) {
  return (
    <form action={submitReadingNote} className="flex flex-col gap-2">
      <input type="hidden" name="bookId" value={book.id} />
      <input type="hidden" name="bookTitle" value={book.title} />
      {book.currentChapter !== null && <input type="hidden" name="currentChapter" value={book.currentChapter} />}
      {book.totalChapters !== null && <input type="hidden" name="totalChapters" value={book.totalChapters} />}
      <textarea
        name="noteText"
        required
        rows={4}
        placeholder="What's on your mind from this book, in your own words?"
        className="rounded-[14px] border hairline bg-surface px-3 py-2 text-[15px]"
      />
      <button type="submit" className="btn-fill self-start px-4 py-1.5">
        Save note
      </button>
    </form>
  );
}
