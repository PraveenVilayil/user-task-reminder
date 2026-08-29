package com.neko.validation;

import jakarta.validation.groups.Default;

/**
 * Bean Validation groups so the same DTO can back both a full create (POST)
 * and a sparse patch (PATCH) without needing two classes.
 *
 * <p>Both groups extend {@link Default}, so validating against either one also
 * applies the ungrouped constraints. Without that inheritance, activating a
 * custom group switches the default group off and constraints such as
 * {@code @Email} and {@code @Size} are silently skipped.</p>
 */
public final class ValidationGroups {

    private ValidationGroups() {
    }

    /** Applied on create; mandatory fields must be present. */
    public interface OnCreate extends Default {
    }

    /** Applied on partial update; only the supplied fields are checked. */
    public interface OnUpdate extends Default {
    }
}
