package com.neko.controller;

import com.neko.dto.NotificationDto;
import com.neko.enums.Channel;
import com.neko.enums.DeliveryStatus;
import com.neko.exceptions.ErrorCode.ErrorCode;
import com.neko.exceptions.UserTaskReminderException;
import com.neko.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
class NotificationControllerTest {

    private static final String BASE = "/taskManagement/api/v1/notification";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    private UUID notificationId;
    private UUID userId;
    private NotificationDto notification;

    @BeforeEach
    void setUp() {
        notificationId = UUID.randomUUID();
        userId = UUID.randomUUID();
        notification = new NotificationDto();
        notification.setId(notificationId);
        notification.setUserId(userId);
        notification.setMessage("Your task is due");
        notification.setSeen(false);
        notification.setChannel(Channel.WEB);
        notification.setDeliveryStatus(DeliveryStatus.DELIVERED);
        notification.setDeliveryAttempts(1);
    }

    @Test
    void createReturns201WithTheResultingDeliveryStatus() throws Exception {
        when(notificationService.create(any(NotificationDto.class))).thenReturn(notification);

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + userId + "\",\"message\":\"Your task is due\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.deliveryStatus").value("DELIVERED"))
                .andExpect(jsonPath("$.deliveryAttempts").value(1));
    }

    @Test
    void createReturns400WhenTheRecipientAndMessageAreMissing() throws Exception {
        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.getCode()))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("message"))
                .andExpect(jsonPath("$.fieldErrors[1].field").value("userId"));
    }

    @Test
    void createReturns404WhenTheRecipientDoesNotExist() throws Exception {
        when(notificationService.create(any(NotificationDto.class)))
                .thenThrow(new UserTaskReminderException(ErrorCode.USER_NOT_FOUND, userId));

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + userId + "\",\"message\":\"hi\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("UTR-002"));
    }

    @Test
    void getReturns404AndTheNotificationNotFoundCode() throws Exception {
        when(notificationService.getById(notificationId))
                .thenThrow(new UserTaskReminderException(ErrorCode.NOTIFICATION_NOT_FOUND, notificationId));

        mockMvc.perform(get(BASE + "/" + notificationId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("UTR-003"));
    }

    @Test
    void listReturns200() throws Exception {
        when(notificationService.get()).thenReturn(List.of(notification));

        mockMvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void patchMarksANotificationSeen() throws Exception {
        notification.setSeen(true);
        when(notificationService.update(any(UUID.class), any(NotificationDto.class))).thenReturn(notification);

        mockMvc.perform(patch(BASE + "/" + notificationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seen\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seen").value(true));
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete(BASE + "/" + notificationId))
                .andExpect(status().isNoContent());

        verify(notificationService).delete(notificationId);
    }
}
