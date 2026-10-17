package com.routinemachine.core.api;

import com.routinemachine.core.domain.ReadingNote;

import java.time.OffsetDateTime;
import java.util.List;

public record ReadingNoteResponse(
        Long id,
        Long bookId,
        String bookTitle,
        String noteText,
        String chapterPlaced,
        Integer ownWordsPercentage,
        List<String> ideasConnected,
        List<String> quotedPhrases,
        String questionBack,
        OffsetDateTime createdAt) {

    public static ReadingNoteResponse from(ReadingNote note) {
        return new ReadingNoteResponse(
                note.getId(),
                note.getBook().getId(),
                note.getBook().getTitle(),
                note.getNoteText(),
                note.getChapterPlaced(),
                note.getOwnWordsPercentage(),
                note.getIdeasConnected(),
                note.getQuotedPhrases(),
                note.getQuestionBack(),
                note.getCreatedAt());
    }
}
