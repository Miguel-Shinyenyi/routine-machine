package com.routinemachine.core.api;

import com.routinemachine.core.domain.Book;
import com.routinemachine.core.domain.ReadingSource;
import com.routinemachine.core.domain.Book;
import com.routinemachine.core.repository.BookRepository;
import com.routinemachine.core.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@RestController
@RequestMapping("/api/books")
public class BookController {

    private final BookRepository bookRepository;
    private final UserRepository userRepository;

    public BookController(BookRepository bookRepository, UserRepository userRepository) {
        this.bookRepository = bookRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    public List<BookResponse> list(@RequestParam Optional<Long> userId) {
        // Per-user scoping (docs/backend.md): userId given = that user's books; omitted = only
        // the unowned (pre-account) ones, never another user's.
        List<Book> books = userId.isPresent()
                ? bookRepository.findByUserIdOrderByCreatedAtDesc(userId.get())
                : bookRepository.findByUserIsNullOrderByCreatedAtDesc();
        return books.stream().map(BookResponse::from).toList();
    }

    @GetMapping("/current")
    public BookResponse current(@RequestParam Optional<Long> userId) {
        Optional<Book> latest = userId.isPresent()
                ? bookRepository.findFirstByUserIdOrderByCreatedAtDescIdDesc(userId.get())
                : bookRepository.findFirstByUserIsNullOrderByCreatedAtDescIdDesc();
        return latest
                .map(BookResponse::from)
                .orElseThrow(() -> new NoSuchElementException("No book logged yet"));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookResponse create(@Valid @RequestBody BookRequest request) {
        Book book = new Book(
                request.title(), request.author(), request.currentChapter(), request.totalChapters(),
                ReadingSource.MANUAL);
        if (request.userId() != null) {
            book.assignUser(userRepository.findById(request.userId())
                    .orElseThrow(() -> new IllegalArgumentException("No user with id " + request.userId())));
        }
        return BookResponse.from(bookRepository.save(book));
    }
}
