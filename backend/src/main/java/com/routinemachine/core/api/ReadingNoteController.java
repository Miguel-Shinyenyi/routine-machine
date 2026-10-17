package com.routinemachine.core.api;

import com.routinemachine.core.domain.Book;
import com.routinemachine.core.domain.ReadingNote;
import com.routinemachine.core.repository.BookRepository;
import com.routinemachine.core.repository.ReadingNoteRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/reading-notes")
public class ReadingNoteController {

    private final ReadingNoteRepository readingNoteRepository;
    private final BookRepository bookRepository;

    public ReadingNoteController(ReadingNoteRepository readingNoteRepository, BookRepository bookRepository) {
        this.readingNoteRepository = readingNoteRepository;
        this.bookRepository = bookRepository;
    }

    @GetMapping
    public List<ReadingNoteResponse> list(@RequestParam Optional<Long> bookId) {
        List<ReadingNote> notes = bookId
                .map(readingNoteRepository::findByBookIdWithBookOrderByCreatedAtDesc)
                .orElseGet(readingNoteRepository::findAllWithBookOrderByCreatedAtDesc);
        return notes.stream().map(ReadingNoteResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReadingNoteResponse create(@Valid @RequestBody ReadingNoteRequest request) {
        Book book = bookRepository.findById(request.bookId())
                .orElseThrow(() -> new IllegalArgumentException("No book with id " + request.bookId()));
        ReadingNote saved = readingNoteRepository.save(new ReadingNote(
                book, request.noteText(), request.chapterPlaced(), request.ownWordsPercentage(),
                request.ideasConnected(), request.quotedPhrases(), request.questionBack()));
        return ReadingNoteResponse.from(saved);
    }
}
