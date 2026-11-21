package com.example.ordering.capability;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public final class ShopOrderCreateCapability implements Capability {
    public static final String ID = "shop.order.create";

    public String id() { return ID; }
    public String name() { return "创建宝宝商店订单"; }
    public String description() { return "从现有宝宝商店商品创建订单"; }
    public String category() { return CapabilityCategory.ORDERING.id(); }
    public String route() { return "/api/shop-orders"; }
    public Map<String, Object> inputSchema() { return OrderCapabilitySupport.SHOP_SCHEMA; }
}
