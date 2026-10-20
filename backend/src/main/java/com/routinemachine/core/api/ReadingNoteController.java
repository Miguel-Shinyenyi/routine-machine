package com.routinemachine.core.api;

import com.routinemachine.core.domain.ReadingNote;
import com.routinemachine.core.repository.ReadingNoteRepository;
import com.routinemachine.core.service.ReadingNoteService;
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
    private final ReadingNoteService readingNoteService;

    public ReadingNoteController(ReadingNoteRepository readingNoteRepository, ReadingNoteService readingNoteService) {
        this.readingNoteRepository = readingNoteRepository;
        this.readingNoteService = readingNoteService;
    }

    @GetMapping
    public List<ReadingNoteResponse> list(@RequestParam Optional<Long> bookId, @RequestParam Optional<Long> userId) {
        // Per-user scoping (docs/backend.md): a note belongs to whoever owns its book. userId
        // given = that user's notes; omitted = only notes on unowned books. Asking for a
        // specific book that is not yours simply finds nothing.
        List<ReadingNote> notes;
        if (userId.isPresent()) {
            notes = bookId.map(id -> readingNoteRepository.findByBookIdAndOwnerWithBook(id, userId.get()))
                    .orElseGet(() -> readingNoteRepository.findByOwnerWithBook(userId.get()));
        } else {
            notes = bookId.map(readingNoteRepository::findByBookIdAndNoOwnerWithBook)
                    .orElseGet(readingNoteRepository::findByNoOwnerWithBook);
        }
        return notes.stream().map(ReadingNoteResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReadingNoteResponse create(@Valid @RequestBody ReadingNoteRequest request) {
        ReadingNote saved = readingNoteService.createNote(
                request.bookId(), request.userId(), request.noteText(), request.chapterPlaced(), request.ownWordsPercentage(),
                request.ideasConnected(), request.quotedPhrases(), request.questionBack());
        return ReadingNoteResponse.from(saved);
    }
}
