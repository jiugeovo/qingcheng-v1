package com.example.ordering.ai;

import com.example.ordering.common.JdbcEntityMappers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.RowMapper;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class AiConversationRepository {

    private final JdbcTemplate jdbc;

    public AiConversationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<AiConversationSummary> findSummariesByUserId(Long userId) {
        return List.copyOf(jdbc.query("""
                        select c.id, c.title, c.model, c.created_at, c.updated_at,
                               count(m.id) as message_count
                        from ai_conversations c
                        left join ai_messages m on m.conversation_id = c.id
                        where c.user_id = ?
                        group by c.id, c.title, c.model, c.created_at, c.updated_at
                        order by c.updated_at desc, c.id desc
                        """,
                (row, rowNum) -> new AiConversationSummary(
                        row.getLong("id"),
                        row.getString("title"),
                        row.getString("model"),
                        row.getLong("message_count"),
                        row.getObject("created_at", LocalDateTime.class),
                        row.getObject("updated_at", LocalDateTime.class)),
                userId));
    }

    public List<AiConversationSummary> findPageByUserId(Long userId, LocalDateTime beforeUpdatedAt,
                                                        Long beforeId, int limit) {
        String cursorClause = beforeUpdatedAt == null ? "" : " and (c.updated_at < ? or (c.updated_at = ? and c.id < ?))";
        String sql = """
                select c.id, c.title, c.model, c.created_at, c.updated_at,
                       count(m.id) as message_count
                from ai_conversations c
                left join ai_messages m on m.conversation_id = c.id
                where c.user_id = ?
                """ + cursorClause + """
                group by c.id, c.title, c.model, c.created_at, c.updated_at
                order by c.updated_at desc, c.id desc
                limit ?
                """;
        RowMapper<AiConversationSummary> mapper = (row, rowNum) -> new AiConversationSummary(
                row.getLong("id"), row.getString("title"), row.getString("model"), row.getLong("message_count"),
                row.getObject("created_at", LocalDateTime.class), row.getObject("updated_at", LocalDateTime.class));
        if (beforeUpdatedAt == null) {
            return List.copyOf(jdbc.query(sql, mapper, userId, limit));
        }
        return List.copyOf(jdbc.query(sql, mapper, userId, beforeUpdatedAt, beforeUpdatedAt, beforeId, limit));
    }

    public Optional<AiConversationEntity> findByIdAndUserId(Long id, Long userId) {
        List<AiConversationEntity> conversations = jdbc.query(
                "select * from ai_conversations where id = ? and user_id = ?",
                JdbcEntityMappers.AI_CONVERSATION,
                id,
                userId);
        return conversations.stream().findFirst();
    }

    public AiConversationEntity save(AiConversationEntity conversation) {
        if (conversation.getId() == null) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement ps = connection.prepareStatement("""
                                insert into ai_conversations
                                (user_id, username, title, model, previous_response_id, created_at, updated_at)
                                values (?, ?, ?, ?, ?, ?, ?)
                                """,
                        Statement.RETURN_GENERATED_KEYS);
                bind(ps, conversation);
                return ps;
            }, keyHolder);
            conversation.setId(keyHolder.getKey().longValue());
        } else {
            jdbc.update("""
                            update ai_conversations
                            set user_id = ?, username = ?, title = ?, model = ?, previous_response_id = ?,
                                created_at = ?, updated_at = ?
                            where id = ?
                            """,
                    conversation.getUserId(),
                    conversation.getUsername(),
                    conversation.getTitle(),
                    conversation.getModel(),
                    conversation.getPreviousResponseId(),
                    conversation.getCreatedAt(),
                    conversation.getUpdatedAt(),
                    conversation.getId());
        }
        return conversation;
    }

    public void delete(AiConversationEntity conversation) {
        if (conversation != null && conversation.getId() != null) {
            jdbc.update("delete from ai_conversations where id = ?", conversation.getId());
        }
    }

    private void bind(PreparedStatement ps, AiConversationEntity conversation) throws java.sql.SQLException {
        ps.setLong(1, conversation.getUserId());
        ps.setString(2, conversation.getUsername());
        ps.setString(3, conversation.getTitle());
        ps.setString(4, conversation.getModel());
        ps.setString(5, conversation.getPreviousResponseId());
        ps.setObject(6, conversation.getCreatedAt());
        ps.setObject(7, conversation.getUpdatedAt());
    }
}
