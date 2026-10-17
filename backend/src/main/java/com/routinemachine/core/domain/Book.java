package com.routinemachine.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.OffsetDateTime;
import java.util.Objects;

// Phase 7's reading record, superseding current_reading_log as the source of truth for "what
// book am I reading" (current_reading_log's existing rows are kept for history, but nothing
// writes new ones from this point on). "Current book" is the latest row by created_at, the
// same latest-row-wins rule current_reading_log used, not a separate finished/unfinished flag
// this phase doesn't need yet.
@Entity
@Table(name = "books")
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column
    private String author;

    @Column(name = "current_chapter")
    private Integer currentChapter;

    @Column(name = "total_chapters")
    private Integer totalChapters;

    @Column(nullable = false)
    private ReadingSource source;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime createdAt;

    protected Book() {
    }

    public Book(String title, String author, Integer currentChapter, Integer totalChapters, ReadingSource source) {
        this.title = Objects.requireNonNull(title, "title must not be null");
        this.author = author;
        this.currentChapter = currentChapter;
        this.totalChapters = totalChapters;
        this.source = source == null ? ReadingSource.MANUAL : source;
    }

    public Book(String title, ReadingSource source) {
        this(title, null, null, null, source);
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public Integer getCurrentChapter() {
        return currentChapter;
    }

    public Integer getTotalChapters() {
        return totalChapters;
    }

    public ReadingSource getSource() {
        return source;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
