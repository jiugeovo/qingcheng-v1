package com.example.ordering.friend;

import com.example.ordering.common.JdbcEntityMappers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class FriendMessageRepository {

    private final JdbcTemplate jdbc;

    public FriendMessageRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<FriendMessageEntity> findAllByUserLowIdAndUserHighId(Long userLowId, Long userHighId, int limit) {
        return jdbc.query("""
                        select * from friend_messages
                        where user_low_id = ? and user_high_id = ?
                        order by created_at desc, id desc
                        limit ?
                        """,
                JdbcEntityMappers.FRIEND_MESSAGE,
                userLowId,
                userHighId,
                limit);
    }

    public List<FriendMessageEntity> findAllByUserLowIdAndUserHighIdAndCreatedAtAfter(
            Long userLowId,
            Long userHighId,
            LocalDateTime createdAt,
            int limit
    ) {
        return jdbc.query("""
                        select * from friend_messages
                        where user_low_id = ? and user_high_id = ? and created_at > ?
                        order by created_at desc, id desc
                        limit ?
                        """,
                JdbcEntityMappers.FRIEND_MESSAGE,
                userLowId,
                userHighId,
                createdAt,
                limit);
    }

    public long countByReceiverIdAndSenderIdAndReadAtIsNull(Long receiverId, Long senderId) {
        return count("select count(*) from friend_messages where receiver_id = ? and sender_id = ? and read_at is null", receiverId, senderId);
    }

    public Optional<FriendMessageEntity> findById(Long id) {
        return jdbc.query(
                "select * from friend_messages where id = ? limit 1",
                JdbcEntityMappers.FRIEND_MESSAGE,
                id
        ).stream().findFirst();
    }

    public long countByReceiverIdAndSenderIdAndReadAtIsNullAndCreatedAtAfter(Long receiverId, Long senderId, LocalDateTime createdAt) {
        return count("""
                        select count(*) from friend_messages
                        where receiver_id = ? and sender_id = ? and read_at is null and created_at > ?
                        """,
                receiverId,
                senderId,
                createdAt);
    }

    public long countByReceiverIdAndReadAtIsNull(Long receiverId) {
        return count("select count(*) from friend_messages where receiver_id = ? and read_at is null", receiverId);
    }

    public long countByReceiverIdAndReadAtIsNullAndCreatedAtAfter(Long receiverId, LocalDateTime createdAt) {
        return count("select count(*) from friend_messages where receiver_id = ? and read_at is null and created_at > ?", receiverId, createdAt);
    }

    public FriendMessageEntity findTopByUserLowIdAndUserHighIdOrderByCreatedAtDesc(Long userLowId, Long userHighId) {
        return findTop("""
                        select * from friend_messages
                        where user_low_id = ? and user_high_id = ?
                        order by created_at desc, id desc
                        limit 1
                        """,
                userLowId,
                userHighId);
    }

    public FriendMessageEntity findTopByUserLowIdAndUserHighIdAndCreatedAtAfterOrderByCreatedAtDesc(
            Long userLowId,
            Long userHighId,
            LocalDateTime createdAt
    ) {
        return findTop("""
                        select * from friend_messages
                        where user_low_id = ? and user_high_id = ? and created_at > ?
                        order by created_at desc, id desc
                        limit 1
                        """,
                userLowId,
                userHighId,
                createdAt);
    }

    public int markConversationRead(Long receiverId, Long senderId, LocalDateTime readAt) {
        return jdbc.update("""
                        update friend_messages
                        set read_at = ?
                        where receiver_id = ? and sender_id = ? and read_at is null
                        """,
                readAt,
                receiverId,
                senderId);
    }

    public FriendMessageEntity save(FriendMessageEntity message) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                            insert into friend_messages
                            (user_low_id, user_high_id, sender_id, receiver_id, message_type,
                             content, media_url, reply_to_message_id, reply_to_sender_id,
                             reply_to_type, reply_to_preview, created_at, read_at)
                            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, message.getUserLowId());
            ps.setLong(2, message.getUserHighId());
            ps.setLong(3, message.getSenderId());
            ps.setLong(4, message.getReceiverId());
            ps.setString(5, message.getMessageType().name());
            ps.setString(6, message.getContent());
            ps.setString(7, message.getMediaUrl());
            ps.setObject(8, message.getReplyToMessageId());
            ps.setObject(9, message.getReplyToSenderId());
            ps.setString(10, message.getReplyToType() == null ? null : message.getReplyToType().name());
            ps.setString(11, message.getReplyToPreview());
            ps.setObject(12, message.getCreatedAt());
            ps.setObject(13, message.getReadAt());
            return ps;
        }, keyHolder);
        message.setId(keyHolder.getKey().longValue());
        return message;
    }

    private long count(String sql, Object... args) {
        Long count = jdbc.queryForObject(sql, Long.class, args);
        return count == null ? 0L : count;
    }

    private FriendMessageEntity findTop(String sql, Object... args) {
        List<FriendMessageEntity> messages = jdbc.query(sql, JdbcEntityMappers.FRIEND_MESSAGE, args);
        return messages.stream().findFirst().orElse(null);
    }
}
