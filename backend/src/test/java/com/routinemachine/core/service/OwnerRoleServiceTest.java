package com.routinemachine.core.service;

import com.routinemachine.core.AbstractIntegrationTest;
import com.routinemachine.core.domain.User;
import com.routinemachine.core.domain.UserRole;
import com.routinemachine.core.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Access control, part 4: the owner role. Set at startup from the OWNER_EMAIL environment
// variable, never hard-coded, and held by exactly one account.
@DataJpaTest
class OwnerRoleServiceTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private UserRepository userRepository;

    private OwnerRoleService service() {
        return new OwnerRoleService(jdbc, userRepository);
    }

    private long saveUser(String email) {
        return userRepository.save(new User(email, "irrelevant-hash", null, null)).getId();
    }

    private String roleOf(long userId) {
        return jdbc.queryForObject("select role from users where id = ?", String.class, userId);
    }

    @Test
    void everyNewAccountStartsWithTheOrdinaryRole() {
        long id = saveUser("role-new@example.com");

        assertThat(roleOf(id)).isEqualTo("USER");
        assertThat(userRepository.findById(id).orElseThrow().getRole()).isEqualTo(UserRole.USER);
    }

    @Test
    void grantsTheOwnerRoleToTheAccountWithTheConfiguredEmail() {
        long owner = saveUser("role-owner@example.com");
        long other = saveUser("role-other@example.com");

        service().grantOwnerRoleTo("Role-Owner@Example.com");

        assertThat(roleOf(owner)).isEqualTo("OWNER");
        assertThat(roleOf(other)).isEqualTo("USER");
    }

    @Test
    void runningTwiceChangesNothingMore() {
        long owner = saveUser("role-twice@example.com");

        service().grantOwnerRoleTo("role-twice@example.com");
        service().grantOwnerRoleTo("role-twice@example.com");

        assertThat(roleOf(owner)).isEqualTo("OWNER");
    }

    @Test
    void whenTheConfiguredEmailChangesThePreviousOwnerLosesTheRole() {
        long first = saveUser("role-first@example.com");
        long second = saveUser("role-second@example.com");

        service().grantOwnerRoleTo("role-first@example.com");
        service().grantOwnerRoleTo("role-second@example.com");

        assertThat(roleOf(first)).isEqualTo("USER");
        assertThat(roleOf(second)).isEqualTo("OWNER");
        assertThat(jdbc.queryForObject("select count(*) from users where role = 'OWNER'", Long.class)).isEqualTo(1L);
    }

    @Test
    void anEmailWithNoAccountChangesNothingAndSaysSo() {
        long owner = saveUser("role-kept@example.com");
        service().grantOwnerRoleTo("role-kept@example.com");

        assertThatThrownBy(() -> service().grantOwnerRoleTo("nobody-has-this@example.com"))
                .isInstanceOf(java.util.NoSuchElementException.class);

        assertThat(roleOf(owner)).isEqualTo("OWNER");
    }
}
