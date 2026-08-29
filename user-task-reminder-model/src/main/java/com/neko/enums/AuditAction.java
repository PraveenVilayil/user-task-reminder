package com.neko.enums;

/**
 * The kind of change recorded in an {@link com.neko.entity.AuditLog} row.
 */
public enum AuditAction {
    CREATE,
    UPDATE,
    STATUS_CHANGE,
    DELETE
}
