package com.routinemachine.core.repository;

import com.routinemachine.core.domain.ReadingNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ReadingNoteRepository extends JpaRepository<ReadingNote, Long> {

    // Plain JOIN FETCH, not LEFT JOIN FETCH: book is required on every reading note, unlike
    // RoutineItem's optional goal, so a matching row always exists.
    @Query("SELECT n FROM ReadingNote n JOIN FETCH n.book ORDER BY n.createdAt DESC")
    List<ReadingNote> findAllWithBookOrderByCreatedAtDesc();

    @Query("SELECT n FROM ReadingNote n JOIN FETCH n.book WHERE n.book.id = :bookId ORDER BY n.createdAt DESC")
    List<ReadingNote> findByBookIdWithBookOrderByCreatedAtDesc(Long bookId);
}
