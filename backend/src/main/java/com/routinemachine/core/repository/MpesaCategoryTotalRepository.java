package com.routinemachine.core.repository;

import com.routinemachine.core.domain.MpesaCategoryTotal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

public interface MpesaCategoryTotalRepository extends JpaRepository<MpesaCategoryTotal, Long> {

    List<MpesaCategoryTotal> findByUserIdAndStatementMonth(Long userId, LocalDate statementMonth);

    // Re-importing the same month replaces that category's total, not a second row —
    // "only category totals are kept," and a corrected or re-uploaded statement should
    // overwrite, not accumulate.
    @Modifying
    @Transactional
    @Query(
            value = "INSERT INTO mpesa_category_totals (user_id, statement_month, category, amount) "
                    + "VALUES (:userId, :statementMonth, :category, :amount) "
                    + "ON CONFLICT (user_id, statement_month, category) DO UPDATE SET amount = EXCLUDED.amount",
            nativeQuery = true)
    void upsert(
            @Param("userId") Long userId, @Param("statementMonth") LocalDate statementMonth,
            @Param("category") String category, @Param("amount") int amount);
}
