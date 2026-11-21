package com.example.ordering.shop;

import com.example.ordering.common.JdbcEntityMappers;
import com.example.ordering.order.StoreOrderNumbers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

@Repository
public class ShopOrderRepository {

    private final JdbcTemplate jdbc;

    public ShopOrderRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ShopOrderEntity> findAllByOrderByCreatedAtDesc() {
        List<ShopOrderEntity> orders = jdbc.query("select * from shop_orders order by created_at desc", JdbcEntityMappers.SHOP_ORDER);
        orders.forEach(this::loadItems);
        return orders;
    }

    public List<ShopOrderEntity> findAllByUserIdOrderByCreatedAtDesc(Long userId) {
        List<ShopOrderEntity> orders = jdbc.query("select * from shop_orders where user_id = ? order by created_at desc",
                JdbcEntityMappers.SHOP_ORDER,
                userId);
        orders.forEach(this::loadItems);
        return orders;
    }

    public List<ShopOrderEntity> findAllByHandlerUserId(Long userId) {
        List<ShopOrderEntity> orders = jdbc.query(
                "select * from shop_orders where handler_user_id = ? order by created_at desc, id desc",
                JdbcEntityMappers.SHOP_ORDER, userId);
        orders.forEach(this::loadItems);
        return orders;
    }

    public Optional<ShopOrderEntity> findById(Long id) {
        return queryOne("select * from shop_orders where id = ?", id);
    }

    public Optional<ShopOrderEntity> findLockedById(Long id) {
        return queryOne("select * from shop_orders where id = ? for update", id);
    }

    public Optional<Long> findUserIdById(Long id) {
        return jdbc.query("select user_id from shop_orders where id = ?", (rs, row) -> rs.getLong(1), id)
                .stream().findFirst();
    }

    public long nextDisplayNumber() {
        return StoreOrderNumbers.next(jdbc, StoreOrderNumbers.Store.BABY_SHOP);
    }

    public ShopOrderEntity save(ShopOrderEntity order) {
        if (order.getId() == null) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement ps = connection.prepareStatement("""
                                insert into shop_orders
                                (user_id, username, nickname, total_quantity, total_amount, remark, status, created_at, updated_at, handler_user_id, display_number)
                                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                                """,
                        Statement.RETURN_GENERATED_KEYS);
                bind(ps, order);
                return ps;
            }, keyHolder);
            order.setId(keyHolder.getKey().longValue());
        } else {
            jdbc.update("""
                            update shop_orders
                            set user_id = ?, username = ?, nickname = ?, total_quantity = ?, total_amount = ?,
                                remark = ?, status = ?, created_at = ?, updated_at = ?
                            where id = ?
                            """,
                    order.getUserId(),
                    order.getUsername(),
                    order.getNickname(),
                    order.getTotalQuantity(),
                    order.getTotalAmount(),
                    order.getRemark(),
                    order.getStatus().name(),
                    order.getCreatedAt(),
                    order.getUpdatedAt(),
                    order.getId());
        }
        replaceItems(order);
        return order;
    }

    public void delete(ShopOrderEntity order) {
        if (order == null || order.getId() == null) {
            return;
        }
        jdbc.update("delete from shop_order_items where shop_order_id = ?", order.getId());
        jdbc.update("delete from shop_orders where id = ?", order.getId());
    }

    private Optional<ShopOrderEntity> queryOne(String sql, Object... args) {
        List<ShopOrderEntity> orders = jdbc.query(sql, JdbcEntityMappers.SHOP_ORDER, args);
        orders.forEach(this::loadItems);
        return orders.stream().findFirst();
    }

    private void bind(PreparedStatement ps, ShopOrderEntity order) throws java.sql.SQLException {
        ps.setLong(1, order.getUserId());
        ps.setString(2, order.getUsername());
        ps.setString(3, order.getNickname());
        ps.setInt(4, order.getTotalQuantity());
        ps.setBigDecimal(5, order.getTotalAmount());
        ps.setString(6, order.getRemark());
        ps.setString(7, order.getStatus().name());
        ps.setObject(8, order.getCreatedAt());
        ps.setObject(9, order.getUpdatedAt());
        ps.setObject(10, order.getHandlerUserId(), java.sql.Types.BIGINT);
        ps.setObject(11, order.getDisplayNumber(), java.sql.Types.BIGINT);
    }

    private void loadItems(ShopOrderEntity order) {
        List<ShopOrderLineEntity> items = jdbc.query("""
                        select * from shop_order_items
                        where shop_order_id = ?
                        order by line_index asc
                        """,
                (rs, rowNum) -> {
                    ShopOrderLineEntity line = new ShopOrderLineEntity();
                    line.setShopItemId(rs.getLong("shop_item_id"));
                    line.setName(rs.getString("name"));
                    line.setQuantity(rs.getInt("quantity"));
                    line.setUnitPrice(rs.getBigDecimal("unit_price"));
                    line.setSubtotal(rs.getBigDecimal("subtotal"));
                    return line;
                },
                order.getId());
        order.setItems(items);
    }

    private void replaceItems(ShopOrderEntity order) {
        jdbc.update("delete from shop_order_items where shop_order_id = ?", order.getId());
        for (int i = 0; i < order.getItems().size(); i++) {
            ShopOrderLineEntity line = order.getItems().get(i);
            jdbc.update("""
                            insert into shop_order_items
                            (shop_order_id, shop_item_id, name, quantity, unit_price, subtotal, line_index)
                            values (?, ?, ?, ?, ?, ?, ?)
                            """,
                    order.getId(),
                    line.getShopItemId(),
                    line.getName(),
                    line.getQuantity(),
                    line.getUnitPrice(),
                    line.getSubtotal(),
                    i);
        }
    }
}
