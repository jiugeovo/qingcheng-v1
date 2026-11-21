package com.example.ordering.shop;

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
public class ShopItemRepository {

    private final JdbcTemplate jdbc;

    public ShopItemRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ShopItem> findAll() {
        return findAllByOrderByCreatedAtDesc();
    }

    public List<ShopItem> findAllByOrderByCreatedAtDesc() {
        return jdbc.query("select * from shop_items order by created_at desc", JdbcEntityMappers.SHOP_ITEM);
    }

    public Optional<ShopItem> findById(Long id) {
        return queryOne("select * from shop_items where id = ?", id);
    }

    public Optional<ShopItem> findByName(String name) {
        return queryOne("select * from shop_items where name = ?", name);
    }

    public boolean existsByName(String name) {
        Integer count = jdbc.queryForObject("select count(*) from shop_items where name = ?", Integer.class, name);
        return count != null && count > 0;
    }

    public boolean existsById(Long id) {
        Integer count = jdbc.queryForObject("select count(*) from shop_items where id = ?", Integer.class, id);
        return count != null && count > 0;
    }

    public ShopItem save(ShopItem item) {
        if (item.getId() == null) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement ps = connection.prepareStatement("""
                                insert into shop_items
                                (name, description, image_url, category, price, created_at)
                                values (?, ?, ?, ?, ?, ?)
                                """,
                        Statement.RETURN_GENERATED_KEYS);
                bind(ps, item);
                return ps;
            }, keyHolder);
            item.setId(keyHolder.getKey().longValue());
        } else {
            jdbc.update("""
                            update shop_items
                            set name = ?, description = ?, image_url = ?, category = ?, price = ?, created_at = ?
                            where id = ?
                            """,
                    item.getName(),
                    item.getDescription(),
                    item.getImageUrl(),
                    item.getCategory(),
                    item.getPrice(),
                    item.getCreatedAt(),
                    item.getId());
        }
        return item;
    }

    public void delete(ShopItem item) {
        if (item != null && item.getId() != null) {
            deleteById(item.getId());
        }
    }

    public void deleteById(Long id) {
        jdbc.update("delete from shop_items where id = ?", id);
    }

    private void bind(PreparedStatement ps, ShopItem item) throws java.sql.SQLException {
        ps.setString(1, item.getName());
        ps.setString(2, item.getDescription());
        ps.setString(3, item.getImageUrl());
        ps.setString(4, item.getCategory());
        ps.setBigDecimal(5, item.getPrice());
        ps.setObject(6, item.getCreatedAt());
    }

    private Optional<ShopItem> queryOne(String sql, Object... args) {
        List<ShopItem> items = jdbc.query(sql, JdbcEntityMappers.SHOP_ITEM, args);
        return items.stream().findFirst();
    }
}
