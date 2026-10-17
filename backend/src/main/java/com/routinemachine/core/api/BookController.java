package com.routinemachine.core.api;

import com.routinemachine.core.domain.Book;
import com.routinemachine.core.domain.ReadingSource;
import com.routinemachine.core.repository.BookRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/books")
public class BookController {

    private final BookRepository bookRepository;

    public BookController(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    @GetMapping
    public List<BookResponse> list() {
        return bookRepository.findAllByOrderByCreatedAtDesc().stream().map(BookResponse::from).toList();
    }

    @GetMapping("/current")
    public BookResponse current() {
        return bookRepository.findCurrent()
                .map(BookResponse::from)
                .orElseThrow(() -> new NoSuchElementException("No book logged yet"));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookResponse create(@Valid @RequestBody BookRequest request) {
        Book saved = bookRepository.save(new Book(
                request.title(), request.author(), request.currentChapter(), request.totalChapters(),
                ReadingSource.MANUAL));
        return BookResponse.from(saved);
    }
}
