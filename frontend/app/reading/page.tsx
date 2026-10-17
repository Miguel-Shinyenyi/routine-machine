import { ApiError, backendFetch } from "@/lib/api";
import type { Book, ReadingNote } from "@/lib/types";
import { NewBookForm } from "@/components/NewBookForm";
import { ReadingNoteForm } from "@/components/ReadingNoteForm";

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

export default async function ReadingPage() {
  const book = await fetchCurrentBook();
  const notes = book ? await backendFetch<ReadingNote[]>(`/api/reading-notes?bookId=${book.id}`) : [];
  const [latestNote, ...earlierNotes] = notes;

  return (
    <div className="flex flex-col gap-8">
      <h1 className="page-title">Reader&apos;s notes.</h1>

      {book === null ? (
        <section className="card p-4">
          <h2 className="section-title mb-3">What are you reading?</h2>
          <NewBookForm />
        </section>
      ) : (
        <>
          <section className="card p-4">
            <h2 className="section-title">{book.title}</h2>
            {book.author !== null && <p className="text-[13px] text-muted">{book.author}</p>}
            {chapterProgress(book) !== null && (
              <p className="mt-2 text-[15px] text-tint-text">{chapterProgress(book)}</p>
            )}
          </section>

          <section className="card p-4">
            <h2 className="section-title mb-3">A note in your own words</h2>
            <ReadingNoteForm book={book} />
            <p className="mt-3 text-[13px] text-muted">
              How a note is read: your note is sent to an assessment that looks for your own
              words, ideas connected elsewhere in the book, and anything quoted directly, then
              asks one question back. Notes never leave the user beyond that one assessment
              call.
            </p>
          </section>

          {latestNote !== undefined && (
            <section className="card p-4">
              <h2 className="section-title mb-3">What the Shepherd read.</h2>
              <p className="text-[15px]">{latestNote.noteText}</p>
              {latestNote.chapterPlaced !== null ? (
                <dl className="mt-4 flex flex-col gap-2 text-[14px]">
                  <div>
                    <dt className="text-muted">Chapter placed</dt>
                    <dd>{latestNote.chapterPlaced}</dd>
                  </div>
                  <div>
                    <dt className="text-muted">Own words</dt>
                    <dd>{latestNote.ownWordsPercentage}%</dd>
                  </div>
                  {latestNote.ideasConnected !== null && latestNote.ideasConnected.length > 0 && (
                    <div>
                      <dt className="text-muted">Ideas connected</dt>
                      <dd>
                        <ul className="list-disc pl-5">
                          {latestNote.ideasConnected.map((idea, index) => (
                            <li key={index}>{idea}</li>
                          ))}
                        </ul>
                      </dd>
                    </div>
                  )}
                  {latestNote.quotedPhrases !== null && latestNote.quotedPhrases.length > 0 && (
                    <div>
                      <dt className="text-muted">Quoted phrases marked</dt>
                      <dd className="flex flex-wrap gap-1">
                        {latestNote.quotedPhrases.map((phrase, index) => (
                          <span key={index} className="chip chip-tint">
                            &ldquo;{phrase}&rdquo;
                          </span>
                        ))}
                      </dd>
                    </div>
                  )}
                  <div>
                    <dt className="text-muted">One question back</dt>
                    <dd>{latestNote.questionBack}</dd>
                  </div>
                </dl>
              ) : (
                <p className="mt-3 text-[13px] text-muted">
                  This note wasn&apos;t assessed (the assessment may be unavailable right now),
                  but it&apos;s saved.
                </p>
              )}
            </section>
          )}

          {earlierNotes.length > 0 && (
            <section className="card p-4">
              <h2 className="section-title mb-3">Earlier notes</h2>
              <ul className="flex flex-col gap-3">
                {earlierNotes.map((note) => (
                  <li key={note.id} className="tile p-3">
                    <p className="text-[13px] text-muted">{note.createdAt.slice(0, 10)}</p>
                    <p className="text-[15px]">{note.noteText}</p>
                  </li>
                ))}
              </ul>
            </section>
          )}
        </>
      )}
    </div>
  );
}
