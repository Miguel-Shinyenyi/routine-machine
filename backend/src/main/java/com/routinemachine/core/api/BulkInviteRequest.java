package com.routinemachine.core.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// `csv` is the raw CSV text (one email per line, or email-first-column), not a multipart
// file upload — a CSV of emails is tiny, so the frontend just reads the file client-side
// (File.text()) and sends its content as plain JSON, no multipart plumbing needed.
public record BulkInviteRequest(@NotNull Long requestingUserId, @NotBlank String csv) {
}
