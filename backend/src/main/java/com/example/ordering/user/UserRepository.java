package com.example.ordering.user;

import com.example.ordering.common.JdbcEntityMappers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public class UserRepository {

    private final JdbcTemplate jdbc;

    public UserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<UserAccount> findAll() {
        return jdbc.query("select * from users order by id asc", JdbcEntityMappers.USER);
    }

    public List<UserAccount> findAllById(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", ids.stream().map(id -> "?").toList());
        return jdbc.query("select * from users where id in (" + placeholders + ")",
                JdbcEntityMappers.USER,
                ids.toArray());
    }

    public Optional<UserAccount> findById(Long id) {
        return queryOne("select * from users where id = ?", id);
    }

    public Optional<UserAccount> findLockedById(Long id) {
        return queryOne("select * from users where id = ? for update", id);
    }

    public Optional<UserAccount> findByUsername(String username) {
        return queryOne("select * from users where username = ?", username);
    }

    public List<UserAccount> searchEnabledUsers(Long currentUserId, String query, Object ignoredPageable) {
        String pattern = "%" + query.toLowerCase() + "%";
        return jdbc.query("""
                        select * from users
                        where enabled = true
                          and id <> ?
                          and (lower(username) like ? or lower(nickname) like ?)
                        order by username asc
                        limit 20
                        """,
                JdbcEntityMappers.USER,
                currentUserId,
                pattern,
                pattern);
    }

    public List<UserAccount> findAllByRoleAndEnabledTrue(UserRole role) {
        return jdbc.query("select * from users where role = ? and enabled = true order by id asc",
                JdbcEntityMappers.USER,
                role.name());
    }

    public List<UserAccount> findAllByRoleInAndEnabledTrue(List<UserRole> roles) {
        if (roles == null || roles.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", roles.stream().map(role -> "?").toList());
        return jdbc.query("select * from users where role in (" + placeholders + ") and enabled = true order by id asc",
                JdbcEntityMappers.USER,
                roles.stream().map(UserRole::name).toArray());
    }

    public boolean existsByUsername(String username) {
        Integer count = jdbc.queryForObject("select count(*) from users where username = ?", Integer.class, username);
        return count != null && count > 0;
    }

    public boolean existsByPhone(String phone) {
        Integer count = jdbc.queryForObject("select count(*) from users where phone = ?", Integer.class, phone);
        return count != null && count > 0;
    }

    public void updateRole(Long id, UserRole role) {
        jdbc.update("update users set role = ? where id = ?", role.name(), id);
    }

    public void updateNickname(Long id, String nickname) {
        jdbc.update("update users set nickname = ? where id = ?", nickname, id);
    }

    public void updateEnabled(Long id, boolean enabled) {
        jdbc.update("update users set enabled = ? where id = ?", enabled, id);
    }

    public void updateQingchengCoins(Long id, BigDecimal coins) {
        jdbc.update("update users set qingcheng_coins = ? where id = ?", coins, id);
    }

    public void updateLastLoginAt(Long id, LocalDateTime lastLoginAt) {
        jdbc.update("update users set last_login_at = ? where id = ?", lastLoginAt, id);
    }

    public UserAccount save(UserAccount user) {
        if (user.getId() == null) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement ps = connection.prepareStatement("""
                                insert into users
                                (username, password_hash, nickname, phone, role, enabled, qingcheng_coins,
                                 last_check_in_date, created_at, last_login_at)
                                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                                """,
                        Statement.RETURN_GENERATED_KEYS);
                bindUser(ps, user, false);
                return ps;
            }, keyHolder);
            user.setId(keyHolder.getKey().longValue());
        } else {
            jdbc.update("""
                            update users
                            set username = ?, password_hash = ?, nickname = ?, phone = ?, role = ?, enabled = ?,
                                qingcheng_coins = ?, last_check_in_date = ?, created_at = ?, last_login_at = ?
                            where id = ?
                            """,
                    user.getUsername(),
                    user.getPasswordHash(),
                    user.getNickname(),
                    user.getPhone(),
                    user.getRole().name(),
                    user.isEnabled(),
                    user.getQingchengCoins(),
                    user.getLastCheckInDate(),
                    user.getCreatedAt(),
                    user.getLastLoginAt(),
                    user.getId());
        }
        return user;
    }

    public void deleteById(Long id) {
        jdbc.update("delete from users where id = ?", id);
    }

    private void bindUser(PreparedStatement ps, UserAccount user, boolean includeId) throws java.sql.SQLException {
        ps.setString(1, user.getUsername());
        ps.setString(2, user.getPasswordHash());
        ps.setString(3, user.getNickname());
        ps.setString(4, user.getPhone());
        ps.setString(5, user.getRole().name());
        ps.setBoolean(6, user.isEnabled());
        ps.setBigDecimal(7, user.getQingchengCoins());
        ps.setObject(8, user.getLastCheckInDate());
        ps.setObject(9, user.getCreatedAt());
        ps.setObject(10, user.getLastLoginAt());
        if (includeId) {
            ps.setLong(11, user.getId());
        }
    }

    private Optional<UserAccount> queryOne(String sql, Object... args) {
        List<UserAccount> users = jdbc.query(sql, JdbcEntityMappers.USER, args);
        return users.stream().findFirst();
    }
}
