package com.example.ordering.capability;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public final class AnniversaryGetCapability implements Capability {
    public String id() { return "anniversary.get"; }
    public String name() { return "查看纪念日详情"; }
    public String description() { return "查看共享纪念日的详情"; }
    public String category() { return CapabilityCategory.LIFESTYLE.id(); }
    public String route() { return "/api/anniversaries/{id}"; }
    public Map<String, Object> inputSchema() { return AnniversaryCapabilitySupport.ID_SCHEMA; }
}
