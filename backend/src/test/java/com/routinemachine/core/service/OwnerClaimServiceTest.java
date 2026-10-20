package com.routinemachine.core.service;

import com.routinemachine.core.AbstractIntegrationTest;
import com.routinemachine.core.domain.User;
import com.routinemachine.core.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class OwnerClaimServiceTest extends AbstractIntegrationTest {

    private static final java.util.List<String> PERSONAL_TABLES = java.util.List.of(
            "goals", "routine_items", "daily_logs", "schedule_templates", "tasks", "books", "current_reading_log");

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private UserRepository userRepository;

    private OwnerClaimService service() {
        return new OwnerClaimService(jdbc, userRepository);
    }

    private User saveUser(String email) {
        return userRepository.save(new User(email, "irrelevant-hash", null, null));
    }

    private long unownedCount(String table) {
        return jdbc.queryForObject("select count(*) from " + table + " where user_id is null", Long.class);
    }

    // One unowned row in every personal table, on top of whatever the seed migrations left.
    private void seedUnownedRows() {
        jdbc.update("insert into goals (name) values ('claim-test goal')");
        jdbc.update("insert into routine_items (name) values ('claim-test routine')");
        jdbc.update("insert into daily_logs (log_date, routine_item_id, source) "
                + "values (current_date, (select id from routine_items where name = 'claim-test routine'), 'manual')");
        jdbc.update("insert into schedule_templates (day_of_week, target_type, target_duration_minutes, sort_order, label) "
                + "values ('MONDAY', 'LEARNING_SLOT', 30, 99, 'claim-test slot')");
        jdbc.update("insert into tasks (task_date, label) values (current_date, 'claim-test task')");
        jdbc.update("insert into books (title) values ('claim-test book')");
        jdbc.update("insert into current_reading_log (title) values ('claim-test reading')");
    }

    @Test
    void givesEveryUnownedPersonalRowToTheOwner() {
        User owner = saveUser("owner@example.com");
        seedUnownedRows();

        Map<String, Integer> claimed = service().claimUnownedDataFor("owner@example.com");

        for (String table : PERSONAL_TABLES) {
            assertThat(claimed.get(table)).as(table).isGreaterThanOrEqualTo(1);
            assertThat(unownedCount(table)).as("unowned rows left in " + table).isZero();
        }
        assertThat(jdbc.queryForObject("select user_id from books where title = 'claim-test book'", Long.class))
                .isEqualTo(owner.getId());
        assertThat(jdbc.queryForObject("select user_id from tasks where label = 'claim-test task'", Long.class))
                .isEqualTo(owner.getId());
    }

    @Test
    void leavesRowsThatBelongToSomeoneElseAlone() {
        User owner = saveUser("owner@example.com");
        User other = saveUser("other@example.com");
        jdbc.update("insert into books (title, user_id) values ('someone elses book', ?)", other.getId());

        service().claimUnownedDataFor("owner@example.com");

        assertThat(jdbc.queryForObject("select user_id from books where title = 'someone elses book'", Long.class))
                .isEqualTo(other.getId());
        assertThat(owner.getId()).isNotEqualTo(other.getId());
    }

    @Test
    void claimingTwiceClaimsNothingTheSecondTime() {
        saveUser("owner@example.com");
        seedUnownedRows();
        service().claimUnownedDataFor("owner@example.com");

        Map<String, Integer> second = service().claimUnownedDataFor("owner@example.com");

        assertThat(second.values()).allMatch(count -> count == 0);
    }

    @Test
    void matchesTheOwnerEmailIgnoringCase() {
        User owner = saveUser("Owner@Example.com");
        seedUnownedRows();

        service().claimUnownedDataFor("owner@example.COM");

        assertThat(jdbc.queryForObject("select user_id from books where title = 'claim-test book'", Long.class))
                .isEqualTo(owner.getId());
    }

    @Test
    void anUnknownOwnerEmailChangesNothingAndSaysSo() {
        seedUnownedRows();
        long before = unownedCount("books");

        assertThatThrownBy(() -> service().claimUnownedDataFor("nobody@example.com"))
                .isInstanceOf(NoSuchElementException.class);

        assertThat(unownedCount("books")).isEqualTo(before);
    }
}
