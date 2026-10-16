package com.routinemachine.core.hubsync;

// path is the file's repo-relative path (e.g. "journal/entries/2026-10-16.md"), sha its git
// blob sha, stable across renames as long as the content doesn't change.
public record GitHubFile(String path, String sha) {
}
