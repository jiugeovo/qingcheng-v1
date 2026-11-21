package com.example.ordering.order;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;

public enum OrderAction {
    ACCEPT, COMPLETE, CANCEL;

    public static List<OrderAction> allowed(long actorId, Long buyerId, Long handlerId, OrderStatus current) {
        if (handlerId == null) return List.of();
        if (Objects.equals(actorId, handlerId)) {
            return switch (current) {
                case PENDING -> List.of(ACCEPT, COMPLETE, CANCEL);
                case PREPARING -> List.of(COMPLETE, CANCEL);
                default -> List.of();
            };
        }
        return Objects.equals(actorId, buyerId) && current == OrderStatus.PENDING ? List.of(CANCEL) : List.of();
    }

    public OrderStatus transition(long actorId, Long buyerId, Long handlerId, OrderStatus current) {
        boolean buyer = Objects.equals(actorId, buyerId);
        boolean handler = Objects.equals(actorId, handlerId);
        if (!buyer && !handler) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在");
        if ((!handler && this != CANCEL) || handlerId == null) {
            throw new AccessDeniedException("你不能进行这项订单操作");
        }
        if (!allowed(actorId, buyerId, handlerId, current).contains(this)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "订单状态已变化，请刷新订单");
        }
        return switch (this) {
            case ACCEPT -> OrderStatus.PREPARING;
            case COMPLETE -> OrderStatus.COMPLETED;
            case CANCEL -> OrderStatus.CANCELLED;
        };
    }
}
