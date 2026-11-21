package com.example.ordering.auth;

import com.example.ordering.common.JdbcEntityMappers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Repository
public class AuthTokenRepository {

    private final JdbcTemplate jdbc;

    public AuthTokenRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<AuthTokenEntity> findById(String token) {
        String tokenHash = hash(token);
        List<AuthTokenEntity> tokens = jdbc.query("select * from auth_tokens where token_hash = ? or token = ?", JdbcEntityMappers.AUTH_TOKEN, tokenHash, token);
        if (tokens.isEmpty()) return Optional.empty();
        AuthTokenEntity entity = tokens.get(0);
        boolean hashMatches = entity.getTokenHash() != null && constantTimeEquals(entity.getTokenHash(), tokenHash);
        boolean legacyTokenMatches = entity.getToken() != null && constantTimeEquals(entity.getToken(), token);
        if (!hashMatches && !legacyTokenMatches) {
            return Optional.empty();
        }
        if (entity.getTokenHash() == null && legacyTokenMatches) {
            jdbc.update("update auth_tokens set token_hash = ? where token = ? and token_hash is null", tokenHash, token);
            entity.setTokenHash(tokenHash);
        }
        return Optional.of(entity);
    }

    public AuthTokenEntity save(AuthTokenEntity token) {
        jdbc.update("""
                        insert into auth_tokens (token, token_hash, user_id, expires_at, created_at)
                        values (?, ?, ?, ?, ?)
                        on duplicate key update user_id = values(user_id), expires_at = values(expires_at), created_at = values(created_at)
                        """,
                token.getToken(),
                hash(token.getToken()),
                token.getUserId(),
                token.getExpiresAt(),
                token.getCreatedAt());
        return token;
    }

    public void delete(AuthTokenEntity token) {
        if (token != null) {
            deleteById(token.getToken());
        }
    }

    public void deleteById(String token) {
        jdbc.update("delete from auth_tokens where token_hash = ? or token = ?", hash(token), token);
    }

    public void deleteByExpiresAtBefore(Instant now) {
        jdbc.update("delete from auth_tokens where expires_at <= ?", now);
    }

    public void deleteAllByUserId(Long userId) {
        jdbc.update("delete from auth_tokens where user_id = ?", userId);
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static boolean constantTimeEquals(String left, String right) {
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }
}
