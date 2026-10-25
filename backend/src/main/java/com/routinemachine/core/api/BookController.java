package com.routinemachine.core.api;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.routinemachine.core.security.TokenUser;
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

    @Operation(summary = "List the signed-in person's books")
    @GetMapping
    public List<BookResponse> list(@AuthenticationPrincipal Jwt jwt) {
        List<Book> books = bookRepository.findByUserIdOrderByCreatedAtDesc(TokenUser.idOf(jwt));
        return books.stream().map(BookResponse::from).toList();
    }

    @Operation(summary = "Get the book being read now")
    @GetMapping("/current")
    public BookResponse current(@AuthenticationPrincipal Jwt jwt) {
        return bookRepository.findFirstByUserIdOrderByCreatedAtDescIdDesc(TokenUser.idOf(jwt))
                .map(BookResponse::from)
                .orElseThrow(() -> new NoSuchElementException("No book logged yet"));
    }

    @Operation(summary = "Start a book")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody BookRequest request) {
        Long userId = TokenUser.idOf(jwt);
        Book book = new Book(
                request.title(), request.author(), request.currentChapter(), request.totalChapters(),
                ReadingSource.MANUAL);
        book.assignUser(userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("No user with id " + userId)));
        return BookResponse.from(bookRepository.save(book));
    }
}
