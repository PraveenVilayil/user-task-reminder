package com.neko.serviceImpl;

import com.neko.TestFixtures;
import com.neko.config.ModelMapperConfig;
import com.neko.dto.UserDto;
import com.neko.entity.User;
import com.neko.exceptions.ErrorCode.ErrorCode;
import com.neko.exceptions.UserTaskReminderException;
import com.neko.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(userRepository,
                new ModelMapperConfig().modelMapper(), TestFixtures.fixedClock());
    }

    @Test
    void createAssignsAnIdAndStampsBothTimestamps() {
        when(userRepository.existsByUserName("vivek")).thenReturn(false);
        when(userRepository.existsByEmail("vivek@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserDto request = new UserDto();
        request.setUserName("vivek");
        request.setEmail("vivek@example.com");
        request.setRoles(List.of("ROLE_USER"));

        UserDto created = userService.create(request);

        assertThat(created.getId()).isNotNull();
        assertThat(created.getCreatedDate()).isEqualTo(TestFixtures.NOW);
        assertThat(created.getUpdatedDate()).isEqualTo(TestFixtures.NOW);
        assertThat(created.getRoles()).containsExactly("ROLE_USER");
    }

    @Test
    void createRejectsADuplicateUserName() {
        when(userRepository.existsByUserName("vivek")).thenReturn(true);

        UserDto request = new UserDto();
        request.setUserName("vivek");
        request.setEmail("vivek@example.com");

        assertThatThrownBy(() -> userService.create(request))
                .isInstanceOf(UserTaskReminderException.class)
                .extracting(exception -> ((UserTaskReminderException) exception).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_USER_NAME);
        verify(userRepository, never()).save(any());
    }

    @Test
    void createRejectsADuplicateEmail() {
        when(userRepository.existsByUserName("vivek")).thenReturn(false);
        when(userRepository.existsByEmail("vivek@example.com")).thenReturn(true);

        UserDto request = new UserDto();
        request.setUserName("vivek");
        request.setEmail("vivek@example.com");

        assertThatThrownBy(() -> userService.create(request))
                .isInstanceOf(UserTaskReminderException.class)
                .extracting(exception -> ((UserTaskReminderException) exception).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_EMAIL);
    }

    @Test
    void getRaisesUserNotFoundForAnUnknownId() {
        UUID unknownId = UUID.randomUUID();
        when(userRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.get(unknownId))
                .isInstanceOf(UserTaskReminderException.class)
                .extracting(exception -> ((UserTaskReminderException) exception).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void updateAppliesOnlyTheFieldsPresentOnTheRequest() {
        User existing = TestFixtures.user();
        when(userRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserDto patch = new UserDto();
        patch.setFirstName("Vivek");

        UserDto updated = userService.update(existing.getId(), patch);

        assertThat(updated.getFirstName()).isEqualTo("Vivek");
        assertThat(updated.getLastName()).isEqualTo("User");
        assertThat(updated.getEmail()).isEqualTo(existing.getEmail());
    }

    @Test
    void updateRejectsAUserNameAlreadyTakenBySomeoneElse() {
        User existing = TestFixtures.user();
        when(userRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(userRepository.existsByUserName("taken")).thenReturn(true);

        UserDto patch = new UserDto();
        patch.setUserName("taken");

        assertThatThrownBy(() -> userService.update(existing.getId(), patch))
                .isInstanceOf(UserTaskReminderException.class)
                .extracting(exception -> ((UserTaskReminderException) exception).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_USER_NAME);
    }

    @Test
    void updateAllowsResubmittingTheSameUserName() {
        User existing = TestFixtures.user();
        when(userRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserDto patch = new UserDto();
        patch.setUserName(existing.getUserName());

        assertThat(userService.update(existing.getId(), patch).getUserName())
                .isEqualTo(existing.getUserName());
        verify(userRepository, never()).existsByUserName(any());
    }

    @Test
    void listMapsEveryUser() {
        when(userRepository.findAll()).thenReturn(List.of(TestFixtures.user(), TestFixtures.user()));

        assertThat(userService.list()).hasSize(2);
    }

    @Test
    void deleteRaisesUserNotFoundForAnUnknownId() {
        UUID unknownId = UUID.randomUUID();
        when(userRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.delete(unknownId))
                .isInstanceOf(UserTaskReminderException.class);
        verify(userRepository, never()).delete(any());
    }
}
