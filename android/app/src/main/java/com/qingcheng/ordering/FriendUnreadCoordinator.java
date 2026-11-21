package com.qingcheng.ordering;

import java.util.Objects;
import java.util.function.LongConsumer;

final class FriendUnreadCoordinator {
    record Request(long sessionId, long sequence) {
    }

    private long sessionId;
    private long sequence;

    synchronized Request request() {
        return new Request(sessionId, ++sequence);
    }

    synchronized boolean commit(Request request, long unread, LongConsumer consumer) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(consumer, "consumer");
        if (request.sessionId() != sessionId || request.sequence() != sequence) {
            return false;
        }
        consumer.accept(Math.max(0, unread));
        return true;
    }

    synchronized void invalidate() {
        sessionId++;
        sequence++;
    }
}
