"use client";

import { useState } from "react";
import { submitReadingNote } from "@/lib/actions";
import type { Book } from "@/lib/types";

export function ReadingNoteForm({ book }: { book: Book }) {
  const [noteText, setNoteText] = useState("");
  const wordCount = noteText.trim() === "" ? 0 : noteText.trim().split(/\s+/).length;

  return (
    <form action={submitReadingNote} className="flex flex-col gap-2">
      <input type="hidden" name="bookId" value={book.id} />
      <input type="hidden" name="bookTitle" value={book.title} />
      {book.currentChapter !== null && <input type="hidden" name="currentChapter" value={book.currentChapter} />}
      {book.totalChapters !== null && <input type="hidden" name="totalChapters" value={book.totalChapters} />}
      <label htmlFor="note" className="text-[13px] font-semibold">
        A note in your own words
      </label>
      <textarea
        id="note"
        name="noteText"
        required
        rows={6}
        value={noteText}
        onChange={(e) => setNoteText(e.target.value)}
        placeholder="What's on your mind from this book, in your own words?"
        className="field"
      />
      <div className="flex flex-wrap items-center justify-between gap-2">
        <span className="text-[13px] text-muted">{wordCount} words</span>
        <button type="submit" className="btn-fill px-4 py-1.5">
          Give it to the Shepherd
        </button>
      </div>
    </form>
  );
}
