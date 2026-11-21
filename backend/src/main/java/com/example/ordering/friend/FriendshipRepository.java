package com.example.ordering.friend;

import com.example.ordering.common.JdbcEntityMappers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

@Repository
public class FriendshipRepository {

    private final JdbcTemplate jdbc;

    public FriendshipRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<FriendshipEntity> findByUserLowIdAndUserHighId(Long userLowId, Long userHighId) {
        return queryOne("select * from friendships where user_low_id = ? and user_high_id = ?", userLowId, userHighId);
    }

    public Optional<FriendshipEntity> findLockedByUserLowIdAndUserHighId(Long userLowId, Long userHighId) {
        return queryOne("select * from friendships where user_low_id = ? and user_high_id = ? for update", userLowId, userHighId);
    }

    public Optional<FriendshipEntity> findLockedById(Long id) {
        return queryOne("select * from friendships where id = ? for update", id);
    }

    public List<FriendshipEntity> findAcceptedForUser(Long userId) {
        return jdbc.query("""
                        select * from friendships
                        where status = 'ACCEPTED' and (user_low_id = ? or user_high_id = ?)
                        order by updated_at desc
                        """,
                JdbcEntityMappers.FRIENDSHIP,
                userId,
                userId);
    }

    public List<FriendshipEntity> findAllByAddresseeIdAndStatusOrderByCreatedAtDesc(Long addresseeId, FriendshipStatus status) {
        return jdbc.query("""
                        select * from friendships
                        where addressee_id = ? and status = ?
                        order by created_at desc
                        """,
                JdbcEntityMappers.FRIENDSHIP,
                addresseeId,
                status.name());
    }

    public List<FriendshipEntity> findAllByRequesterIdAndStatusOrderByCreatedAtDesc(Long requesterId, FriendshipStatus status) {
        return jdbc.query("""
                        select * from friendships
                        where requester_id = ? and status = ?
                        order by created_at desc
                        """,
                JdbcEntityMappers.FRIENDSHIP,
                requesterId,
                status.name());
    }

    public FriendshipEntity save(FriendshipEntity friendship) {
        if (friendship.getId() == null) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement ps = connection.prepareStatement("""
                                insert into friendships
                                (user_low_id, user_high_id, requester_id, addressee_id, status, created_at, updated_at,
                                 requester_cleared_messages_at, addressee_cleared_messages_at)
                                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                                """,
                        Statement.RETURN_GENERATED_KEYS);
                bind(ps, friendship);
                return ps;
            }, keyHolder);
            friendship.setId(keyHolder.getKey().longValue());
        } else {
            jdbc.update("""
                            update friendships
                            set user_low_id = ?, user_high_id = ?, requester_id = ?, addressee_id = ?, status = ?,
                                created_at = ?, updated_at = ?, requester_cleared_messages_at = ?, addressee_cleared_messages_at = ?
                            where id = ?
                            """,
                    friendship.getUserLowId(),
                    friendship.getUserHighId(),
                    friendship.getRequesterId(),
                    friendship.getAddresseeId(),
                    friendship.getStatus().name(),
                    friendship.getCreatedAt(),
                    friendship.getUpdatedAt(),
                    friendship.getRequesterClearedMessagesAt(),
                    friendship.getAddresseeClearedMessagesAt(),
                    friendship.getId());
        }
        return friendship;
    }

    private Optional<FriendshipEntity> queryOne(String sql, Object... args) {
        List<FriendshipEntity> friendships = jdbc.query(sql, JdbcEntityMappers.FRIENDSHIP, args);
        return friendships.stream().findFirst();
    }

    private void bind(PreparedStatement ps, FriendshipEntity friendship) throws java.sql.SQLException {
        ps.setLong(1, friendship.getUserLowId());
        ps.setLong(2, friendship.getUserHighId());
        ps.setLong(3, friendship.getRequesterId());
        ps.setLong(4, friendship.getAddresseeId());
        ps.setString(5, friendship.getStatus().name());
        ps.setObject(6, friendship.getCreatedAt());
        ps.setObject(7, friendship.getUpdatedAt());
        ps.setObject(8, friendship.getRequesterClearedMessagesAt());
        ps.setObject(9, friendship.getAddresseeClearedMessagesAt());
    }
}
