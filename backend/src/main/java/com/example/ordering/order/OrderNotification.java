package com.example.ordering.order;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderNotification(
        Long id,
        Long userId,
        String username,
        String nickname,
        List<OrderLine> items,
        BigDecimal totalAmount,
        String remark,
        OrderStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) Long handlerUserId,
        @JsonInclude(JsonInclude.Include.NON_NULL) String orderKind,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<OrderAction> allowedActions,
        @JsonInclude(JsonInclude.Include.NON_NULL) String displayNumber
) {
    public OrderNotification(Long id, Long userId, String username, String nickname, List<OrderLine> items,
                             BigDecimal totalAmount, String remark, OrderStatus status,
                             LocalDateTime createdAt, LocalDateTime updatedAt) {
        this(id, userId, username, nickname, items, totalAmount, remark, status, createdAt, updatedAt,
                null, null, null, null);
    }

    public record OrderLine(
            Long menuItemId,
            String name,
            BigDecimal price,
            int quantity,
            BigDecimal subtotal
    ) {
    }
}
