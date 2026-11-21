package com.example.ordering.anniversary;

import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class AnniversaryRepository {

    private static final String COLUMNS =
            "id, user_id, title, anniversary_date, note, repeat_yearly, anniversary_type, created_at, updated_at";

    private static final RowMapper<AnniversaryEntity> ROW_MAPPER = (rs, rowNum) -> new AnniversaryEntity(
            rs.getLong("id"),
            rs.getLong("user_id"),
            rs.getString("title"),
            rs.getObject("anniversary_date", LocalDate.class),
            rs.getString("note"),
            rs.getBoolean("repeat_yearly"),
            AnniversaryType.fromId(rs.getString("anniversary_type")),
            rs.getObject("created_at", LocalDateTime.class),
            rs.getObject("updated_at", LocalDateTime.class)
    );

    private final JdbcTemplate jdbc;

    public AnniversaryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<AnniversaryEntity> findAllShared() {
        return List.copyOf(jdbc.query("select " + COLUMNS
                + " from anniversaries order by anniversary_date asc, id asc", ROW_MAPPER, new Object[0]));
    }

    public Optional<AnniversaryEntity> findSharedById(long id) {
        return jdbc.query("select " + COLUMNS + " from anniversaries where id = ?", ROW_MAPPER, id)
                .stream().findFirst();
    }

    public List<AnniversaryEntity> findAllByUserId(long userId) {
        return List.copyOf(jdbc.query(
                "select " + COLUMNS + " from anniversaries where user_id = ? order by anniversary_date asc, id asc",
                ROW_MAPPER, userId));
    }

    public Optional<AnniversaryEntity> findByIdAndUserId(long id, long userId) {
        return jdbc.query(
                "select " + COLUMNS + " from anniversaries where id = ? and user_id = ?",
                ROW_MAPPER, id, userId).stream().findFirst();
    }

    /** Must be called inside the same transaction as the subsequent update. */
    public Optional<AnniversaryEntity> findByIdAndUserIdForUpdate(long id, long userId) {
        return jdbc.query(
                "select " + COLUMNS + " from anniversaries where id = ? and user_id = ? for update",
                ROW_MAPPER, id, userId).stream().findFirst();
    }

    /** Administrative access only; authorization is enforced by AnniversaryService. */
    public Optional<AnniversaryEntity> findByIdForUpdate(long id) {
        return jdbc.query("select " + COLUMNS + " from anniversaries where id = ? for update", ROW_MAPPER, id)
                .stream().findFirst();
    }

    public boolean deleteById(long id) {
        return jdbc.update("delete from anniversaries where id = ?", id) == 1;
    }

    public AnniversaryEntity insert(AnniversaryEntity entity) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    insert into anniversaries
                        (user_id, title, anniversary_date, note, repeat_yearly, anniversary_type, created_at, updated_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, entity.userId());
            statement.setString(2, entity.title());
            statement.setObject(3, entity.date());
            statement.setString(4, entity.note());
            statement.setBoolean(5, entity.repeatYearly());
            statement.setString(6, entity.type().id());
            statement.setObject(7, entity.createdAt());
            statement.setObject(8, entity.updatedAt());
            return statement;
        }, keyHolder);

        Number generatedId = keyHolder.getKey();
        if (generatedId == null) {
            throw new DataRetrievalFailureException("Database did not return a generated anniversary ID");
        }
        return new AnniversaryEntity(
                generatedId.longValue(), entity.userId(), entity.title(), entity.date(), entity.note(),
                entity.repeatYearly(), entity.type(), entity.createdAt(), entity.updatedAt());
    }

    public boolean update(AnniversaryEntity entity) {
        Objects.requireNonNull(entity.id(), "Anniversary ID is required for an update");
        return jdbc.update("""
                        update anniversaries
                        set title = ?, anniversary_date = ?, note = ?, repeat_yearly = ?, anniversary_type = ?, updated_at = ?
                        where id = ? and user_id = ?
                        """,
                entity.title(), entity.date(), entity.note(), entity.repeatYearly(), entity.type().id(), entity.updatedAt(),
                entity.id(), entity.userId()) == 1;
    }

    public boolean deleteByIdAndUserId(long id, long userId) {
        return jdbc.update("delete from anniversaries where id = ? and user_id = ?", id, userId) == 1;
    }
}
