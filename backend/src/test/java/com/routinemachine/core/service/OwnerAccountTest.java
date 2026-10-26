package com.routinemachine.core.service;

import com.routinemachine.core.AbstractIntegrationTest;
import com.routinemachine.core.domain.User;
import com.routinemachine.core.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class OwnerAccountTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    private User saveUser(String email) {
        return userRepository.save(new User(email, "irrelevant-hash", null, null));
    }

    @Test
    void withNoOwnerConfiguredOnlyUnownedRowsAreIntegrationTargets() {
        User someone = saveUser("someone@example.com");
        OwnerAccount none = new OwnerAccount(userRepository, "");

        assertThat(none.owner()).isEmpty();
        assertThat(none.isIntegrationTarget(null)).isTrue();
        assertThat(none.isIntegrationTarget(someone)).isFalse();
    }

    @Test
    void theOwnersRowsAndUnownedRowsAreTargetsButNobodyElsesAre() {
        User owner = saveUser("owner@example.com");
        User other = saveUser("other@example.com");
        OwnerAccount account = new OwnerAccount(userRepository, "owner@example.com");

        assertThat(account.isIntegrationTarget(owner)).isTrue();
        assertThat(account.isIntegrationTarget(null)).isTrue();
        assertThat(account.isIntegrationTarget(other)).isFalse();
    }

    @Test
    void aConfiguredEmailWithNoAccountBehavesLikeNoOwner() {
        User someone = saveUser("someone2@example.com");
        OwnerAccount account = new OwnerAccount(userRepository, "nobody-registered@example.com");

        assertThat(account.owner()).isEmpty();
        assertThat(account.isIntegrationTarget(someone)).isFalse();
        assertThat(account.isIntegrationTarget(null)).isTrue();
    }

    @Test
    void theOwnerIsMatchedIgnoringCaseAndSurroundingSpaces() {
        User owner = saveUser("Owner3@Example.com");
        OwnerAccount account = new OwnerAccount(userRepository, "  owner3@example.COM ");

        assertThat(account.owner()).map(User::getId).contains(owner.getId());
        assertThat(account.isOwner(owner.getId())).isTrue();
        assertThat(account.isOwner(owner.getId() + 1000)).isFalse();
    }
}
