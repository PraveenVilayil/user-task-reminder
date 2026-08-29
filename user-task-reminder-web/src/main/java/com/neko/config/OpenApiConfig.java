package com.neko.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI document metadata. The UI is served by springdoc at
 * {@code /swagger-ui.html} and the raw document at {@code /v3/api-docs}.
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private String serverPort;

    @Bean
    public OpenAPI userTaskReminderOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("User Task Reminder API")
                        .version("v1")
                        .description("""
                                REST API for tasks, users, reminders, notifications and audit trails.

                                Reminders are either one-time (a due date) or recurring (a Spring cron
                                expression). When a reminder fires, a notification is created and
                                dispatched over its channel.""")
                        .contact(new Contact().name("user-task-reminder contributors"))
                        .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")))
                .servers(List.of(new Server()
                        .url("http://localhost:" + serverPort)
                        .description("Local development")));
    }
}
