package com.neko.validation;

/**
 * Bean Validation groups so the same DTO can back both a full create (POST)
 * and a sparse patch (PATCH) without needing two classes.
 */
public final class ValidationGroups {

    private ValidationGroups() {
    }

    /** Applied on create; mandatory fields must be present. */
    public interface OnCreate {
    }

    /** Applied on partial update; only supplied fields are checked. */
    public interface OnUpdate {
    }
}
