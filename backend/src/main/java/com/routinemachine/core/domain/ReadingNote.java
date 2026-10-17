package com.routinemachine.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

// A reader's own note on a book, optionally assessed by the intelligence service's
// POST /reading-notes/assess (see docs/intelligence-service.md). The assessment fields are
// all nullable: a note can be saved even when assessment failed or was never run, since
// writing the note down is the user's actual action, assessment is an addition to it, not a
// precondition for it.
@Entity
@Table(name = "reading_notes")
public class ReadingNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Column(name = "note_text", nullable = false)
    private String noteText;

    @Column(name = "chapter_placed")
    private String chapterPlaced;

    @Column(name = "own_words_percentage")
    private Integer ownWordsPercentage;

    @Convert(converter = StringListConverter.class)
    @Column(name = "ideas_connected")
    private List<String> ideasConnected;

    @Convert(converter = StringListConverter.class)
    @Column(name = "quoted_phrases")
    private List<String> quotedPhrases;

    @Column(name = "question_back")
    private String questionBack;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime createdAt;

    protected ReadingNote() {
    }

    public ReadingNote(
            Book book, String noteText, String chapterPlaced, Integer ownWordsPercentage,
            List<String> ideasConnected, List<String> quotedPhrases, String questionBack) {
        this.book = Objects.requireNonNull(book, "book must not be null");
        this.noteText = Objects.requireNonNull(noteText, "noteText must not be null");
        this.chapterPlaced = chapterPlaced;
        this.ownWordsPercentage = ownWordsPercentage;
        this.ideasConnected = ideasConnected;
        this.quotedPhrases = quotedPhrases;
        this.questionBack = questionBack;
    }

    public Long getId() {
        return id;
    }

    public Book getBook() {
        return book;
    }

    public String getNoteText() {
        return noteText;
    }

    public String getChapterPlaced() {
        return chapterPlaced;
    }

    public Integer getOwnWordsPercentage() {
        return ownWordsPercentage;
    }

    public List<String> getIdeasConnected() {
        return ideasConnected;
    }

    public List<String> getQuotedPhrases() {
        return quotedPhrases;
    }

    public String getQuestionBack() {
        return questionBack;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
