package com.neko;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Bootstrap class for the db module tests.
 *
 * <p>The persistence module has no application of its own, but {@code @DataJpaTest}
 * needs a {@code @SpringBootConfiguration} to anchor its context. This one wires
 * up exactly the entities and repositories the module owns, and nothing else.</p>
 */
@SpringBootApplication
@EntityScan("com.neko.entity")
@EnableJpaRepositories("com.neko.repositories")
public class TestJpaApplication {
}
