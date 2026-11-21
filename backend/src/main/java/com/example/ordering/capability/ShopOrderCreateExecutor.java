package com.example.ordering.capability;

import com.example.ordering.shop.ShopOrderService;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public final class ShopOrderCreateExecutor implements CapabilityExecutor {
    private final ShopOrderService service;

    public ShopOrderCreateExecutor(ShopOrderService service) {
        this.service = service;
    }

    public String capabilityId() { return ShopOrderCreateCapability.ID; }

    public Map<String, Object> execute(Map<String, Object> input, CapabilityExecutionContext context) {
        var actor = OrderCapabilitySupport.actor(context);
        var request = OrderCapabilitySupport.shopRequest(input);
        String key = OrderCapabilitySupport.idempotencyKey(input);
        return Map.of("order", key == null ? service.createOrder(actor, request) : service.createOrder(actor, request, key));
    }
}
