package com.example.ordering.anniversary;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public record AnniversaryResponse(
        Long id,
        String title,
        LocalDate date,
        String note,
        boolean repeatYearly,
        AnniversaryType type,
        LocalDate nextOccurrence,
        Long daysUntil,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean canManage
) {
    public AnniversaryResponse(Long id, String title, LocalDate date, String note, boolean repeatYearly,
                               AnniversaryType type, LocalDate nextOccurrence, Long daysUntil,
                               LocalDateTime createdAt, LocalDateTime updatedAt) {
        this(id, title, date, note, repeatYearly, type, nextOccurrence, daysUntil, createdAt, updatedAt, false);
    }

    static AnniversaryResponse from(AnniversaryEntity entity, LocalDate today) {
        return from(entity, today, false);
    }

    static AnniversaryResponse from(AnniversaryEntity entity, LocalDate today, boolean canManage) {
        LocalDate next = AnniversaryDates.nextOccurrence(entity.date(), entity.repeatYearly(), today);
        return new AnniversaryResponse(
                entity.id(), entity.title(), entity.date(), entity.note(), entity.repeatYearly(), entity.type(),
                next, next == null ? null : ChronoUnit.DAYS.between(today, next),
                entity.createdAt(), entity.updatedAt(), canManage
        );
    }
}
