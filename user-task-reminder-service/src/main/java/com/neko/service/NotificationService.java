package com.neko.service;

import com.neko.dto.NotificationDto;
import com.neko.enums.Channel;

import java.util.List;
import java.util.UUID;

public interface NotificationService {

    NotificationDto create(NotificationDto notificationDto);

    List<NotificationDto> get();

    NotificationDto getById(UUID id);

    List<NotificationDto> getByUser(UUID userId);

    NotificationDto update(UUID id, NotificationDto notificationDto);

    void delete(UUID id);

    /**
     * Raises a notification from inside the application (reminder fired,
     * task overdue) rather than from an inbound API call.
     *
     * @param taskId  task the notification refers to, may be null
     * @param userId  recipient, may be null when the task has no owner
     * @param message body text
     * @param channel delivery channel; WEB is used when null
     */
    NotificationDto raise(UUID taskId, UUID userId, String message, Channel channel);

    /**
     * Re-dispatches notifications left PENDING or FAILED, up to the configured
     * attempt limit.
     *
     * @return the number of notifications re-dispatched
     */
    int retryFailedDeliveries();
}
