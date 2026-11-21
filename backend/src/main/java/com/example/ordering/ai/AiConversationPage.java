package com.example.ordering.ai;

import java.util.List;

public record AiConversationPage(List<AiConversationSummary> items, String nextCursor, boolean hasMore) {
    public AiConversationPage {
        items = List.copyOf(items);
        nextCursor = nextCursor == null ? "" : nextCursor;
    }
}
