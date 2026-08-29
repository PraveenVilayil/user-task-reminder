package com.neko.specifications;

import com.neko.dto.TaskSearchCriteria;
import com.neko.entity.Task;
import com.neko.enums.Priority;
import com.neko.enums.Status;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Composable JPA criteria fragments backing the task search endpoint.
 * Every fragment is null-tolerant, so callers can chain them unconditionally.
 */
public final class TaskSpecifications {

    private TaskSpecifications() {
    }

    /** Builds the full predicate for a search request. */
    public static Specification<Task> from(TaskSearchCriteria criteria, LocalDateTime now) {
        if (criteria == null) {
            return (root, query, cb) -> cb.conjunction();
        }
        return Specification.allOf(
                textMatches(criteria.getQuery()),
                hasStatus(criteria.getStatus()),
                hasPriority(criteria.getPriority()),
                hasAssignee(criteria.getAssigneeId()),
                dueAfter(criteria.getDueFrom()),
                dueBefore(criteria.getDueTo()),
                hasLabel(criteria.getLabel()),
                isOverdue(criteria.getOverdue(), now));
    }

    /** Case-insensitive contains match across name and description. */
    public static Specification<Task> textMatches(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String pattern = "%" + text.toLowerCase(Locale.ROOT).trim() + "%";
        return (root, query, cb) -> {
            List<Predicate> matches = new ArrayList<>();
            matches.add(cb.like(cb.lower(root.get("name")), pattern));
            matches.add(cb.like(cb.lower(root.get("description")), pattern));
            return cb.or(matches.toArray(new Predicate[0]));
        };
    }

    public static Specification<Task> hasStatus(Status status) {
        return status == null ? null : (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Task> hasPriority(Priority priority) {
        return priority == null ? null : (root, query, cb) -> cb.equal(root.get("priority"), priority);
    }

    public static Specification<Task> hasAssignee(UUID assigneeId) {
        return assigneeId == null
                ? null
                : (root, query, cb) -> cb.equal(root.join("createdBy", JoinType.LEFT).get("id"), assigneeId);
    }

    public static Specification<Task> dueAfter(LocalDateTime from) {
        return from == null ? null : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("dueDate"), from);
    }

    public static Specification<Task> dueBefore(LocalDateTime to) {
        return to == null ? null : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("dueDate"), to);
    }

    public static Specification<Task> hasLabel(String label) {
        if (label == null || label.isBlank()) {
            return null;
        }
        return (root, query, cb) -> {
            if (query != null) {
                query.distinct(true);
            }
            return cb.equal(root.join("labels", JoinType.INNER), label);
        };
    }

    /** Past the due date and not yet completed. */
    public static Specification<Task> isOverdue(Boolean overdue, LocalDateTime now) {
        if (overdue == null) {
            return null;
        }
        return (root, query, cb) -> {
            Predicate late = cb.and(
                    cb.isNotNull(root.get("dueDate")),
                    cb.lessThan(root.get("dueDate"), now),
                    cb.notEqual(root.get("status"), Status.COMPLETED));
            return Boolean.TRUE.equals(overdue) ? late : cb.not(late);
        };
    }
}
