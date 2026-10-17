package com.routinemachine.core.api;

import jakarta.validation.constraints.NotBlank;

public record BookRequest(@NotBlank String title, String author, Integer currentChapter, Integer totalChapters) {
}
