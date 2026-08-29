package com.neko.config;

import com.neko.notification.NotificationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

/**
 * Enables the scheduler and publishes the {@link Clock} every service takes its
 * time from.
 *
 * <p>Nothing in the domain calls {@code LocalDateTime.now()} directly; tests
 * replace this bean with a fixed clock and assert exact instants instead of
 * sleeping.</p>
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(NotificationProperties.class)
public class SchedulingConfig {

    @Bean
    @Primary
    public Clock systemClock() {
        return Clock.systemDefaultZone();
    }
}
