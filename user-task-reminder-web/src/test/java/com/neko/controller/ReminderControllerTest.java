package com.neko.controller;

import com.neko.dto.ReminderDto;
import com.neko.enums.Channel;
import com.neko.enums.ReminderStatus;
import com.neko.exceptions.ErrorCode.ErrorCode;
import com.neko.exceptions.UserTaskReminderException;
import com.neko.service.ReminderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
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

@WebMvcTest(ReminderController.class)
class ReminderControllerTest {

    private static final String BASE = "/taskManagement/api/v1/reminder";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReminderService reminderService;

    private UUID reminderId;
    private UUID taskId;
    private ReminderDto reminder;

    @BeforeEach
    void setUp() {
        reminderId = UUID.randomUUID();
        taskId = UUID.randomUUID();
        reminder = new ReminderDto();
        reminder.setId(reminderId);
        reminder.setTaskId(taskId);
        reminder.setCron("0 0 9 * * MON-FRI");
        reminder.setChannel(Channel.WEB);
        reminder.setStatus(ReminderStatus.SCHEDULED);
        reminder.setNextFireTime(LocalDateTime.of(2026, 3, 3, 9, 0));
        reminder.setFireCount(0);
    }

    @Test
    void createReturns201WithTheComputedNextFireTime() throws Exception {
        when(reminderService.create(any(ReminderDto.class))).thenReturn(reminder);

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskId\":\"" + taskId + "\",\"cron\":\"0 0 9 * * MON-FRI\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(reminderId.toString()))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.nextFireTime").value("2026-03-03T09:00:00"));
    }

    @Test
    void createReturns400WhenNeitherCronNorDueDateIsSupplied() throws Exception {
        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskId\":\"" + taskId + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.getCode()))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("scheduleSpecified"));
    }

    @Test
    void createReturns400WhenBothCronAndDueDateAreSupplied() throws Exception {
        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskId\":\"" + taskId + "\",\"cron\":\"0 0 9 * * *\","
                                + "\"dueDate\":\"2026-09-01T09:00:00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.getCode()));
    }

    @Test
    void createReturns400WhenTaskIdIsMissing() throws Exception {
        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cron\":\"0 0 9 * * *\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='taskId')]").exists());
    }

    @Test
    void createReturns400ForAnUnparseableCron() throws Exception {
        when(reminderService.create(any(ReminderDto.class)))
                .thenThrow(new UserTaskReminderException(ErrorCode.INVALID_CRON_EXPRESSION, "nope"));

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskId\":\"" + taskId + "\",\"cron\":\"nope\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UTR-103"));
    }

    @Test
    void createReturns404WhenTheTaskDoesNotExist() throws Exception {
        when(reminderService.create(any(ReminderDto.class)))
                .thenThrow(new UserTaskReminderException(ErrorCode.TASK_NOT_FOUND, taskId));

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskId\":\"" + taskId + "\",\"cron\":\"0 0 9 * * *\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("UTR-001"));
    }

    @Test
    void getReturns404AndTheReminderNotFoundCode() throws Exception {
        when(reminderService.get(reminderId))
                .thenThrow(new UserTaskReminderException(ErrorCode.REMINDER_NOT_FOUND, reminderId));

        mockMvc.perform(get(BASE + "/" + reminderId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("UTR-004"));
    }

    @Test
    void listReturns200() throws Exception {
        when(reminderService.list()).thenReturn(List.of(reminder));

        mockMvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void cancelReturns200WithTheCancelledReminder() throws Exception {
        ReminderDto cancelled = new ReminderDto();
        cancelled.setId(reminderId);
        cancelled.setTaskId(taskId);
        cancelled.setStatus(ReminderStatus.CANCELLED);
        when(reminderService.cancel(reminderId)).thenReturn(cancelled);

        mockMvc.perform(post(BASE + "/" + reminderId + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.nextFireTime").doesNotExist());
    }

    @Test
    void patchReturns200() throws Exception {
        when(reminderService.update(any(UUID.class), any(ReminderDto.class))).thenReturn(reminder);

        mockMvc.perform(patch(BASE + "/" + reminderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Stand-up\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete(BASE + "/" + reminderId))
                .andExpect(status().isNoContent());

        verify(reminderService).delete(reminderId);
    }
}
