package com.neko.integration;

import com.neko.notification.NotificationDispatcher;
import com.neko.service.AuditService;
import com.neko.service.NotificationService;
import com.neko.service.ReminderService;
import com.neko.service.TaskService;
import com.neko.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boots the whole reactor against H2: every bean wires, Flyway applies the
 * baseline, and the OpenAPI document is actually generated.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApplicationContextIT {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private org.flywaydb.core.Flyway flyway;

    @Test
    void everyServiceIsWired() {
        assertThat(context.getBean(TaskService.class)).isNotNull();
        assertThat(context.getBean(UserService.class)).isNotNull();
        assertThat(context.getBean(NotificationService.class)).isNotNull();
        assertThat(context.getBean(ReminderService.class)).isNotNull();
        assertThat(context.getBean(AuditService.class)).isNotNull();
    }

    @Test
    void flywayHasAppliedTheBaselineMigration() {
        assertThat(flyway.info().applied())
                .isNotEmpty()
                .anyMatch(migration -> "1".equals(migration.getVersion().getVersion()));
    }

    @Test
    void theInAppChannelIsAvailableAndEmailIsNotByDefault() {
        NotificationDispatcher dispatcher = context.getBean(NotificationDispatcher.class);

        assertThat(dispatcher.supportedChannels()).containsExactly(com.neko.enums.Channel.WEB);
    }

    @Test
    void schedulersAreDisabledUnderTheTestProfile() {
        assertThat(context.getBeanNamesForType(com.neko.scheduling.ReminderScheduler.class)).isEmpty();
        assertThat(context.getBeanNamesForType(com.neko.scheduling.MaintenanceScheduler.class)).isEmpty();
    }

    @Test
    void theOpenApiDocumentIsServedAndDescribesEveryResource() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("User Task Reminder API"))
                .andExpect(jsonPath("$.paths['/taskManagement/api/v1/task']").exists())
                .andExpect(jsonPath("$.paths['/taskManagement/api/v1/task/search']").exists())
                .andExpect(jsonPath("$.paths['/taskManagement/api/v1/user']").exists())
                .andExpect(jsonPath("$.paths['/taskManagement/api/v1/reminder']").exists())
                .andExpect(jsonPath("$.paths['/taskManagement/api/v1/notification']").exists())
                .andExpect(jsonPath("$.paths['/taskManagement/api/v1/task/{id}/audit']").exists());
    }

    @Test
    void swaggerUiIsReachableAtTheAdvertisedPath() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void theHealthEndpointIsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
