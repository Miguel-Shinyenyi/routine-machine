import { ApiError, backendFetch } from "@/lib/api";
import { markQuotes } from "@/lib/markQuotes";
import type { Book, ReadingNote } from "@/lib/types";
import { NewBookForm } from "@/components/NewBookForm";
import { ReadingNoteForm } from "@/components/ReadingNoteForm";

// Mirrors ReadingNoteService.OWN_WORDS_THRESHOLD (backend/.../service/ReadingNoteService.java):
// the frontend has no way to ask "did this count," so it re-states the same named threshold
// rather than guessing. If that constant ever changes, this must change with it.
const OWN_WORDS_THRESHOLD = 70;

async function fetchCurrentBook(): Promise<Book | null> {
  try {
    return await backendFetch<Book>("/api/books/current");
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) return null;
    throw error;
  }
}

function chapterProgress(book: Book): string | null {
  if (book.currentChapter === null || book.totalChapters === null) return null;
  return `Chapter ${book.currentChapter} of ${book.totalChapters}`;
}

function noteStatusLabel(note: ReadingNote): string {
  if (note.ownWordsPercentage === null) return "not assessed";
  if (note.ownWordsPercentage >= OWN_WORDS_THRESHOLD) return `${note.ownWordsPercentage}% own words`;
  return "not counted, mostly quotes";
}

