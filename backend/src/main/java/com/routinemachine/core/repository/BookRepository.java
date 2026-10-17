package com.routinemachine.core.repository;

import com.routinemachine.core.domain.Book;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BookRepository extends JpaRepository<Book, Long> {

    List<Book> findAllByOrderByCreatedAtDesc();

    Optional<Book> findFirstByOrderByCreatedAtDescIdDesc();

    default Optional<Book> findCurrent() {
        return findFirstByOrderByCreatedAtDescIdDesc();
    }
}
