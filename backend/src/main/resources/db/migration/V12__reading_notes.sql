CREATE TABLE reading_notes (
    id BIGSERIAL PRIMARY KEY,
    book_id BIGINT NOT NULL REFERENCES books(id),
    note_text TEXT NOT NULL,
    chapter_placed TEXT,
    own_words_percentage INT,
    ideas_connected TEXT,
    quoted_phrases TEXT,
    question_back TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT reading_notes_own_words_percentage_valid
        CHECK (own_words_percentage IS NULL OR own_words_percentage BETWEEN 0 AND 100)
);

CREATE INDEX idx_reading_notes_book_id ON reading_notes(book_id);
