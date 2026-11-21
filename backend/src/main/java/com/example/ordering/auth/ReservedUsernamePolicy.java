package com.example.ordering.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public final class ReservedUsernamePolicy {

    private final String configuredUsername;
    private final String normalizedUsername;

    public ReservedUsernamePolicy(@Value("${app.super-admin.username}") String configuredUsername) {
        if (configuredUsername == null || configuredUsername.isBlank()) {
            throw new IllegalArgumentException("SUPER_ADMIN_USERNAME must not be blank");
        }
        this.configuredUsername = configuredUsername.trim();
        this.normalizedUsername = normalize(this.configuredUsername);
    }

    public String configuredUsername() {
        return configuredUsername;
    }

    public boolean isReserved(String username) {
        return username != null && normalizedUsername.equals(normalize(username));
    }

    private static String normalize(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }
}
