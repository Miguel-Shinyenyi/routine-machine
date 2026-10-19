package com.routinemachine.core.repository;

import com.routinemachine.core.domain.Saving;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SavingRepository extends JpaRepository<Saving, Long> {

    Optional<Saving> findByUserIdAndLocalDate(Long userId, LocalDate localDate);

    List<Saving> findByUserIdAndLocalDateBetween(Long userId, LocalDate start, LocalDate end);

    List<Saving> findByUserId(Long userId);
}
