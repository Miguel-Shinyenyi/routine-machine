package com.routinemachine.core.service;

import com.routinemachine.core.domain.User;
import com.routinemachine.core.repository.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

// Per-user scoping, part 1. Before accounts existed, this app held exactly one person's data,
// stored with a NULL user_id. Once every page is scoped to the signed-in user, that data would
// belong to nobody and vanish from view, so it is handed to the owner's account, once.
// Which account is the owner comes from an environment variable (OWNER_EMAIL), never from code.
// Only rows with a NULL user_id move; anything already owned stays put. Safe to run repeatedly.
@Service
public class OwnerClaimService {

    // Fixed list, concatenated into SQL below — never built from input.
    private static final List<String> PERSONAL_TABLES = List.of(
            "goals", "routine_items", "daily_logs", "schedule_templates", "tasks", "books", "current_reading_log");

    private final JdbcTemplate jdbc;
    private final UserRepository userRepository;

    public OwnerClaimService(JdbcTemplate jdbc, UserRepository userRepository) {
        this.jdbc = jdbc;
        this.userRepository = userRepository;
    }

    // All or nothing: if any table fails (for example the owner already has a goal with the same
    // name as an unowned one, which the (user_id, name) unique index forbids), nothing moves.
    @Transactional
    public Map<String, Integer> claimUnownedDataFor(String ownerEmail) {
        User owner = userRepository.findByEmailIgnoreCase(ownerEmail)
                .orElseThrow(() -> new NoSuchElementException("No account with the configured owner email"));

        Map<String, Integer> claimed = new LinkedHashMap<>();
        for (String table : PERSONAL_TABLES) {
            claimed.put(table, jdbc.update("update " + table + " set user_id = ? where user_id is null", owner.getId()));
        }
        return claimed;
    }
}
