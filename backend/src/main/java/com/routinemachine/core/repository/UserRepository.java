package com.routinemachine.core.repository;

import com.routinemachine.core.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByGithubId(String githubId);

    boolean existsByEmailIgnoreCase(String email);

    // The hillside's whole population, v1 and v2 alike — see docs/backend.md's decisions log
    // for why "cohort" and "open" both resolve to this same set until Phase 12 builds real
    // cohorts.
    List<User> findByHillsideOptInTrue();
}
