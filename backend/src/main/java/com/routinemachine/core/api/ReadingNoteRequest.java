package com.routinemachine.core.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ReadingNoteRequest(
        @NotNull Long bookId,
        @NotBlank String noteText,
        String chapterPlaced,
        @Min(0) @Max(100) Integer ownWordsPercentage,
        List<String> ideasConnected,
        List<String> quotedPhrases,
        String questionBack) {
}
