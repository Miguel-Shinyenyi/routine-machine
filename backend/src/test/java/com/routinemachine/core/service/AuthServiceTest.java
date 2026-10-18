package com.routinemachine.core.service;

import com.routinemachine.core.AbstractIntegrationTest;
import com.routinemachine.core.domain.User;
import com.routinemachine.core.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class AuthServiceTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private AuthService service() {
        return new AuthService(userRepository, passwordEncoder);
    }

    @Test
    void registeringCreatesAUserWithAHashedPassword() {
        User user = service().register("reader@example.com", "a-strong-password");

        assertThat(user.getEmail()).isEqualTo("reader@example.com");
        assertThat(user.getPasswordHash()).isNotEqualTo("a-strong-password");
        assertThat(passwordEncoder.matches("a-strong-password", user.getPasswordHash())).isTrue();
    }

    @Test
    void registeringWithAnAlreadyUsedEmailFails() {
        service().register("reader@example.com", "a-strong-password");

        assertThatThrownBy(() -> service().register("reader@example.com", "a-different-password"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void loggingInWithTheCorrectPasswordReturnsTheUser() {
        service().register("reader@example.com", "a-strong-password");

        Optional<User> result = service().login("reader@example.com", "a-strong-password");

        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("reader@example.com");
    }

    @Test
    void loggingInWithTheWrongPasswordFails() {
        service().register("reader@example.com", "a-strong-password");

        assertThat(service().login("reader@example.com", "the-wrong-password")).isEmpty();
    }

    @Test
    void loggingInWithAnUnknownEmailFails() {
        assertThat(service().login("nobody@example.com", "whatever")).isEmpty();
    }

    @Test
    void loggingInAGithubOnlyAccountWithAPasswordFails() {
        service().findOrCreateFromGithub("gh-123", "reader@example.com", "Reader");

        assertThat(service().login("reader@example.com", "any-password")).isEmpty();
    }

    @Test
    void findOrCreateFromGithubCreatesANewAccountTheFirstTime() {
        User user = service().findOrCreateFromGithub("gh-123", "reader@example.com", "Reader");

        assertThat(user.getGithubId()).isEqualTo("gh-123");
        assertThat(user.getEmail()).isEqualTo("reader@example.com");
        assertThat(user.getPasswordHash()).isNull();
    }

    @Test
    void findOrCreateFromGithubReturnsTheSameAccountOnARepeatLogin() {
        var authService = service();
        User first = authService.findOrCreateFromGithub("gh-123", "reader@example.com", "Reader");

        User second = authService.findOrCreateFromGithub("gh-123", "reader@example.com", "Reader");

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(userRepository.findAll()).hasSize(1);
    }

    @Test
    void findOrCreateFromGithubLinksToAnExistingPasswordAccountWithTheSameEmail() {
        User passwordAccount = service().register("reader@example.com", "a-strong-password");

        User linked = service().findOrCreateFromGithub("gh-123", "reader@example.com", "Reader");

        assertThat(linked.getId()).isEqualTo(passwordAccount.getId());
        assertThat(linked.getGithubId()).isEqualTo("gh-123");
        assertThat(userRepository.findAll()).hasSize(1);
    }
}
