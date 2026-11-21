package com.example.ordering.capability;

import com.example.ordering.anniversary.AnniversaryRequest;
import com.example.ordering.anniversary.AnniversaryType;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

/** Shared wire schema and conversion for the internal anniversary commands. */
final class AnniversaryCapabilitySupport {
    static final Map<String, Object> ID_SCHEMA = Map.of(
            "id", Map.of("type", "integer", "required", true, "minimum", 1));
    static final Map<String, Object> WRITE_SCHEMA = Map.of(
            "title", Map.of("type", "string", "required", true, "minLength", 1, "maxLength", 100),
            "date", Map.of("type", "string", "required", true, "format", "date"),
            "note", Map.of("type", "string", "required", false, "maxLength", 1000),
            "repeatYearly", Map.of("type", "boolean", "required", false),
            "type", Map.of("type", "string", "required", false, "enum", AnniversaryType.ids()));
    static final Map<String, Object> UPDATE_SCHEMA = updateSchema();

    private AnniversaryCapabilitySupport() {
    }

    /** Mirrors the request record's normalization before its documented string bounds are checked. */
    static Map<String, Object> normalize(Map<String, Object> input) {
        if (input == null) return null;
        Map<String, Object> normalized = new HashMap<>(input);
        if (input.get("title") instanceof String title) normalized.put("title", title.strip());
        if (input.get("note") instanceof String note) normalized.put("note", note.strip());
        return normalized;
    }

    static long id(Map<String, Object> input) {
        Object value = input == null ? null : input.get("id");
        if (!(value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long)
                || ((Number) value).longValue() <= 0) {
            throw new IllegalArgumentException("id 必须是正整数");
        }
        return ((Number) value).longValue();
    }

    static AnniversaryRequest request(Map<String, Object> input, Validator validator) {
        if (input == null || !(input.get("date") instanceof String dateValue)) {
            throw new IllegalArgumentException("date 必须是有效的 ISO 日期（yyyy-MM-dd）");
        }
        LocalDate date;
        try {
            date = LocalDate.parse(dateValue);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("date 必须是有效的 ISO 日期（yyyy-MM-dd）", ex);
        }
        AnniversaryRequest request = new AnniversaryRequest(
                (String) input.get("title"), date, (String) input.get("note"),
                (Boolean) input.get("repeatYearly"),
                input.get("type") == null ? null : AnniversaryType.fromId((String) input.get("type")));
        var violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
        return request;
    }

    private static Map<String, Object> updateSchema() {
        Map<String, Object> schema = new HashMap<>(WRITE_SCHEMA);
        schema.putAll(ID_SCHEMA);
        return Map.copyOf(schema);
    }
}
