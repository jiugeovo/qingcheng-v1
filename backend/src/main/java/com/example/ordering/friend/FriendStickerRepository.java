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
public class FriendStickerRepository {

    private final JdbcTemplate jdbc;

    public FriendStickerRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<FriendStickerEntity> findAllByUserId(Long userId) {
        return jdbc.query("""
                        select * from friend_stickers
                        where user_id = ?
                        order by created_at desc, id desc
                        """,
                JdbcEntityMappers.FRIEND_STICKER,
                userId);
    }

    public Optional<FriendStickerEntity> findByIdAndUserId(Long id, Long userId) {
        return jdbc.query("""
                        select * from friend_stickers
                        where id = ? and user_id = ?
                        limit 1
                        """,
                JdbcEntityMappers.FRIEND_STICKER,
                id,
                userId).stream().findFirst();
    }

    public long countByUserId(Long userId) {
        Long count = jdbc.queryForObject(
                "select count(*) from friend_stickers where user_id = ?", Long.class, userId);
        return count == null ? 0L : count;
    }

    public FriendStickerEntity save(FriendStickerEntity sticker) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                            insert into friend_stickers (user_id, image_url, created_at)
                            values (?, ?, ?)
                            """,
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, sticker.getUserId());
            ps.setString(2, sticker.getImageUrl());
            ps.setObject(3, sticker.getCreatedAt());
            return ps;
        }, keyHolder);
        sticker.setId(keyHolder.getKey().longValue());
        return sticker;
    }
}
