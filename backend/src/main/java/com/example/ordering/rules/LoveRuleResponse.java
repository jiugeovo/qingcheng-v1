package com.example.ordering.rules;

import java.time.LocalDateTime;

public record LoveRuleResponse(
        Long id,
        String content,
        int sortOrder,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static LoveRuleResponse from(LoveRuleEntity rule) {
        return new LoveRuleResponse(
                rule.getId(),
                rule.getContent(),
                rule.getSortOrder(),
                rule.getCreatedAt(),
                rule.getUpdatedAt()
        );
    }
}
