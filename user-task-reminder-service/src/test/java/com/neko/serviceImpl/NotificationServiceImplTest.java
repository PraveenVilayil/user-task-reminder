package com.neko.serviceImpl;

import com.neko.TestFixtures;
import com.neko.config.ModelMapperConfig;
import com.neko.dto.NotificationDto;
import com.neko.entity.Notification;
import com.neko.entity.Task;
import com.neko.entity.User;
import com.neko.enums.Channel;
import com.neko.enums.DeliveryStatus;
import com.neko.exceptions.ErrorCode.ErrorCode;
import com.neko.exceptions.UserTaskReminderException;
import com.neko.notification.NotificationDispatcher;
import com.neko.notification.NotificationProperties;
import com.neko.repositories.NotificationRepository;
import com.neko.repositories.TaskRepository;
import com.neko.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private NotificationDispatcher dispatcher;

    private NotificationProperties properties;
    private NotificationServiceImpl notificationService;
    private User recipient;
    private Task task;

    @BeforeEach
    void setUp() {
        properties = new NotificationProperties();
        notificationService = new NotificationServiceImpl(notificationRepository, userRepository, taskRepository,
                dispatcher, properties, new ModelMapperConfig().modelMapper(), TestFixtures.fixedClock());
        recipient = TestFixtures.user();
        task = TestFixtures.task(UUID.randomUUID(), recipient);
    }

    @Test
    void createResolvesTheRecipientAndTaskThenDispatches() {
        when(userRepository.findById(recipient.getId())).thenReturn(Optional.of(recipient));
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

        NotificationDto request = new NotificationDto();
        request.setUserId(recipient.getId());
        request.setTaskId(task.getId());
        request.setMessage("Your task is due");

        NotificationDto created = notificationService.create(request);

        assertThat(created.getId()).isNotNull();
        assertThat(created.getUserId()).isEqualTo(recipient.getId());
        assertThat(created.getTaskId()).isEqualTo(task.getId());
        assertThat(created.getCreatedDate()).isEqualTo(TestFixtures.NOW);
        assertThat(created.getChannel()).isEqualTo(Channel.WEB);
        verify(dispatcher).dispatch(any(Notification.class));
    }

    @Test
    void createDefaultsSeenToFalseAndStatusToPending() {
        when(userRepository.findById(recipient.getId())).thenReturn(Optional.of(recipient));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

        NotificationDto request = new NotificationDto();
        request.setUserId(recipient.getId());
        request.setMessage("Standalone message");

        notificationService.create(request);

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(saved.capture());
        assertThat(saved.getValue().getSeen()).isFalse();
        assertThat(saved.getValue().getDeliveryStatus()).isEqualTo(DeliveryStatus.PENDING);
    }

    @Test
    void createRejectsAnUnknownRecipient() {
        UUID unknownUser = UUID.randomUUID();
        when(userRepository.findById(unknownUser)).thenReturn(Optional.empty());

        NotificationDto request = new NotificationDto();
        request.setUserId(unknownUser);
        request.setMessage("Nobody home");

        assertThatThrownBy(() -> notificationService.create(request))
                .isInstanceOf(UserTaskReminderException.class)
                .extracting(exception -> ((UserTaskReminderException) exception).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void createRejectsAnUnknownTask() {
        UUID unknownTask = UUID.randomUUID();
        when(userRepository.findById(recipient.getId())).thenReturn(Optional.of(recipient));
        when(taskRepository.findById(unknownTask)).thenReturn(Optional.empty());

        NotificationDto request = new NotificationDto();
        request.setUserId(recipient.getId());
        request.setTaskId(unknownTask);
        request.setMessage("Ghost task");

        assertThatThrownBy(() -> notificationService.create(request))
                .isInstanceOf(UserTaskReminderException.class)
                .extracting(exception -> ((UserTaskReminderException) exception).getErrorCode())
                .isEqualTo(ErrorCode.TASK_NOT_FOUND);
    }

    @Test
    void raiseBuildsAndPersistsANotificationForInternalCallers() {
        when(userRepository.findById(recipient.getId())).thenReturn(Optional.of(recipient));
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

        NotificationDto raised = notificationService.raise(
                task.getId(), recipient.getId(), "Reminder fired", Channel.EMAIL);

        assertThat(raised.getMessage()).isEqualTo("Reminder fired");
        assertThat(raised.getChannel()).isEqualTo(Channel.EMAIL);
    }

    @Test
    void getByIdRaisesNotificationNotFoundForAnUnknownId() {
        UUID unknownId = UUID.randomUUID();
        when(notificationRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.getById(unknownId))
                .isInstanceOf(UserTaskReminderException.class)
                .extracting(exception -> ((UserTaskReminderException) exception).getErrorCode())
                .isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND);
    }

    @Test
    void updateAppliesOnlyTheClientOwnedFields() {
        Notification existing = TestFixtures.notification(recipient, task);
        existing.setDeliveryAttempts(2);
        existing.setDeliveryStatus(DeliveryStatus.FAILED);
        when(notificationRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

        NotificationDto patch = new NotificationDto();
        patch.setSeen(true);

        NotificationDto updated = notificationService.update(existing.getId(), patch);

        assertThat(updated.getSeen()).isTrue();
        assertThat(updated.getMessage()).isEqualTo("Something happened");
        // Delivery bookkeeping is untouched by a client patch.
        assertThat(updated.getDeliveryAttempts()).isEqualTo(2);
        assertThat(updated.getDeliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
    }

    @Test
    void getByUserRejectsAnUnknownUser() {
        UUID unknownUser = UUID.randomUUID();
        when(userRepository.existsById(unknownUser)).thenReturn(false);

        assertThatThrownBy(() -> notificationService.getByUser(unknownUser))
                .isInstanceOf(UserTaskReminderException.class)
                .extracting(exception -> ((UserTaskReminderException) exception).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void retryFailedDeliveriesRedispatchesTheBacklogAndSavesIt() {
        Notification pending = TestFixtures.notification(recipient, task);
        Notification failed = TestFixtures.notification(recipient, task);
        failed.setDeliveryStatus(DeliveryStatus.FAILED);
        when(notificationRepository.findByDeliveryStatusInAndDeliveryAttemptsLessThan(anyList(), anyInt()))
                .thenReturn(List.of(pending, failed));
        when(dispatcher.dispatch(any(Notification.class))).thenReturn(true, false);

        assertThat(notificationService.retryFailedDeliveries()).isEqualTo(2);

        verify(dispatcher, org.mockito.Mockito.times(2)).dispatch(any(Notification.class));
        verify(notificationRepository).saveAll(List.of(pending, failed));
    }

    @Test
    void retryFailedDeliveriesDoesNothingOnAnEmptyBacklog() {
        when(notificationRepository.findByDeliveryStatusInAndDeliveryAttemptsLessThan(anyList(), anyInt()))
                .thenReturn(List.of());

        assertThat(notificationService.retryFailedDeliveries()).isZero();

        verify(notificationRepository, never()).saveAll(any());
    }

    @Test
    void deleteRaisesNotificationNotFoundForAnUnknownId() {
        UUID unknownId = UUID.randomUUID();
        when(notificationRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.delete(unknownId))
                .isInstanceOf(UserTaskReminderException.class);
    }
}
