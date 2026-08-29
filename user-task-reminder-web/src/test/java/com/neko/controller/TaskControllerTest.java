package com.neko.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neko.dto.AuditLogDto;
import com.neko.dto.PageResponse;
import com.neko.dto.TaskDto;
import com.neko.dto.TaskSearchCriteria;
import com.neko.enums.AuditAction;
import com.neko.enums.Priority;
import com.neko.enums.Status;
import com.neko.exceptions.ErrorCode.ErrorCode;
import com.neko.exceptions.UserTaskReminderException;
import com.neko.service.AuditService;
import com.neko.service.ReminderService;
import com.neko.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TaskController.class)
class TaskControllerTest {

    private static final String BASE = "/taskManagement/api/v1/task";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TaskService taskService;

    @MockitoBean
    private ReminderService reminderService;

    @MockitoBean
    private AuditService auditService;

    private UUID taskId;
    private UUID ownerId;
    private TaskDto task;

    @BeforeEach
    void setUp() {
        taskId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        task = new TaskDto();
        task.setId(taskId);
        task.setName("Ship the release notes");
        task.setCreatedById(ownerId);
        task.setStatus(Status.PENDING);
        task.setPriority(Priority.HIGH);
        task.setCreatedDate(LocalDateTime.of(2026, 3, 2, 9, 0));
    }

    @Test
    void createReturns201WithTheStoredTask() throws Exception {
        when(taskService.create(any(TaskDto.class))).thenReturn(task);

        TaskDto request = new TaskDto();
        request.setName("Ship the release notes");
        request.setCreatedById(ownerId);

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(taskId.toString()))
                .andExpect(jsonPath("$.name").value("Ship the release notes"));
    }

    @Test
    void createReturns400WithFieldErrorsWhenTheBodyIsInvalid() throws Exception {
        // Missing both the mandatory name and the owner id.
        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.getCode()))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("createdById"))
                .andExpect(jsonPath("$.fieldErrors[1].field").value("name"));
    }

    @Test
    void createReturns400WhenTheBodyIsNotParseableJson() throws Exception {
        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.MALFORMED_REQUEST.getCode()));
    }

    @Test
    void getReturns200ForAKnownTask() throws Exception {
        when(taskService.get(taskId)).thenReturn(task);

        mockMvc.perform(get(BASE + "/" + taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(taskId.toString()));
    }

    @Test
    void getReturns404AndTheTaskNotFoundCode() throws Exception {
        when(taskService.get(taskId))
                .thenThrow(new UserTaskReminderException(ErrorCode.TASK_NOT_FOUND, taskId));

        mockMvc.perform(get(BASE + "/" + taskId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("UTR-001"))
                .andExpect(jsonPath("$.message").value("Task with ID " + taskId + " Not Found"))
                .andExpect(jsonPath("$.path").value(BASE + "/" + taskId));
    }

    @Test
    void getReturns400ForAnIdThatIsNotAUuid() throws Exception {
        mockMvc.perform(get(BASE + "/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_PARAMETER.getCode()));
    }

    @Test
    void listReturns200WithEveryTask() throws Exception {
        when(taskService.list()).thenReturn(List.of(task));

        mockMvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(taskId.toString()));
    }

    @Test
    void updateReturns200NotCreated() throws Exception {
        when(taskService.update(eq(taskId), any(TaskDto.class))).thenReturn(task);

        mockMvc.perform(patch(BASE + "/" + taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"priority\":\"HIGH\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value("HIGH"));
    }

    @Test
    void updateReturns409WhenPrerequisitesAreStillOpen() throws Exception {
        UUID blockerId = UUID.randomUUID();
        when(taskService.update(eq(taskId), any(TaskDto.class))).thenThrow(
                new UserTaskReminderException(ErrorCode.TASK_PREREQUISITES_INCOMPLETE, taskId, blockerId));

        mockMvc.perform(patch(BASE + "/" + taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("UTR-402"));
    }

    @Test
    void searchReturnsThePaginationEnvelope() throws Exception {
        when(taskService.search(any(TaskSearchCriteria.class), any(Pageable.class)))
                .thenReturn(PageResponse.of(List.of(task), 0, 20, 1));

        mockMvc.perform(get(BASE + "/search")
                        .param("q", "release")
                        .param("status", "PENDING")
                        .param("sort", "dueDate,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void searchReturns400ForASortPropertyThatIsNotAllowed() throws Exception {
        when(taskService.search(any(TaskSearchCriteria.class), any(Pageable.class)))
                .thenThrow(new UserTaskReminderException(ErrorCode.INVALID_SORT_PROPERTY, "secret"));

        mockMvc.perform(get(BASE + "/search").param("sort", "secret,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UTR-106"));
    }

    @Test
    void searchReturns400ForAnUnknownEnumValue() throws Exception {
        mockMvc.perform(get(BASE + "/search").param("status", "NOT_A_STATUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_PARAMETER.getCode()));
    }

    @Test
    void auditTrailReturnsTheRecordedChanges() throws Exception {
        AuditLogDto entry = new AuditLogDto();
        entry.setId(UUID.randomUUID());
        entry.setTaskId(taskId);
        entry.setAction(AuditAction.STATUS_CHANGE);
        entry.setPerformedBy("vivek");
        entry.setFieldName("status");
        entry.setOldValue("PENDING");
        entry.setNewValue("COMPLETED");
        when(auditService.getTaskAuditTrail(taskId)).thenReturn(List.of(entry));

        mockMvc.perform(get(BASE + "/" + taskId + "/audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("STATUS_CHANGE"))
                .andExpect(jsonPath("$[0].oldValue").value("PENDING"))
                .andExpect(jsonPath("$[0].newValue").value("COMPLETED"));
    }

    @Test
    void remindersForATaskAreServedFromTheReminderService() throws Exception {
        when(reminderService.listByTask(taskId)).thenReturn(List.of());

        mockMvc.perform(get(BASE + "/" + taskId + "/reminder"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete(BASE + "/" + taskId))
                .andExpect(status().isNoContent());

        verify(taskService).delete(taskId);
    }

    @Test
    void deleteReturns404ForAnUnknownTask() throws Exception {
        org.mockito.Mockito.doThrow(new UserTaskReminderException(ErrorCode.TASK_NOT_FOUND, taskId))
                .when(taskService).delete(taskId);

        mockMvc.perform(delete(BASE + "/" + taskId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("UTR-001"));
    }
}
