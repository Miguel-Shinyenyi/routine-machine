package com.routinemachine.core.repository;

import com.routinemachine.core.AbstractIntegrationTest;
import com.routinemachine.core.domain.CurrentReadingLog;
import com.routinemachine.core.domain.ReadingSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CurrentReadingLogRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private CurrentReadingLogRepository currentReadingLogRepository;

    @Test
    void latestReturnsTheMostRecentlyAddedRow() {
        currentReadingLogRepository.save(new CurrentReadingLog("Sapiens", ReadingSource.MANUAL));
        currentReadingLogRepository.save(new CurrentReadingLog("Homo Deus", ReadingSource.MANUAL));

        CurrentReadingLog latest = currentReadingLogRepository.findLatest().orElseThrow();

        assertThat(latest.getTitle()).isEqualTo("Homo Deus");
    }

    @Test
    void latestIsEmptyWhenNothingHasBeenLogged() {
        assertThat(currentReadingLogRepository.findLatest()).isEmpty();
    }
}
