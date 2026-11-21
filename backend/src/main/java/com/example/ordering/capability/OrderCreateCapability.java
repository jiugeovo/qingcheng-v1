package com.example.ordering.capability;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public final class OrderCreateCapability implements Capability {
    public static final String ID = "order.create";

    public String id() { return ID; }
    public String name() { return "创建点菜订单"; }
    public String description() { return "从现有菜单创建点菜订单"; }
    public String category() { return CapabilityCategory.ORDERING.id(); }
    public String route() { return "/api/orders"; }
    public Map<String, Object> inputSchema() { return OrderCapabilitySupport.MENU_SCHEMA; }
}
