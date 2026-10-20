package com.routinemachine.core.repository;

import com.routinemachine.core.domain.Book;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BookRepository extends JpaRepository<Book, Long> {

    List<Book> findAllByOrderByCreatedAtDesc();

    // Per-user scoping (docs/backend.md): a user's own books, or — userId omitted — only the
    // unowned ones. findCurrent() above stays global on purpose: hub-sync still uses it, and
    // moving hub-sync onto the owner account is part 5.
    List<Book> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Book> findByUserIsNullOrderByCreatedAtDesc();

    Optional<Book> findFirstByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    Optional<Book> findFirstByUserIsNullOrderByCreatedAtDescIdDesc();

    Optional<Book> findFirstByOrderByCreatedAtDescIdDesc();

    default Optional<Book> findCurrent() {
        return findFirstByOrderByCreatedAtDescIdDesc();
    }
}
