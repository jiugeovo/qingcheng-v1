package com.example.ordering.admin.audit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;

@Repository
public class AdminAuditLogRepository {

    private final JdbcTemplate jdbc;

    public AdminAuditLogRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public AdminAuditLogEntity save(AdminAuditLogEntity log) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                            insert into admin_audit_logs
                            (actor_user_id, actor_username, action, target_type, target_id, before_value,
                             after_value, amount_delta, detail, created_at)
                            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    Statement.RETURN_GENERATED_KEYS);
            if (log.getActorUserId() == null) {
                ps.setObject(1, null);
            } else {
                ps.setLong(1, log.getActorUserId());
            }
            ps.setString(2, log.getActorUsername());
            ps.setString(3, log.getAction());
            ps.setString(4, log.getTargetType());
            ps.setString(5, log.getTargetId());
            ps.setString(6, log.getBeforeValue());
            ps.setString(7, log.getAfterValue());
            ps.setBigDecimal(8, log.getAmountDelta());
            ps.setString(9, log.getDetail());
            ps.setObject(10, log.getCreatedAt());
            return ps;
        }, keyHolder);
        log.setId(keyHolder.getKey().longValue());
        return log;
    }
}
