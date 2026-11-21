package com.example.ordering.capability;

import com.example.ordering.order.CreateOrderRequest;
import com.example.ordering.order.OrderItemRequest;
import com.example.ordering.shop.CreateShopOrderRequest;
import com.example.ordering.shop.ShopOrderItemRequest;
import com.example.ordering.user.UserAccount;
import org.springframework.security.access.AccessDeniedException;

import java.util.Collection;
import java.util.Map;

/** Converts validated internal commands without changing the existing order DTO contracts. */
final class OrderCapabilitySupport {
    static final Map<String, Object> MENU_SCHEMA = schema("menuItemId");
    static final Map<String, Object> SHOP_SCHEMA = schema("shopItemId");

    private OrderCapabilitySupport() {
    }

    static UserAccount actor(CapabilityExecutionContext context) {
        if (context == null) {
            throw new AccessDeniedException("请先登录");
        }
        return context.toUserAccount();
    }

    static CreateOrderRequest menuRequest(Map<String, Object> input) {
        new OrderCreateCapability().validate(input);
        return new CreateOrderRequest(((Collection<?>) input.get("items")).stream()
                .map(item -> (Map<?, ?>) item)
                .map(item -> new OrderItemRequest(((Number) item.get("menuItemId")).longValue(),
                        ((Number) item.get("quantity")).intValue()))
                .toList(), (String) input.get("remark"));
    }

    static CreateShopOrderRequest shopRequest(Map<String, Object> input) {
        new ShopOrderCreateCapability().validate(input);
        return new CreateShopOrderRequest(((Collection<?>) input.get("items")).stream()
                .map(item -> (Map<?, ?>) item)
                .map(item -> new ShopOrderItemRequest(((Number) item.get("shopItemId")).longValue(),
                        ((Number) item.get("quantity")).intValue()))
                .toList(), (String) input.get("remark"));
    }

    static String idempotencyKey(Map<String, Object> input) {
        return (String) input.get("idempotencyKey");
    }

    private static Map<String, Object> schema(String itemId) {
        return Map.of(
                "items", Map.of("type", "array", "required", true, "minItems", 1,
                        "items", Map.of("type", "object", "required", true,
                                "properties", Map.of(
                                        // Existing DTOs require an ID; existence stays a domain-service check.
                                        itemId, Map.of("type", "integer", "required", true),
                                        "quantity", Map.of("type", "integer", "required", true,
                                                "minimum", 1, "maximum", Integer.MAX_VALUE)))),
                "remark", Map.of("type", "string", "required", false),
                "idempotencyKey", Map.of("type", "string", "required", false, "maxLength", 128));
    }
}
