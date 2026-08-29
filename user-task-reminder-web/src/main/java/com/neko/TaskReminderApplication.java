package com.neko;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Entry point. The reactor splits entities, repositories and services across
 * modules, so component scanning is widened to the shared {@code com.neko} root
 * and JPA is pointed at the model and db modules explicitly.
 */
@SpringBootApplication(scanBasePackages = "com.neko")
@EntityScan("com.neko.entity")
@EnableJpaRepositories(basePackages = "com.neko.repositories")
public class TaskReminderApplication {

    public static void main(String[] args) {
        SpringApplication.run(TaskReminderApplication.class, args);
    }
}
