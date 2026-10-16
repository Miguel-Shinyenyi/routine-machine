package com.routinemachine.core.hubsync;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

// Reads UMWAYI's public file listing and commit history over the GitHub REST API. No
// credential needed: UMWAYI is a public repo and this only ever reads file names, blob shas,
// and commit dates, never content (content reads, where they're needed, go through
// HubJournalReader's git-crypt unlock flow instead).
public interface GitHubContentClient {

    // Files directly inside the given repo-relative directory. Never throws: a missing
    // directory or a failed call both return an empty list, logged by the implementation, so a
    // sync run skips that directory rather than failing the whole run.
    List<GitHubFile> listDirectory(String path);

    // The calendar date of the path's earliest commit, used for files (like essays) whose name
    // doesn't carry a date the way journal entries' do. Empty if the call fails or the path has
    // no commit history.
    Optional<LocalDate> firstCommitDate(String path);
}
