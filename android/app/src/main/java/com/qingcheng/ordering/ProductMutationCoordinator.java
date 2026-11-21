package com.qingcheng.ordering;

import java.util.HashMap;
import java.util.Map;

final class ProductMutationCoordinator {
    enum Kind {
        DELETE,
        ORDER
    }

    record Mutation(long itemId, boolean menu, Kind kind, String authToken, long sequence) {
        Mutation {
            authToken = authToken == null ? "" : authToken;
        }
    }

    private record Key(long itemId, boolean menu, Kind kind, String authToken) {
    }

    private final Map<Key, Mutation> active = new HashMap<>();
    private long nextSequence;

    synchronized Mutation beginDelete(long itemId, boolean menu, String authToken) {
        return begin(itemId, menu, Kind.DELETE, authToken);
    }

    synchronized Mutation beginOrder(long itemId, boolean menu, String authToken) {
        return begin(itemId, menu, Kind.ORDER, authToken);
    }

    private Mutation begin(long itemId, boolean menu, Kind kind, String authToken) {
        String token = authToken == null ? "" : authToken;
        if (itemId <= 0 || token.isEmpty() || kind == null) {
            return null;
        }
        Key key = new Key(itemId, menu, kind, token);
        if (active.containsKey(key)) {
            return null;
        }
        Mutation mutation = new Mutation(itemId, menu, kind, token, ++nextSequence);
        active.put(key, mutation);
        return mutation;
    }

    synchronized boolean isCurrent(Mutation mutation) {
        if (mutation == null) {
            return false;
        }
        Key key = new Key(mutation.itemId(), mutation.menu(), mutation.kind(), mutation.authToken());
        return mutation.equals(active.get(key));
    }

    synchronized boolean finish(Mutation mutation) {
        if (!isCurrent(mutation)) {
            return false;
        }
        active.remove(new Key(
                mutation.itemId(), mutation.menu(), mutation.kind(), mutation.authToken()));
        return true;
    }

    synchronized void reset() {
        active.clear();
    }
}
