package com.routinemachine.core;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

// Subclasses use different Spring test slices (@DataJpaTest for repositories, @SpringBootTest
// for the API layer), each keyed to its own cached ApplicationContext. @DirtiesContext here
// stops a later test class from reusing a cached context whose DataSource points at an earlier,
// now-dead container's port once Testcontainers restarts the static POSTGRES field per class.
// hub-sync.enabled=false, wakatime-sync.enabled=false, activitywatch-sync.enabled=false, and
// github-sync.enabled=false stop each sync's @Scheduled job from firing in any @SpringBootTest
// context and making real outbound calls during the suite; tests of each sync's logic call it
// directly instead of waiting on the scheduler. rescuetime-sync.enabled is already false by
// default (it's an opt-in feature, unlike the other three), listed here anyway so this stays
// true even if that default ever changes.
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "hub-sync.enabled=false", "wakatime-sync.enabled=false", "activitywatch-sync.enabled=false",
        "github-sync.enabled=false", "rescuetime-sync.enabled=false"})
public abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"));
}
