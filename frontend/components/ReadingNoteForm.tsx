"use client";

import { useState, useSyncExternalStore } from "react";
import { submitReadingNote } from "@/lib/actions";
import type { Book } from "@/lib/types";

function draftKey(bookId: number): string {
  return `routine-machine:reading-draft:${bookId}`;
}

// A real (if small) feature, not a decorative copy of the reference's own "Save draft"
// button: since there's no backend draft state, this reads/writes the browser's own
// localStorage, scoped per book, so a saved draft survives a reload or a closed tab without
// needing a new endpoint. Every access is wrapped — private browsing or blocked storage just
// means drafts silently don't persist, not a crash.
function loadDraft(bookId: number): string {
  try {
    return localStorage.getItem(draftKey(bookId)) ?? "";
  } catch {
    return "";
  }
}

function saveDraft(bookId: number, text: string) {
  try {
    if (text.trim() === "") {
      localStorage.removeItem(draftKey(bookId));
    } else {
      localStorage.setItem(draftKey(bookId), text);
    }
  } catch {
    // Storage unavailable (private browsing, blocked site data) — the note itself still works.
  }
}

// No subscribe-able change event for localStorage within the same tab, so this never notifies
// — useSyncExternalStore is used purely for its safe server/client snapshot split (empty on
// the server, the real draft once hydrated on the client), the same reason ThemeToggle uses
// it instead of useState set inside a useEffect (see docs/frontend.md's decisions log).
function subscribeNever() {
  return () => {};
}

export function ReadingNoteForm({ book }: { book: Book }) {
  const draft = useSyncExternalStore(
    subscribeNever,
    () => loadDraft(book.id),
    () => "",
  );
  const [edited, setEdited] = useState<string | null>(null);
  const [draftSaved, setDraftSaved] = useState(false);
  const noteText = edited ?? draft;
  const wordCount = noteText.trim() === "" ? 0 : noteText.trim().split(/\s+/).length;

  return (
    <form
      action={(formData) => {
        saveDraft(book.id, "");
        return submitReadingNote(formData);
      }}
      className="flex flex-col gap-2"
    >
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
        onChange={(e) => {
          setEdited(e.target.value);
          setDraftSaved(false);
        }}
        placeholder="What's on your mind from this book, in your own words?"
        className="field"
      />
      <div className="flex flex-wrap items-center justify-between gap-2">
        <span className="text-[13px] text-muted">
          {wordCount} words{draftSaved && " · draft saved"}
        </span>
        <div className="flex gap-2">
          <button
            type="button"
            className="rounded-full px-4 py-1.5 text-[14px] font-semibold"
            style={{ background: "var(--fill)", color: "var(--tint-text)" }}
            onClick={() => {
              saveDraft(book.id, noteText);
              setDraftSaved(true);
            }}
          >
            Save draft
          </button>
          <button type="submit" className="btn-fill px-4 py-1.5">
            Give it to the Shepherd
          </button>
        </div>
      </div>
    </form>
  );
}
