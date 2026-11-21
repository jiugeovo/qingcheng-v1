package com.example.ordering.idempotency;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Arrays;

@Repository
public class OrderIdempotencyRepository {
    private final JdbcTemplate jdbc;

    public OrderIdempotencyRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record Receipt(String requestHash, String responseJson, LocalDateTime expiresAt) { }

    public Optional<Receipt> findLocked(long userId, String operation, String key) {
        return jdbc.query("""
                select request_hash, response_json, expires_at from order_idempotency_receipts
                where user_id = ? and operation = ? and idempotency_key = ? for update
                """, (rs, row) -> new Receipt(rs.getString("request_hash"), rs.getString("response_json"),
                rs.getTimestamp("expires_at").toLocalDateTime()), userId, operation, key).stream().findFirst();
    }

    public void save(long userId, String operation, String key, String requestHash, String responseJson,
                     LocalDateTime now, LocalDateTime expiresAt) {
        jdbc.update("""
                insert into order_idempotency_receipts
                (user_id, operation, idempotency_key, request_hash, response_json, created_at, expires_at)
                values (?, ?, ?, ?, ?, ?, ?)
                """, userId, operation, key, requestHash, responseJson, now, expiresAt);
    }

    public void delete(long userId, String operation, String key) {
        jdbc.update("delete from order_idempotency_receipts where user_id = ? and operation = ? and idempotency_key = ?",
                userId, operation, key);
    }

    public int deleteExpiredBatch(LocalDateTime now) {
        List<ReceiptKey> keys = jdbc.query("""
                select user_id, operation, idempotency_key
                from order_idempotency_receipts
                where expires_at <= ?
                order by expires_at
                limit 500
                """, (rs, row) -> new ReceiptKey(rs.getLong("user_id"), rs.getString("operation"),
                rs.getString("idempotency_key")), now);
        if (keys.isEmpty()) return 0;
        int[] counts = jdbc.batchUpdate("delete from order_idempotency_receipts "
                        + "where user_id = ? and operation = ? and idempotency_key = ? and expires_at <= ?",
                new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement statement, int index) throws SQLException {
                        ReceiptKey key = keys.get(index);
                        statement.setLong(1, key.userId());
                        statement.setString(2, key.operation());
                        statement.setString(3, key.key());
                        statement.setObject(4, now);
                    }

                    @Override
                    public int getBatchSize() { return keys.size(); }
                });
        return Arrays.stream(counts).map(count -> count == java.sql.Statement.SUCCESS_NO_INFO
                ? 1 : Math.max(count, 0)).sum();
    }

    private record ReceiptKey(long userId, String operation, String key) { }
}
