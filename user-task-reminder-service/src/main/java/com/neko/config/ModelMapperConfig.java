package com.neko.config;

import com.neko.dto.AuditLogDto;
import com.neko.dto.NotificationDto;
import com.neko.dto.ReminderDto;
import com.neko.dto.TaskDto;
import com.neko.dto.UserDto;
import com.neko.entity.AuditLog;
import com.neko.entity.Notification;
import com.neko.entity.Reminder;
import com.neko.entity.Task;
import com.neko.entity.User;
import com.neko.enums.Status;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Read-direction mapping only: entity to DTO.
 *
 * <p>The write direction is done explicitly in the services because it has to
 * resolve associations (owner, task, prerequisites) against the database, and a
 * reflective copy cannot do that safely.</p>
 */
@Configuration
public class ModelMapperConfig {

    @Bean
    public ModelMapper modelMapper() {
        ModelMapper mapper = new ModelMapper();
        mapper.getConfiguration()
                .setMatchingStrategy(MatchingStrategies.STANDARD)
                .setFieldMatchingEnabled(true)
                .setSkipNullEnabled(true)
                .setAmbiguityIgnored(true);

        configureTask(mapper);
        configureNotification(mapper);
        configureReminder(mapper);
        configureUser(mapper);
        mapper.createTypeMap(AuditLog.class, AuditLogDto.class);
        return mapper;
    }

    private void configureTask(ModelMapper mapper) {
        mapper.createTypeMap(Task.class, TaskDto.class)
                .addMappings(map -> {
                    map.skip(TaskDto::setCreatedById);
                    map.skip(TaskDto::setPrerequisiteIds);
                    map.skip(TaskDto::setOverdue);
                    map.skip(TaskDto::setAttachments);
                })
                .setPostConverter(context -> {
                    Task source = context.getSource();
                    TaskDto target = context.getDestination();
                    if (source.getCreatedBy() != null) {
                        target.setCreatedById(source.getCreatedBy().getId());
                    }
                    if (source.getPrerequisites() != null && !source.getPrerequisites().isEmpty()) {
                        target.setPrerequisiteIds(source.getPrerequisites().stream()
                                .map(Task::getId)
                                .filter(Objects::nonNull)
                                .toList());
                    }
                    target.setOverdue(source.getDueDate() != null
                            && source.getDueDate().isBefore(LocalDateTime.now())
                            && source.getStatus() != Status.COMPLETED);
                    if (source.getLabels() != null) {
                        target.setLabels(List.copyOf(source.getLabels()));
                    }
                    return target;
                });
    }

    private void configureNotification(ModelMapper mapper) {
        mapper.createTypeMap(Notification.class, NotificationDto.class)
                .addMappings(map -> {
                    map.skip(NotificationDto::setUserId);
                    map.skip(NotificationDto::setTaskId);
                })
                .setPostConverter(context -> {
                    Notification source = context.getSource();
                    NotificationDto target = context.getDestination();
                    target.setUserId(source.getUser() == null ? null : source.getUser().getId());
                    target.setTaskId(source.getTask() == null ? null : source.getTask().getId());
                    target.setDeliveryAttempts(source.getDeliveryAttempts());
                    return target;
                });
    }

    private void configureReminder(ModelMapper mapper) {
        mapper.createTypeMap(Reminder.class, ReminderDto.class)
                .addMappings(map -> map.skip(ReminderDto::setTaskId))
                .setPostConverter(context -> {
                    Reminder source = context.getSource();
                    ReminderDto target = context.getDestination();
                    target.setTaskId(source.getTask() == null ? null : source.getTask().getId());
                    target.setFireCount(source.getFireCount());
                    return target;
                });
    }

    private void configureUser(ModelMapper mapper) {
        mapper.createTypeMap(User.class, UserDto.class)
                .setPostConverter(context -> {
                    User source = context.getSource();
                    UserDto target = context.getDestination();
                    target.setRoles(source.getRoles() == null ? null : List.copyOf(source.getRoles()));
                    return target;
                });
    }
}
