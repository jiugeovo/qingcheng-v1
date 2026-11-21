package com.qingcheng.ordering;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

final class OrderMutationCoordinator {
    record Mutation(long orderId, boolean menu, String authToken, long sequence, String action) {
        Mutation {
            authToken = authToken == null ? "" : authToken;
            action = action == null || action.isBlank() ? "PROCESSING" : action;
        }
    }

    private record OrderKey(long orderId, boolean menu, String authToken) {
    }

    private final Map<OrderKey, Mutation> active = new HashMap<>();
    private long nextSequence;

    synchronized Mutation begin(long orderId, boolean menu, String authToken) {
        return begin(orderId, menu, authToken, "PROCESSING");
    }

    synchronized Mutation begin(long orderId, boolean menu, String authToken, String action) {
        String token = authToken == null ? "" : authToken;
        if (orderId <= 0 || token.isEmpty()) {
            return null;
        }
        OrderKey key = new OrderKey(orderId, menu, token);
        if (active.containsKey(key)) {
            return null;
        }
        Mutation mutation = new Mutation(orderId, menu, token, ++nextSequence, action);
        active.put(key, mutation);
        return mutation;
    }

    synchronized boolean isCurrent(Mutation mutation) {
        if (mutation == null) {
            return false;
        }
        OrderKey key = new OrderKey(
                mutation.orderId(), mutation.menu(), mutation.authToken());
        return mutation.equals(active.get(key));
    }

    synchronized boolean finish(Mutation mutation) {
        if (!isCurrent(mutation)) {
            return false;
        }
        active.remove(new OrderKey(
                mutation.orderId(), mutation.menu(), mutation.authToken()));
        return true;
    }

    synchronized void reset() {
        active.clear();
    }

    synchronized List<Mutation> active(String authToken) {
        String token = authToken == null ? "" : authToken;
        List<Mutation> result = new ArrayList<>();
        for (Mutation mutation : active.values()) {
            if (mutation.authToken().equals(token)) {
                result.add(mutation);
            }
        }
        result.sort(Comparator.comparingLong(Mutation::sequence));
        return List.copyOf(result);
    }
}
