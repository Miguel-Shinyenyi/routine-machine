package com.routinemachine.core;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

// Subclasses use different Spring test slices (@DataJpaTest for repositories, @SpringBootTest
// for the API layer), each keyed to its own cached ApplicationContext. @DirtiesContext here
// stops a later test class from reusing a cached context whose DataSource points at an earlier,
// now-dead container's port once Testcontainers restarts the static POSTGRES field per class.
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"));
}
