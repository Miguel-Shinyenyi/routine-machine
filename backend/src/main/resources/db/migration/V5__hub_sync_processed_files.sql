CREATE TABLE hub_sync_processed_files (
    id BIGSERIAL PRIMARY KEY,
    repo_path VARCHAR(500) NOT NULL,
    content_sha VARCHAR(64) NOT NULL UNIQUE,
    kind VARCHAR(20) NOT NULL,
    file_date DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT hub_sync_processed_files_kind_valid CHECK (kind IN ('JOURNAL', 'ARTICLE'))
);

CREATE INDEX idx_hub_sync_processed_files_file_date ON hub_sync_processed_files(file_date);
