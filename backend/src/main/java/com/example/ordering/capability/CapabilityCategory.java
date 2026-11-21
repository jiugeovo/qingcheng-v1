package com.example.ordering.capability;

/** Stable catalog identifiers; UI labels belong to each client, not the orchestration contract. */
public enum CapabilityCategory {
    LIFESTYLE("lifestyle"),
    ORDERING("ordering"),
    CREATION("creation"),
    INTELLIGENCE("intelligence"),
    SOCIAL("social");

    private final String id;

    CapabilityCategory(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
