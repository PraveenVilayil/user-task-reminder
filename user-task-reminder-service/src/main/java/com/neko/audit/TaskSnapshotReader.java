package com.neko.audit;

import com.neko.dto.TaskDto;
import com.neko.repositories.TaskRepository;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Reads the pre-change state of a task for {@link TaskAuditAspect}.
 *
 * <p>Exists as its own bean so the read happens inside a transaction: mapping a
 * task to its DTO touches lazy collections, which would fail if it ran in the
 * aspect itself.</p>
 */
@Component
public class TaskSnapshotReader {

    private final TaskRepository taskRepository;
    private final ModelMapper mapper;

    public TaskSnapshotReader(TaskRepository taskRepository, ModelMapper mapper) {
        this.taskRepository = taskRepository;
        this.mapper = mapper;
    }

    /**
     * @return the task as it currently stands, or null when it does not exist
     */
    @Transactional(readOnly = true)
    public TaskDto snapshot(UUID taskId) {
        if (taskId == null) {
            return null;
        }
        return taskRepository.findById(taskId)
                .map(task -> mapper.map(task, TaskDto.class))
                .orElse(null);
    }
}
