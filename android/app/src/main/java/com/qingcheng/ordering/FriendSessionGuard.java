package com.qingcheng.ordering;

import java.util.Objects;

final class FriendSessionGuard {
    record Session(long generation, String authToken) {
        Session {
            authToken = authToken == null ? "" : authToken;
        }

        boolean authenticated() {
            return !authToken.isEmpty();
        }
    }

    private long generation;

    synchronized Session capture(String authToken) {
        return new Session(generation, authToken);
    }

    synchronized boolean isCurrent(Session session, String currentAuthToken) {
        return session != null
                && session.generation() == generation
                && session.authenticated()
                && Objects.equals(session.authToken(), currentAuthToken == null ? "" : currentAuthToken);
    }

    synchronized void invalidate() {
        generation++;
    }
}
