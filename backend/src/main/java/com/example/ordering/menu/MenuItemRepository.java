package com.example.ordering.menu;

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
public class MenuItemRepository {

    private final JdbcTemplate jdbc;

    public MenuItemRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<MenuItem> findAll() {
        return jdbc.query("select * from menu_items order by created_at desc", JdbcEntityMappers.MENU_ITEM);
    }

    public List<MenuItem> findByAvailableTrueOrderByCreatedAtDesc() {
        return jdbc.query("select * from menu_items where available = true order by created_at desc", JdbcEntityMappers.MENU_ITEM);
    }

    public Optional<MenuItem> findById(Long id) {
        return queryOne("select * from menu_items where id = ?", id);
    }

    public Optional<MenuItem> findByName(String name) {
        return queryOne("select * from menu_items where name = ?", name);
    }

    public boolean existsByName(String name) {
        Integer count = jdbc.queryForObject("select count(*) from menu_items where name = ?", Integer.class, name);
        return count != null && count > 0;
    }

    public boolean existsById(Long id) {
        Integer count = jdbc.queryForObject("select count(*) from menu_items where id = ?", Integer.class, id);
        return count != null && count > 0;
    }

    public MenuItem save(MenuItem item) {
        if (item.getId() == null) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement ps = connection.prepareStatement("""
                                insert into menu_items
                                (name, description, image_url, category, price, available, created_at)
                                values (?, ?, ?, ?, ?, ?, ?)
                                """,
                        Statement.RETURN_GENERATED_KEYS);
                bind(ps, item);
                return ps;
            }, keyHolder);
            item.setId(keyHolder.getKey().longValue());
        } else {
            jdbc.update("""
                            update menu_items
                            set name = ?, description = ?, image_url = ?, category = ?, price = ?, available = ?, created_at = ?
                            where id = ?
                            """,
                    item.getName(),
                    item.getDescription(),
                    item.getImageUrl(),
                    item.getCategory(),
                    item.getPrice(),
                    item.isAvailable(),
                    item.getCreatedAt(),
                    item.getId());
        }
        return item;
    }

    public void deleteById(Long id) {
        jdbc.update("delete from menu_items where id = ?", id);
    }

    private void bind(PreparedStatement ps, MenuItem item) throws java.sql.SQLException {
        ps.setString(1, item.getName());
        ps.setString(2, item.getDescription());
        ps.setString(3, item.getImageUrl());
        ps.setString(4, item.getCategory());
        ps.setBigDecimal(5, item.getPrice());
        ps.setBoolean(6, item.isAvailable());
        ps.setObject(7, item.getCreatedAt());
    }

    private Optional<MenuItem> queryOne(String sql, Object... args) {
        List<MenuItem> items = jdbc.query(sql, JdbcEntityMappers.MENU_ITEM, args);
        return items.stream().findFirst();
    }
}
