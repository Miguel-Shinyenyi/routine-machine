CREATE TABLE books (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(500) NOT NULL,
    author VARCHAR(255),
    current_chapter INT,
    total_chapters INT,
    source VARCHAR(20) NOT NULL DEFAULT 'manual',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT books_source_valid CHECK (source IN ('manual', 'hub-sync'))
);

CREATE INDEX idx_books_created_at ON books(created_at);
