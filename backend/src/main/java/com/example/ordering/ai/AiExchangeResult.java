package com.example.ordering.ai;

public record AiExchangeResult(
        Long conversationId,
        AiMessageResponse userMessage,
        AiMessageResponse assistantMessage
) {
}
