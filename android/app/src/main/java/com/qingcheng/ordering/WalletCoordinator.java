package com.qingcheng.ordering;

/** Rejects wallet results from a prior account, role, or superseded refresh. */
final class WalletCoordinator {
    record Request(AccountScope account, String role, long sequence) {
        Request { role = role == null ? "" : role; }
    }

    private String token = "";
    private String role = "";
    private long sequence;

    synchronized Request begin(String authToken, String userRole) {
        token = authToken == null ? "" : authToken;
        role = userRole == null ? "" : userRole;
        return new Request(new AccountScope(token, sequence), role, ++sequence);
    }

    synchronized boolean isCurrent(Request request, String authToken, String userRole) {
        return request != null && request.sequence() == sequence
                && request.account().token().equals(token)
                && token.equals(authToken == null ? "" : authToken)
                && role.equals(userRole == null ? "" : userRole)
                && request.role().equals(role);
    }

    synchronized void invalidate() { sequence++; }
}