export default async function ReadingPage() {
  const book = await fetchCurrentBook();
  const notes = book ? await backendFetch<ReadingNote[]>(`/api/reading-notes?bookId=${book.id}`) : [];
  const [latestNote, ...earlierNotes] = notes;

  return (
    <div className="flex flex-col gap-6">
      <div>
        <p className="text-[15px] font-semibold text-tint-text">Reader&apos;s notes</p>
        <h1 className="page-title">Read slowly. Say it back.</h1>
        <p className="mt-2 max-w-[62ch] text-[17px] text-muted">
          Write what the pages gave you, in your words. The Shepherd finds where you are in the
          book from what you understood, not from what you copied.
        </p>
      </div>

      {book === null ? (
        <section className="card p-4">
          <h2 className="section-title mb-3">What are you reading?</h2>
          <NewBookForm />
        </section>
      ) : (
        <div className="flex flex-wrap items-start gap-6">
          <div className="flex min-w-0 flex-[999_1_540px] flex-col gap-4">
            <section className="card flex flex-wrap items-center gap-4 p-4">
              <div
                aria-hidden="true"
                className="flex h-[108px] w-[72px] shrink-0 items-end rounded-[6px] bg-ink p-2 text-[11px] font-semibold leading-tight text-canvas"
              >
                [Book cover]
              </div>
              <div className="min-w-0 flex-1 basis-[260px]">
                <h2 className="section-title">{book.title}</h2>
                <p className="mb-2.5 mt-0.5 text-[14px] text-muted">
                  {book.author ?? "Unknown author"}
                  {book.source === "hub-sync" && " · set from your journal's “Current book:” line"}
                </p>
                {chapterProgress(book) !== null && (
                  <>
                    <div className="mb-1.5 flex justify-between text-[14px]">
                      <span>{chapterProgress(book)}</span>
                    </div>
                    <div className="h-2 overflow-hidden rounded-full" style={{ background: "var(--fill)" }}>
                      <div
                        className="h-full rounded-full"
                        style={{
                          width: `${Math.round(((book.currentChapter ?? 0) / (book.totalChapters ?? 1)) * 100)}%`,
                          background: "var(--tint)",
                        }}
                      />
                    </div>
                  </>
                )}
              </div>
            </section>

            <section className="card p-4">
              <ReadingNoteForm book={book} />
            </section>

            {latestNote !== undefined && (
              <section className="card flex flex-col gap-3.5 p-4" aria-labelledby="read">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <h2 id="read" className="text-[17px] font-semibold">
                    What the Shepherd read.
                  </h2>
                  {latestNote.ownWordsPercentage !== null && latestNote.ownWordsPercentage >= OWN_WORDS_THRESHOLD && (
                    <span className="chip chip-tended">Counts for today</span>
                  )}
                </div>

                {latestNote.ownWordsPercentage !== null ? (
                  <>
                    <div className="grid grid-cols-3 gap-3">
                      <div className="tile p-3">
                        <b className="block text-[24px] font-semibold tracking-tight">
                          {latestNote.chapterPlaced ?? "—"}
                        </b>
                        <span className="text-[12px] text-muted">Where you are</span>
                      </div>
                      <div className="tile p-3">
                        <b className="block text-[24px] font-semibold tracking-tight">
                          {latestNote.ownWordsPercentage}%
                        </b>
                        <span className="text-[12px] text-muted">In your own words</span>
                      </div>
                      <div className="tile p-3">
                        <b className="block text-[24px] font-semibold tracking-tight">
                          {latestNote.ideasConnected?.length ?? 0}
                        </b>
                        <span className="text-[12px] text-muted">Ideas you connected</span>
                      </div>
                    </div>

                    <p className="text-[17px] leading-relaxed">
                      {markQuotes(latestNote.noteText, latestNote.quotedPhrases ?? []).map((segment, i) =>
                        segment.quoted ? (
                          <mark key={i} className="chip-tint rounded-none px-0.5 py-0">
                            {segment.text}
                          </mark>
                        ) : (
                          <span key={i}>{segment.text}</span>
                        ),
                      )}
                    </p>
                    {latestNote.quotedPhrases !== null && latestNote.quotedPhrases.length > 0 && (
                      <p className="text-[13px] text-muted">
                        Marked: close to the book&rsquo;s own wording. That&rsquo;s fine in a note; it just
                        doesn&rsquo;t count toward your own words.
                      </p>
                    )}

                    {latestNote.ideasConnected !== null && latestNote.ideasConnected.length > 0 && (
                      <ul className="flex flex-col gap-1.5 pl-5 text-[15px]" style={{ listStyleType: "disc" }}>
                        {latestNote.ideasConnected.map((idea, i) => (
                          <li key={i}>{idea}</li>
                        ))}
                      </ul>
                    )}

                    {latestNote.questionBack !== null && (
                      <div className="tile p-3 text-[15px]">
                        <strong className="font-semibold">One question back: </strong>
                        {latestNote.questionBack}
                      </div>
                    )}
                  </>
                ) : (
                  <>
                    <p className="text-[15px]">{latestNote.noteText}</p>
                    <p className="text-[13px] text-muted">
                      This note wasn&apos;t assessed (the assessment may be unavailable right now), but
                      it&apos;s saved.
                    </p>
                  </>
                )}
              </section>
            )}
          </div>

          <aside className="flex min-w-0 flex-[1_1_300px] flex-col gap-4" aria-label="Earlier notes and how it works">
            {earlierNotes.length > 0 && (
              <div>
                <h2 className="mb-2 px-1 text-[17px] font-semibold">Earlier notes</h2>
                <div className="list overflow-hidden rounded-[14px] bg-surface">
                  {earlierNotes.map((note, i) => (
                    <div
                      key={note.id}
                      className={`flex items-center gap-3 px-4 py-2.5 ${i === 0 ? "" : "border-t hairline"}`}
                    >
                      <span className="chip">{note.chapterPlaced ?? "Unplaced"}</span>
                      <div className="min-w-0">
                        <strong className="block truncate text-[14px] font-semibold">{note.noteText}</strong>
                        <span className="text-[12px] text-muted">
                          {note.createdAt.slice(0, 10)} · {noteStatusLabel(note)}
                        </span>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}

            <div className="card">
              <h2 className="text-[17px] font-semibold">How a note is read.</h2>
              <ol className="mt-2 flex flex-col gap-1.5 pl-5 text-[14px] text-muted" style={{ listStyleType: "decimal" }}>
                <li>Your note is sent to an assessment that places where you are in the book.</li>
                <li>Overlap with the book&rsquo;s own wording is marked. Copied passages never count.</li>
                <li>It looks for synthesis: your examples, links to your life or work, a question of your own.</li>
                <li>A note counts when most of it is yours and it names an idea, not just events.</li>
              </ol>
            </div>

            <div className="card">
              <h2 className="text-[17px] font-semibold">Your notes stay yours.</h2>
              <p className="mt-1.5 text-[14px] text-muted">
                The hillside sees only that a note counted and how far through the book you are.
                Never the words.
              </p>
            </div>
          </aside>
        </div>
      )}
    </div>
  );
}
