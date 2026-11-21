package com.example.ordering.ai;

import java.time.LocalDateTime;

public record AiConversationSummary(
        Long id,
        String title,
        String model,
        long messageCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
