package com.example.ordering.ai;

import com.example.ordering.common.JdbcEntityMappers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

@Repository
public class AiMessageRepository {

    private final JdbcTemplate jdbc;

    public AiMessageRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<AiMessageEntity> findAllByConversationIdOrderByCreatedAtAsc(Long conversationId) {
        return jdbc.query("select * from ai_messages where conversation_id = ? order by created_at asc, id asc",
                JdbcEntityMappers.AI_MESSAGE,
                conversationId);
    }

    public List<AiMessageEntity> findRecentByConversationIdOrderByCreatedAtAsc(Long conversationId, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        // Bound the history in SQL before restoring chronological order. The ID breaks
        // ties when user and assistant timestamps share the database's microsecond precision.
        return List.copyOf(jdbc.query("""
                        select recent.* from (
                            select * from ai_messages
                            where conversation_id = ?
                            order by created_at desc, id desc
                            limit ?
                        ) recent
                        order by recent.created_at asc, recent.id asc
                        """,
                JdbcEntityMappers.AI_MESSAGE,
                conversationId,
                limit));
    }

    public List<AiMessageEntity> findTop12ByConversationIdAndImageRefsIsNotNullOrderByCreatedAtDesc(Long conversationId) {
        return jdbc.query("""
                        select * from ai_messages
                        where conversation_id = ? and image_refs is not null
                        order by created_at desc, id desc
                        limit 12
                        """,
                JdbcEntityMappers.AI_MESSAGE,
                conversationId);
    }

    public AiMessageEntity save(AiMessageEntity message) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                            insert into ai_messages
                            (conversation_id, role, content, model, image_count, image_refs, created_at)
                            values (?, ?, ?, ?, ?, ?, ?)
                            """,
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, message.getConversationId());
            ps.setString(2, message.getRole());
            ps.setString(3, message.getContent());
            ps.setString(4, message.getModel());
            ps.setInt(5, message.getImageCount());
            ps.setString(6, message.getImageRefs());
            ps.setObject(7, message.getCreatedAt());
            return ps;
        }, keyHolder);
        message.setId(keyHolder.getKey().longValue());
        return message;
    }

    public void deleteAllByConversationId(Long conversationId) {
        jdbc.update("delete from ai_messages where conversation_id = ?", conversationId);
    }
}
