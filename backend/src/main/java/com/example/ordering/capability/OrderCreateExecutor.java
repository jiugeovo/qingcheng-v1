package com.example.ordering.capability;

import com.example.ordering.order.OrderService;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public final class OrderCreateExecutor implements CapabilityExecutor {
    private final OrderService service;

    public OrderCreateExecutor(OrderService service) {
        this.service = service;
    }

    public String capabilityId() { return OrderCreateCapability.ID; }

    public Map<String, Object> execute(Map<String, Object> input, CapabilityExecutionContext context) {
        var actor = OrderCapabilitySupport.actor(context);
        var request = OrderCapabilitySupport.menuRequest(input);
        String key = OrderCapabilitySupport.idempotencyKey(input);
        return Map.of("order", key == null ? service.createOrder(actor, request) : service.createOrder(actor, request, key));
    }
}
