package com.routinemachine.core.service;

import com.routinemachine.core.domain.User;
import com.routinemachine.core.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User register(String email, String password) {
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("Email is already registered: " + email);
        }
        return userRepository.save(new User(email, passwordEncoder.encode(password), null, null));
    }

    // Empty, not an exception, on any failure: unknown email, a GitHub-only account with no
    // password set, or a wrong password all look the same to the caller, same reasoning
    // login endpoints never say which part of "email or password" was wrong.
    public Optional<User> login(String email, String password) {
        return userRepository.findByEmailIgnoreCase(email)
                .filter(user -> user.getPasswordHash() != null)
                .filter(user -> passwordEncoder.matches(password, user.getPasswordHash()));
    }

    // Trusts that the caller has already verified this GitHub identity (Next.js's own OAuth
    // flow does that); this just finds or creates the matching backend account. Protected by
    // a service-to-service token at the controller layer, not by anything in here — see
    // AuthController and docs/backend.md's decisions log.
    @Transactional
    public User findOrCreateFromGithub(String githubId, String email, String displayName) {
        Optional<User> byGithubId = userRepository.findByGithubId(githubId);
        if (byGithubId.isPresent()) {
            return byGithubId.get();
        }

        Optional<User> byEmail = userRepository.findByEmailIgnoreCase(email);
        if (byEmail.isPresent()) {
            User existing = byEmail.get();
            existing.linkGithubId(githubId);
            return existing;
        }

        return userRepository.save(new User(email, null, githubId, displayName));
    }
}
