package com.neko.service;

import com.neko.dto.PageResponse;
import com.neko.dto.TaskDto;
import com.neko.dto.TaskSearchCriteria;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface TaskService {

    TaskDto create(TaskDto task);

    TaskDto get(UUID id);

    List<TaskDto> list();

    /** Paged, sorted and filtered task lookup. */
    PageResponse<TaskDto> search(TaskSearchCriteria criteria, Pageable pageable);

    TaskDto update(UUID id, TaskDto task);

    void delete(UUID id);
}
