package com.neko.serviceImpl;

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
import com.neko.service.NotificationService;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final NotificationDispatcher dispatcher;
    private final NotificationProperties properties;
    private final ModelMapper mapper;
    private final Clock clock;

    public NotificationServiceImpl(NotificationRepository notificationRepository,
                                   UserRepository userRepository,
                                   TaskRepository taskRepository,
                                   NotificationDispatcher dispatcher,
                                   NotificationProperties properties,
                                   ModelMapper mapper,
                                   Clock clock) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.taskRepository = taskRepository;
        this.dispatcher = dispatcher;
        this.properties = properties;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public NotificationDto create(NotificationDto notification) {
        Notification entity = new Notification();
        entity.setId(UUID.randomUUID());
        entity.setCreatedDate(LocalDateTime.now(clock));
        entity.setMessage(notification.getMessage());
        entity.setSeen(notification.getSeen() != null && notification.getSeen());
        entity.setChannel(notification.getChannel() == null
                ? properties.getDefaultChannel()
                : notification.getChannel());
        entity.setUser(resolveUser(notification.getUserId()));
        entity.setTask(resolveTask(notification.getTaskId()));
        entity.setDeliveryStatus(DeliveryStatus.PENDING);

        dispatcher.dispatch(entity);
        Notification saved = notificationRepository.save(entity);
        return mapper.map(saved, NotificationDto.class);
    }

    @Override
    @Transactional
    public NotificationDto raise(UUID taskId, UUID userId, String message, Channel channel) {
        NotificationDto request = new NotificationDto();
        request.setTaskId(taskId);
        request.setUserId(userId);
        request.setMessage(message);
        request.setChannel(channel);
        return create(request);
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationDto getById(UUID id) {
        return mapper.map(findOrThrow(id), NotificationDto.class);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationDto> get() {
        return notificationRepository.findAll().stream()
                .map(notification -> mapper.map(notification, NotificationDto.class))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationDto> getByUser(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new UserTaskReminderException(ErrorCode.USER_NOT_FOUND, userId);
        }
        return notificationRepository.findByUserIdOrderByCreatedDateDesc(userId).stream()
                .map(notification -> mapper.map(notification, NotificationDto.class))
                .toList();
    }

    /**
     * Partial update. Only the client-owned fields are writable; delivery
     * bookkeeping (status, attempts, timestamps) belongs to the dispatcher.
     */
    @Override
    @Transactional
    public NotificationDto update(UUID id, NotificationDto notification) {
        Notification existing = findOrThrow(id);

        if (notification.getMessage() != null) {
            existing.setMessage(notification.getMessage());
        }
        if (notification.getSeen() != null) {
            existing.setSeen(notification.getSeen());
        }
        if (notification.getChannel() != null) {
            existing.setChannel(notification.getChannel());
        }
        if (notification.getUserId() != null) {
            existing.setUser(resolveUser(notification.getUserId()));
        }
        if (notification.getTaskId() != null) {
            existing.setTask(resolveTask(notification.getTaskId()));
        }

        return mapper.map(notificationRepository.save(existing), NotificationDto.class);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        notificationRepository.delete(findOrThrow(id));
        log.info("Deleted notification {}", id);
    }

    @Override
    @Transactional
    public int retryFailedDeliveries() {
        List<Notification> backlog = notificationRepository
                .findByDeliveryStatusInAndDeliveryAttemptsLessThan(
                        List.of(DeliveryStatus.PENDING, DeliveryStatus.FAILED),
                        properties.getMaxDeliveryAttempts());
        if (backlog.isEmpty()) {
            return 0;
        }

        int delivered = 0;
        for (Notification notification : backlog) {
            if (dispatcher.dispatch(notification)) {
                delivered++;
            }
        }
        notificationRepository.saveAll(backlog);
        log.info("Retried {} notification deliveries, {} succeeded", backlog.size(), delivered);
        return backlog.size();
    }

    private Notification findOrThrow(UUID id) {
        return notificationRepository.findById(id)
                .orElseThrow(() -> new UserTaskReminderException(ErrorCode.NOTIFICATION_NOT_FOUND, id));
    }

    private User resolveUser(UUID userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserTaskReminderException(ErrorCode.USER_NOT_FOUND, userId));
    }

    private Task resolveTask(UUID taskId) {
        if (taskId == null) {
            return null;
        }
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new UserTaskReminderException(ErrorCode.TASK_NOT_FOUND, taskId));
    }
}
