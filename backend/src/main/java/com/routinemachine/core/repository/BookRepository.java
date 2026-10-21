package com.routinemachine.core.repository;

import com.routinemachine.core.domain.Book;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BookRepository extends JpaRepository<Book, Long> {

    List<Book> findAllByOrderByCreatedAtDesc();

    // A user's own books (the token's user). findAllByOrderByCreatedAtDesc() above is everyone's and is
    // no longer used by the app: hub-sync looks at the owner's books and the unowned ones only.
    List<Book> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Book> findFirstByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    Optional<Book> findFirstByUserIsNullOrderByCreatedAtDescIdDesc();

    Optional<Book> findFirstByOrderByCreatedAtDescIdDesc();

    default Optional<Book> findCurrent() {
        return findFirstByOrderByCreatedAtDescIdDesc();
    }
}
