package com.routinemachine.core.service;

import com.routinemachine.core.domain.User;
import com.routinemachine.core.repository.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

// Gives the OWNER role to the one account whose email is configured, and takes it from any other
// account that holds it, so there is exactly one owner. Idempotent. The address comes from the
// OWNER_EMAIL environment variable (see OwnerClaimRunner), never from code.
@Service
public class OwnerRoleService {

    private final JdbcTemplate jdbc;
    private final UserRepository userRepository;

    public OwnerRoleService(JdbcTemplate jdbc, UserRepository userRepository) {
        this.jdbc = jdbc;
        this.userRepository = userRepository;
    }

    @Transactional
    public void grantOwnerRoleTo(String ownerEmail) {
        User owner = userRepository.findByEmailIgnoreCase(ownerEmail)
                .orElseThrow(() -> new NoSuchElementException("No account with the configured owner email"));
        jdbc.update("update users set role = 'USER' where role = 'OWNER' and id <> ?", owner.getId());
        jdbc.update("update users set role = 'OWNER' where id = ? and role <> 'OWNER'", owner.getId());
    }
}
