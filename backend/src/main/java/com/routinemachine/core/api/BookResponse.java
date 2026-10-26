package com.routinemachine.core.api;

import com.routinemachine.core.domain.Book;
import com.routinemachine.core.service.Ownership;

import java.time.OffsetDateTime;

public record BookResponse(
        Long id, String title, String author, Integer currentChapter, Integer totalChapters, String source,
        OffsetDateTime createdAt, Long userId) {

    public static BookResponse from(Book book) {
        return new BookResponse(
                book.getId(),
                book.getTitle(),
                book.getAuthor(),
                book.getCurrentChapter(),
                book.getTotalChapters(),
                book.getSource().getValue(),
                book.getCreatedAt(),
                Ownership.idOf(book.getUser()));
    }
}
