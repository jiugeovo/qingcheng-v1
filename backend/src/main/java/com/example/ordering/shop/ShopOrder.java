package com.example.ordering.shop;

import com.example.ordering.order.OrderStatus;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.example.ordering.order.OrderAction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ShopOrder(
        Long id,
        Long userId,
        String username,
        String nickname,
        List<OrderLine> items,
        int totalQuantity,
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
    public ShopOrder(Long id, Long userId, String username, String nickname, List<OrderLine> items,
                             int totalQuantity, BigDecimal totalAmount, String remark, OrderStatus status,
                             LocalDateTime createdAt, LocalDateTime updatedAt) {
        this(id, userId, username, nickname, items, totalQuantity, totalAmount, remark, status, createdAt, updatedAt,
                null, null, null, null);
    }

    public record OrderLine(
            Long shopItemId,
            String name,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal subtotal
    ) {
    }
}
