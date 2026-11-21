package com.qingcheng.ordering;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable accumulated page state; stale or repeating cursors cannot duplicate rows/requests. */
final class AiHistoryCoordinator {
    record State(List<AiHistoryState.ConversationSummary> items, String nextCursor, boolean hasMore,
                 boolean loading, String error, String token, long generation) {
        State {
            items = List.copyOf(items);
            nextCursor = nextCursor == null ? "" : nextCursor;
            error = error == null ? "" : error;
            token = token == null ? "" : token;
        }
    }

    private final Map<Long, AiHistoryState.ConversationSummary> items = new LinkedHashMap<>();
    private String token = "";
    private String cursor = "";
    private boolean hasMore;
    private boolean loading;
    private String error = "";
    private long generation;

    synchronized State begin(String authToken, boolean reset) {
        String next = authToken == null ? "" : authToken;
        if (!token.equals(next)) reset = true;
        token = next;
        // A request, not just an account/reset, owns a generation. A late previous page
        // must not consume the loading slot of a subsequent page or retry.
        generation++;
        if (reset) {
            cursor = "";
            hasMore = true;
            items.clear();
        }
        loading = true;
        error = "";
        return snapshot();
    }

    synchronized boolean loaded(State request, AiHistoryState.Page page) {
        if (!matches(request)) return false;
        for (AiHistoryState.ConversationSummary item : page.items()) {
            items.put(item.id(), item);
        }
        String nextCursor = page.nextCursor();
        hasMore = page.hasMore() && !nextCursor.isEmpty() && !nextCursor.equals(cursor);
        cursor = nextCursor;
        loading = false;
        error = "";
        return true;
    }

    synchronized boolean failed(State request, Exception failure) {
        if (!matches(request)) return false;
        loading = false;
        error = failure == null || failure.getMessage() == null ? "加载失败" : failure.getMessage();
        return true;
    }

    synchronized State snapshot() {
        return new State(new ArrayList<>(items.values()), cursor, hasMore, loading,
                error, token, generation);
    }

    private boolean matches(State request) {
        return request != null && request.generation() == generation
                && request.token().equals(token) && loading;
    }
}
