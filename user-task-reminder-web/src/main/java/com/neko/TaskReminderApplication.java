package com.neko;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point. The reactor splits entities, repositories and services across
 * modules, so component scanning is widened to the shared {@code com.neko} root;
 * JPA is pointed at the model and db modules by
 * {@link com.neko.config.JpaConfig}.
 */
@SpringBootApplication(scanBasePackages = "com.neko")
public class TaskReminderApplication {

    public static void main(String[] args) {
        SpringApplication.run(TaskReminderApplication.class, args);
    }
}
