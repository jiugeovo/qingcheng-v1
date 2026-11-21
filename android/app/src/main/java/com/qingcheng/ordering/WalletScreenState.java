package com.qingcheng.ordering;

import java.util.Objects;

/** One immutable data snapshot per authenticated account and role. UI-thread owned. */
final class WalletScreenState {
    enum Phase { EMPTY, LOADING, REFRESHING, READY, ERROR }
    record Snapshot(WalletView.State data, Phase phase) { }
    private String token = "";
    private String role = "";
    private Snapshot snapshot = new Snapshot(null, Phase.EMPTY);

    void useScope(String token, String role) {
        if (!matchesScope(token, role)) {
            clear();
            this.token = Objects.requireNonNullElse(token, "");
            this.role = Objects.requireNonNullElse(role, "");
        }
    }

    boolean matchesScope(String token, String role) {
        return this.token.equals(token) && this.role.equals(role);
    }

    Snapshot snapshot() { return snapshot; }

    void loading() {
        snapshot = new Snapshot(snapshot.data(), snapshot.data() == null ? Phase.LOADING : Phase.REFRESHING);
    }

    void loaded(WalletView.State data) {
        snapshot = new Snapshot(Objects.requireNonNull(data), Phase.READY);
    }

    void failed() { snapshot = new Snapshot(snapshot.data(), Phase.ERROR); }

    void clear() {
        token = "";
        role = "";
        snapshot = new Snapshot(null, Phase.EMPTY);
    }
}
