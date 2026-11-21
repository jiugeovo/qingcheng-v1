package com.example.ordering.capability;

import java.util.Map;
import java.util.Set;
import com.example.ordering.user.UserRole;

/** A user-facing action that can be discovered and invoked by clients or AI. */
public interface Capability {
    String id();
    String name();
    String description();
    String category();
    String route();
    default boolean enabled() { return true; }
    default Map<String, Object> inputSchema() { return Map.of(); }
    default Set<UserRole> visibleTo() { return Set.of(UserRole.USER, UserRole.ADMIN, UserRole.SUPER_ADMIN); }

    /** Domain normalization happens before shared schema validation, never before authorization. */
    default Map<String, Object> normalizeInput(Map<String, Object> input) {
        return input;
    }

    default void validate(Map<String, Object> input) {
        CapabilitySchemas.validate(CapabilitySchemas.freeze(inputSchema()), normalizeInput(input));
    }
}
