package com.qingcheng.ordering;

import java.io.IOException;

/** Network boundary for AI conversation history. */
final class AiHistoryService {
    private final ApiClient api;

    AiHistoryService(ApiClient api) { this.api = api; }

    AiHistoryState.Page load(String cursor, int limit, String token) throws Exception {
        try {
            return api.aiConversationsPage(cursor, limit, token);
        } catch (ApiClient.RequestRejectedException rejected) {
            if ((rejected.statusCode() == 404 || rejected.statusCode() == 405)
                    && (cursor == null || cursor.isEmpty())) {
                return AiHistoryState.Page.from(api.aiConversations(token));
            }
            throw rejected;
        }
    }
}
