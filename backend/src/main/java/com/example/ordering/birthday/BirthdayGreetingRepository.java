package com.example.ordering.birthday;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public class BirthdayGreetingRepository {

    private final JdbcTemplate jdbc;

    public BirthdayGreetingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean savePrepared(
            Long userId,
            int greetingYear,
            String content,
            String model,
            LocalDateTime updatedAt
    ) {
        int updated = jdbc.update("""
                        update birthday_greeting_deliveries
                        set status = 'PREPARED', greeting_content = ?, model = ?, updated_at = ?
                        where user_id = ? and greeting_year = ? and status <> 'SENT'
                        """,
                content,
                model,
                updatedAt,
                userId,
                greetingYear);
        if (updated == 1) {
            return true;
        }
        try {
            jdbc.update("""
                            insert into birthday_greeting_deliveries
                            (user_id, greeting_year, status, greeting_content, model, created_at, updated_at)
                            values (?, ?, 'PREPARED', ?, ?, ?, ?)
                            """,
                    userId,
                    greetingYear,
                    content,
                    model,
                    updatedAt,
                    updatedAt);
            return true;
        } catch (DuplicateKeyException ignored) {
            return false;
        }
    }

    public boolean claim(
            Long userId,
            int greetingYear,
            String fallbackContent,
            String model,
            LocalDateTime createdAt
    ) {
        int updated = jdbc.update("""
                        update birthday_greeting_deliveries
                        set status = 'PROCESSING', updated_at = ?
                        where user_id = ? and greeting_year = ? and status = 'PREPARED'
                        """,
                createdAt,
                userId,
                greetingYear);
        if (updated == 1) {
            return true;
        }
        try {
            jdbc.update("""
                            insert into birthday_greeting_deliveries
                            (user_id, greeting_year, status, greeting_content, model, created_at, updated_at)
                            values (?, ?, 'PROCESSING', ?, ?, ?, ?)
                            """,
                    userId,
                    greetingYear,
                    fallbackContent,
                    model,
                    createdAt,
                    createdAt);
            return true;
        } catch (DuplicateKeyException ignored) {
            return false;
        }
    }

    public Optional<PreparedGreeting> findClaimed(Long userId, int greetingYear) {
        return jdbc.query(
                        """
                                select greeting_content, model from birthday_greeting_deliveries
                                where user_id = ? and greeting_year = ? and status = 'PROCESSING'
                                """,
                        (resultSet, rowNum) -> new PreparedGreeting(
                                resultSet.getString("greeting_content"),
                                resultSet.getString("model")
                        ),
                        userId,
                        greetingYear)
                .stream()
                .findFirst();
    }

    public void markSent(Long userId, int greetingYear, Long conversationId, String model, LocalDateTime updatedAt) {
        jdbc.update("""
                        update birthday_greeting_deliveries
                        set status = 'SENT', conversation_id = ?, model = ?, updated_at = ?
                        where user_id = ? and greeting_year = ?
                        """,
                conversationId,
                model,
                updatedAt,
                userId,
                greetingYear);
    }

    public record PreparedGreeting(String content, String model) {
    }
}
