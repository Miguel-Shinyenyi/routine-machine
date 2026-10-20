package com.routinemachine.core.repository;

import com.routinemachine.core.domain.ReadingNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReadingNoteRepository extends JpaRepository<ReadingNote, Long> {

    // Plain JOIN FETCH, not LEFT JOIN FETCH: book is required on every reading note, unlike
    // RoutineItem's optional goal, so a matching row always exists.
    @Query("SELECT n FROM ReadingNote n JOIN FETCH n.book ORDER BY n.createdAt DESC")
    List<ReadingNote> findAllWithBookOrderByCreatedAtDesc();

    // Per-user scoping (docs/backend.md): a note belongs to whoever owns its book.
    @Query("SELECT n FROM ReadingNote n JOIN FETCH n.book b WHERE b.user.id = :userId ORDER BY n.createdAt DESC")
    List<ReadingNote> findByOwnerWithBook(@Param("userId") Long userId);

    @Query("SELECT n FROM ReadingNote n JOIN FETCH n.book b WHERE b.user IS NULL ORDER BY n.createdAt DESC")
    List<ReadingNote> findByNoOwnerWithBook();

    @Query("SELECT n FROM ReadingNote n JOIN FETCH n.book b WHERE b.id = :bookId AND b.user.id = :userId ORDER BY n.createdAt DESC")
    List<ReadingNote> findByBookIdAndOwnerWithBook(@Param("bookId") Long bookId, @Param("userId") Long userId);

    @Query("SELECT n FROM ReadingNote n JOIN FETCH n.book b WHERE b.id = :bookId AND b.user IS NULL ORDER BY n.createdAt DESC")
    List<ReadingNote> findByBookIdAndNoOwnerWithBook(@Param("bookId") Long bookId);

    @Query("SELECT n FROM ReadingNote n JOIN FETCH n.book WHERE n.book.id = :bookId ORDER BY n.createdAt DESC")
    List<ReadingNote> findByBookIdWithBookOrderByCreatedAtDesc(Long bookId);
}
