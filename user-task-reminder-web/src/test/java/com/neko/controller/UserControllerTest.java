package com.neko.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neko.dto.UserDto;
import com.neko.exceptions.ErrorCode.ErrorCode;
import com.neko.exceptions.UserTaskReminderException;
import com.neko.service.NotificationService;
import com.neko.service.UserService;
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

@WebMvcTest(UserController.class)
class UserControllerTest {

    private static final String BASE = "/taskManagement/api/v1/user";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private NotificationService notificationService;

    private UUID userId;
    private UserDto user;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = new UserDto();
        user.setId(userId);
        user.setUserName("vivek");
        user.setEmail("vivek@example.com");
        user.setRoles(List.of("ROLE_USER"));
    }

    @Test
    void createReturns201() throws Exception {
        when(userService.create(any(UserDto.class))).thenReturn(user);

        UserDto request = new UserDto();
        request.setUserName("vivek");
        request.setEmail("vivek@example.com");

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_USER"));
    }

    @Test
    void createReturns400WhenMandatoryFieldsAreMissing() throws Exception {
        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.getCode()))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("email"))
                .andExpect(jsonPath("$.fieldErrors[1].field").value("userName"));
    }

    @Test
    void createReturns400ForAMalformedEmail() throws Exception {
        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userName\":\"vivek\",\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("email"))
                .andExpect(jsonPath("$.fieldErrors[0].rejectedValue").value("not-an-email"));
    }

    @Test
    void createReturns409ForADuplicateUserName() throws Exception {
        when(userService.create(any(UserDto.class)))
                .thenThrow(new UserTaskReminderException(ErrorCode.DUPLICATE_USER_NAME, "vivek"));

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userName\":\"vivek\",\"email\":\"vivek@example.com\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("UTR-400"))
                .andExpect(jsonPath("$.message").value("User name vivek is already taken"));
    }

    @Test
    void getReturns404AndTheUserNotFoundCode() throws Exception {
        when(userService.get(userId))
                .thenThrow(new UserTaskReminderException(ErrorCode.USER_NOT_FOUND, userId));

        mockMvc.perform(get(BASE + "/" + userId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("UTR-002"));
    }

    @Test
    void listReturns200() throws Exception {
        when(userService.list()).thenReturn(List.of(user));

        mockMvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void patchAppliesAPartialUpdate() throws Exception {
        when(userService.update(any(UUID.class), any(UserDto.class))).thenReturn(user);

        mockMvc.perform(patch(BASE + "/" + userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Vivek\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userName").value("vivek"));
    }

    @Test
    void notificationsForAUserAreServedFromTheNotificationService() throws Exception {
        when(notificationService.getByUser(userId)).thenReturn(List.of());

        mockMvc.perform(get(BASE + "/" + userId + "/notification"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete(BASE + "/" + userId))
                .andExpect(status().isNoContent());

        verify(userService).delete(userId);
    }
}
