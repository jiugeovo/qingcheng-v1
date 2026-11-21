package com.example.ordering.anniversary;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.List;

/** Stable persisted and wire IDs; titles never determine an anniversary's category. */
public enum AnniversaryType {
    MEETING("meeting", "相识纪念"),
    RELATIONSHIP("relationship", "恋爱纪念"),
    WEDDING("wedding", "结婚纪念"),
    BIRTHDAY("birthday", "生日"),
    OTHER("other", "其他日子");

    private static final List<String> IDS = Arrays.stream(values()).map(AnniversaryType::id).toList();

    private final String id;
    private final String label;

    AnniversaryType(String id, String label) {
        this.id = id;
        this.label = label;
    }

    @JsonValue
    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public static List<String> ids() {
        return IDS;
    }

    public static AnniversaryType fromId(String id) {
        return Arrays.stream(values()).filter(type -> type.id.equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "type 必须是 meeting、relationship、wedding、birthday 或 other"));
    }
}
