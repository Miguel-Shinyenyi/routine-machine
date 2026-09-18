package com.routinemachine.core.repository;

import com.routinemachine.core.AbstractIntegrationTest;
import com.routinemachine.core.domain.RoutineItem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RoutineItemRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private RoutineItemRepository routineItemRepository;

    @Test
    void savesAndReloadsARoutineItem() {
        RoutineItem saved = routineItemRepository.save(new RoutineItem("journaling", "Daily journal entry"));

        RoutineItem found = routineItemRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getName()).isEqualTo("journaling");
        assertThat(found.getDescription()).isEqualTo("Daily journal entry");
        assertThat(found.getCreatedAt()).isNotNull();
    }
}
