package com.example.ordering.anniversary;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

public record AnniversaryEntity(
        Long id,
        long userId,
        String title,
        LocalDate date,
        String note,
        boolean repeatYearly,
        AnniversaryType type,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public AnniversaryEntity {
        Objects.requireNonNull(type, "Anniversary type is required");
    }
}
