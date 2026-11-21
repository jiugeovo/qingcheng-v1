package com.example.ordering.rules;

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
public class LoveRuleRepository {

    private final JdbcTemplate jdbc;

    public LoveRuleRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<LoveRuleEntity> findAllByOrderBySortOrderAscIdAsc() {
        return jdbc.query("select * from love_rules order by sort_order asc, id asc", JdbcEntityMappers.LOVE_RULE);
    }

    public Optional<LoveRuleEntity> findTopByOrderBySortOrderDesc() {
        List<LoveRuleEntity> rules = jdbc.query("select * from love_rules order by sort_order desc limit 1", JdbcEntityMappers.LOVE_RULE);
        return rules.stream().findFirst();
    }

    public Optional<LoveRuleEntity> findById(Long id) {
        List<LoveRuleEntity> rules = jdbc.query("select * from love_rules where id = ?", JdbcEntityMappers.LOVE_RULE, id);
        return rules.stream().findFirst();
    }

    public LoveRuleEntity save(LoveRuleEntity rule) {
        if (rule.getId() == null) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement ps = connection.prepareStatement("""
                                insert into love_rules (content, sort_order, created_at, updated_at)
                                values (?, ?, ?, ?)
                                """,
                        Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, rule.getContent());
                ps.setInt(2, rule.getSortOrder());
                ps.setObject(3, rule.getCreatedAt());
                ps.setObject(4, rule.getUpdatedAt());
                return ps;
            }, keyHolder);
            rule.setId(keyHolder.getKey().longValue());
        } else {
            jdbc.update("""
                            update love_rules
                            set content = ?, sort_order = ?, created_at = ?, updated_at = ?
                            where id = ?
                            """,
                    rule.getContent(),
                    rule.getSortOrder(),
                    rule.getCreatedAt(),
                    rule.getUpdatedAt(),
                    rule.getId());
        }
        return rule;
    }

    public void delete(LoveRuleEntity rule) {
        if (rule != null && rule.getId() != null) {
            jdbc.update("delete from love_rules where id = ?", rule.getId());
        }
    }
}
