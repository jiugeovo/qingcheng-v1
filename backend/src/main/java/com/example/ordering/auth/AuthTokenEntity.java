package com.example.ordering.auth;


import java.time.Instant;

public class AuthTokenEntity {

    private String token;

    private String tokenHash;

    private Long userId;

    private Instant expiresAt;

    private Instant createdAt;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getTokenHash() { return tokenHash; }

    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
