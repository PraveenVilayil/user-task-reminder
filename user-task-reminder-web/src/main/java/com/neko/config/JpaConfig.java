package com.neko.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Points JPA at the model and db modules.
 *
 * <p>Deliberately a separate configuration rather than annotations on the
 * application class: on the application class these would force an
 * EntityManagerFactory into every sliced test, including {@code @WebMvcTest},
 * which has no datasource. Here they are picked up by the full application and
 * skipped by the web slice.</p>
 */
@Configuration
@EntityScan("com.neko.entity")
@EnableJpaRepositories("com.neko.repositories")
public class JpaConfig {
}
