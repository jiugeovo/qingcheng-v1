package com.example.ordering.order;

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
public class OrderNotificationRepository {

    private final JdbcTemplate jdbc;

    public OrderNotificationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<OrderNotificationEntity> findAllByOrderByCreatedAtDesc() {
        List<OrderNotificationEntity> orders = jdbc.query("select * from order_notifications order by created_at desc",
                JdbcEntityMappers.MENU_ORDER);
        orders.forEach(this::loadItems);
        return orders;
    }

    public List<OrderNotificationEntity> findAllByUserIdOrderByCreatedAtDesc(Long userId) {
        List<OrderNotificationEntity> orders = jdbc.query("select * from order_notifications where user_id = ? order by created_at desc",
                JdbcEntityMappers.MENU_ORDER,
                userId);
        orders.forEach(this::loadItems);
        return orders;
    }

    public List<OrderNotificationEntity> findAllByHandlerUserId(Long userId) {
        List<OrderNotificationEntity> orders = jdbc.query(
                "select * from order_notifications where handler_user_id = ? order by created_at desc, id desc",
                JdbcEntityMappers.MENU_ORDER, userId);
        orders.forEach(this::loadItems);
        return orders;
    }

    public Optional<OrderNotificationEntity> findById(Long id) {
        return queryOne("select * from order_notifications where id = ?", id);
    }

    public Optional<OrderNotificationEntity> findLockedById(Long id) {
        return queryOne("select * from order_notifications where id = ? for update", id);
    }

    public Optional<Long> findUserIdById(Long id) {
        return jdbc.query("select user_id from order_notifications where id = ?", (rs, row) -> rs.getLong(1), id)
                .stream().findFirst();
    }

    public long nextDisplayNumber() {
        return StoreOrderNumbers.next(jdbc, StoreOrderNumbers.Store.PIGGY_SHOP);
    }

    public OrderNotificationEntity save(OrderNotificationEntity order) {
        if (order.getId() == null) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement ps = connection.prepareStatement("""
                                insert into order_notifications
                                (user_id, username, nickname, total_amount, remark, status, created_at, updated_at, handler_user_id, display_number)
                                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                                """,
                        Statement.RETURN_GENERATED_KEYS);
                bind(ps, order);
                return ps;
            }, keyHolder);
            order.setId(keyHolder.getKey().longValue());
        } else {
            jdbc.update("""
                            update order_notifications
                            set user_id = ?, username = ?, nickname = ?, total_amount = ?, remark = ?,
                                status = ?, created_at = ?, updated_at = ?
                            where id = ?
                            """,
                    order.getUserId(),
                    order.getUsername(),
                    order.getNickname(),
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

    public void delete(OrderNotificationEntity order) {
        if (order == null || order.getId() == null) {
            return;
        }
        jdbc.update("delete from order_notification_items where order_notification_id = ?", order.getId());
        jdbc.update("delete from order_notifications where id = ?", order.getId());
    }

    private Optional<OrderNotificationEntity> queryOne(String sql, Object... args) {
        List<OrderNotificationEntity> orders = jdbc.query(sql, JdbcEntityMappers.MENU_ORDER, args);
        orders.forEach(this::loadItems);
        return orders.stream().findFirst();
    }

    private void bind(PreparedStatement ps, OrderNotificationEntity order) throws java.sql.SQLException {
        ps.setLong(1, order.getUserId());
        ps.setString(2, order.getUsername());
        ps.setString(3, order.getNickname());
        ps.setBigDecimal(4, order.getTotalAmount());
        ps.setString(5, order.getRemark());
        ps.setString(6, order.getStatus().name());
        ps.setObject(7, order.getCreatedAt());
        ps.setObject(8, order.getUpdatedAt());
        ps.setObject(9, order.getHandlerUserId(), java.sql.Types.BIGINT);
        ps.setObject(10, order.getDisplayNumber(), java.sql.Types.BIGINT);
    }

    private void loadItems(OrderNotificationEntity order) {
        List<OrderNotificationLineEntity> items = jdbc.query("""
                        select * from order_notification_items
                        where order_notification_id = ?
                        order by line_index asc
                        """,
                (rs, rowNum) -> {
                    OrderNotificationLineEntity line = new OrderNotificationLineEntity();
                    line.setMenuItemId(rs.getLong("menu_item_id"));
                    line.setName(rs.getString("name"));
                    line.setPrice(rs.getBigDecimal("price"));
                    line.setQuantity(rs.getInt("quantity"));
                    line.setSubtotal(rs.getBigDecimal("subtotal"));
                    line.setItemKind(rs.getString("item_kind"));
                    return line;
                },
                order.getId());
        order.setItems(items);
    }

    private void replaceItems(OrderNotificationEntity order) {
        jdbc.update("delete from order_notification_items where order_notification_id = ?", order.getId());
        for (int i = 0; i < order.getItems().size(); i++) {
            OrderNotificationLineEntity line = order.getItems().get(i);
            jdbc.update("""
                            insert into order_notification_items
                            (order_notification_id, menu_item_id, name, price, quantity, subtotal, line_index, item_kind)
                            values (?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    order.getId(),
                    line.getMenuItemId(),
                    line.getName(),
                    line.getPrice(),
                    line.getQuantity(),
                    line.getSubtotal(),
                    i,
                    line.getItemKind());
        }
    }
}
