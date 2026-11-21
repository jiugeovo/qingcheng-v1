package com.qingcheng.ordering;

/** Account identity snapshot used to reject stale work after login changes. */
record AccountScope(String token, long generation) {
    AccountScope { token = token == null ? "" : token; }
    boolean isAuthenticated() { return !token.isEmpty(); }
}
