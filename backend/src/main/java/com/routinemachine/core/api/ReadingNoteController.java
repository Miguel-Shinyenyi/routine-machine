package com.routinemachine.core.api;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.routinemachine.core.security.TokenUser;
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

    @Operation(summary = "List reading notes")
    @GetMapping
    public List<ReadingNoteResponse> list(@AuthenticationPrincipal Jwt jwt, @RequestParam Optional<Long> bookId) {
        // A note belongs to whoever owns its book. Asking for a specific book that is not yours
        // simply finds nothing.
        Long userId = TokenUser.idOf(jwt);
        List<ReadingNote> notes = bookId.map(id -> readingNoteRepository.findByBookIdAndOwnerWithBook(id, userId))
                .orElseGet(() -> readingNoteRepository.findByOwnerWithBook(userId));
        return notes.stream().map(ReadingNoteResponse::from).toList();
    }

    @Operation(summary = "Save a reading note")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReadingNoteResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ReadingNoteRequest request) {
        ReadingNote saved = readingNoteService.createNote(
                request.bookId(), TokenUser.idOf(jwt), request.noteText(), request.chapterPlaced(), request.ownWordsPercentage(),
                request.ideasConnected(), request.quotedPhrases(), request.questionBack());
        return ReadingNoteResponse.from(saved);
    }
}
