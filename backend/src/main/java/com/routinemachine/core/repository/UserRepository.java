package com.routinemachine.core.repository;

import com.routinemachine.core.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByGithubId(String githubId);

    boolean existsByEmailIgnoreCase(String email);
}
