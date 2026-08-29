package com.neko.serviceImpl;

import com.neko.dto.PageResponse;
import com.neko.dto.TaskDto;
import com.neko.dto.TaskSearchCriteria;
import com.neko.entity.Task;
import com.neko.entity.User;
import com.neko.enums.Status;
import com.neko.exceptions.ErrorCode.ErrorCode;
import com.neko.exceptions.UserTaskReminderException;
import com.neko.repositories.TaskRepository;
import com.neko.repositories.UserRepository;
import com.neko.service.TaskService;
import com.neko.specifications.TaskSpecifications;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TaskServiceImpl implements TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskServiceImpl.class);

    /**
     * Properties a client may sort by. Anything else is rejected rather than
     * being handed to the persistence layer, which would surface as a 500.
     */
    private static final Set<String> SORTABLE_PROPERTIES = Set.of(
            "name", "createdDate", "modifiedDate", "dueDate", "priority", "status");

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final ModelMapper mapper;
    private final Clock clock;

    public TaskServiceImpl(TaskRepository taskRepository, UserRepository userRepository,
                           ModelMapper mapper, Clock clock) {
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public TaskDto create(TaskDto taskDto) {
        Task task = new Task();
        task.setId(UUID.randomUUID());
        task.setCreatedDate(LocalDateTime.now(clock));
        task.setName(taskDto.getName());
        task.setDescription(taskDto.getDescription());
        task.setDueDate(taskDto.getDueDate());
        task.setRecurring(taskDto.getRecurring());
        task.setRemainderId(taskDto.getRemainderId());
        task.setModifiedBy(taskDto.getModifiedBy());
        task.setPriority(taskDto.getPriority());
        task.setStatus(taskDto.getStatus() == null ? Status.PENDING : taskDto.getStatus());
        task.setLabels(taskDto.getLabels() == null ? new ArrayList<>() : new ArrayList<>(taskDto.getLabels()));
        task.setCreatedBy(resolveOwner(taskDto.getCreatedById()));
        task.setPrerequisites(resolvePrerequisites(task.getId(), taskDto.getPrerequisiteIds()));

        if (task.getStatus() == Status.COMPLETED) {
            assertPrerequisitesComplete(task);
        }

        Task saved = taskRepository.save(task);
        log.info("Created task {} ({})", saved.getName(), saved.getId());
        return mapper.map(saved, TaskDto.class);
    }

    @Override
    @Transactional(readOnly = true)
    public TaskDto get(UUID id) {
        return mapper.map(findOrThrow(id), TaskDto.class);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskDto> list() {
        return taskRepository.findAll().stream()
                .map(task -> mapper.map(task, TaskDto.class))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TaskDto> search(TaskSearchCriteria criteria, Pageable pageable) {
        Pageable effective = pageable == null ? Pageable.unpaged() : pageable;
        assertSortable(effective.getSort());

        Page<Task> page = taskRepository.findAll(
                TaskSpecifications.from(criteria, LocalDateTime.now(clock)), effective);

        List<TaskDto> content = page.getContent().stream()
                .map(task -> mapper.map(task, TaskDto.class))
                .collect(Collectors.toList());

        return PageResponse.of(content, page.getNumber(), page.getSize(), page.getTotalElements());
    }

    /**
     * Partial update: only non-null fields on the incoming DTO are applied.
     * Server-owned fields (id, createdDate) are never taken from the request,
     * and a move to COMPLETED is refused while prerequisites are still open.
     */
    @Override
    @Transactional
    public TaskDto update(UUID id, TaskDto taskDto) {
        Task existing = findOrThrow(id);

        if (taskDto.getName() != null) {
            existing.setName(taskDto.getName());
        }
        if (taskDto.getDescription() != null) {
            existing.setDescription(taskDto.getDescription());
        }
        if (taskDto.getDueDate() != null) {
            existing.setDueDate(taskDto.getDueDate());
            // a new due date reopens the overdue sweep for this task
            existing.setOverdueNotifiedAt(null);
        }
        if (taskDto.getRecurring() != null) {
            existing.setRecurring(taskDto.getRecurring());
        }
        if (taskDto.getRemainderId() != null) {
            existing.setRemainderId(taskDto.getRemainderId());
        }
        if (taskDto.getModifiedBy() != null) {
            existing.setModifiedBy(taskDto.getModifiedBy());
        }
        if (taskDto.getPriority() != null) {
            existing.setPriority(taskDto.getPriority());
        }
        if (taskDto.getLabels() != null) {
            existing.setLabels(new ArrayList<>(taskDto.getLabels()));
        }
        if (taskDto.getCreatedById() != null) {
            existing.setCreatedBy(resolveOwner(taskDto.getCreatedById()));
        }
        if (taskDto.getPrerequisiteIds() != null) {
            existing.setPrerequisites(resolvePrerequisites(existing.getId(), taskDto.getPrerequisiteIds()));
        }
        if (taskDto.getStatus() != null) {
            if (taskDto.getStatus() == Status.COMPLETED && existing.getStatus() != Status.COMPLETED) {
                assertPrerequisitesComplete(existing);
            }
            existing.setStatus(taskDto.getStatus());
        }
        existing.setModifiedDate(LocalDateTime.now(clock));

        return mapper.map(taskRepository.save(existing), TaskDto.class);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        taskRepository.delete(findOrThrow(id));
        log.info("Deleted task {}", id);
    }

    private Task findOrThrow(UUID id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new UserTaskReminderException(ErrorCode.TASK_NOT_FOUND, id));
    }

    /** A task may only reference an owner that actually exists. */
    private User resolveOwner(UUID ownerId) {
        if (ownerId == null) {
            return null;
        }
        return userRepository.findById(ownerId)
                .orElseThrow(() -> new UserTaskReminderException(ErrorCode.USER_NOT_FOUND, ownerId));
    }

    private List<Task> resolvePrerequisites(UUID taskId, List<UUID> prerequisiteIds) {
        if (prerequisiteIds == null || prerequisiteIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<Task> prerequisites = new ArrayList<>();
        for (UUID prerequisiteId : prerequisiteIds) {
            if (prerequisiteId.equals(taskId)) {
                throw new UserTaskReminderException(ErrorCode.TASK_PREREQUISITE_CYCLE, taskId);
            }
            Task prerequisite = taskRepository.findById(prerequisiteId)
                    .orElseThrow(() -> new UserTaskReminderException(ErrorCode.TASK_NOT_FOUND, prerequisiteId));
            if (reaches(prerequisite, taskId)) {
                throw new UserTaskReminderException(ErrorCode.TASK_PREREQUISITE_CYCLE, taskId);
            }
            prerequisites.add(prerequisite);
        }
        return prerequisites;
    }

    /** Depth-first walk of the prerequisite graph looking for the target id. */
    private boolean reaches(Task from, UUID targetId) {
        Set<UUID> visited = new HashSet<>();
        Deque<Task> stack = new ArrayDeque<>();
        stack.push(from);
        while (!stack.isEmpty()) {
            Task current = stack.pop();
            if (current.getId() == null || !visited.add(current.getId())) {
                continue;
            }
            if (targetId.equals(current.getId())) {
                return true;
            }
            if (current.getPrerequisites() != null) {
                current.getPrerequisites().forEach(stack::push);
            }
        }
        return false;
    }

    private void assertPrerequisitesComplete(Task task) {
        if (task.getPrerequisites() == null || task.getPrerequisites().isEmpty()) {
            return;
        }
        String open = task.getPrerequisites().stream()
                .filter(prerequisite -> prerequisite.getStatus() != Status.COMPLETED)
                .map(prerequisite -> String.valueOf(prerequisite.getId()))
                .collect(Collectors.joining(", "));
        if (!open.isEmpty()) {
            throw new UserTaskReminderException(ErrorCode.TASK_PREREQUISITES_INCOMPLETE, task.getId(), open);
        }
    }

    private void assertSortable(Sort sort) {
        if (sort == null || sort.isUnsorted()) {
            return;
        }
        sort.forEach(order -> {
            if (!SORTABLE_PROPERTIES.contains(order.getProperty())) {
                throw new UserTaskReminderException(ErrorCode.INVALID_SORT_PROPERTY, order.getProperty());
            }
        });
    }
}
