package com.example.ordering.capability;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public final class AnniversaryDeleteCapability implements Capability {
    public String id() { return "anniversary.delete"; }
    public String name() { return "删除纪念日"; }
    public String description() { return "删除当前用户指定的纪念日"; }
    public String category() { return CapabilityCategory.LIFESTYLE.id(); }
    public String route() { return "/api/anniversaries/{id}"; }
    public Map<String, Object> inputSchema() { return AnniversaryCapabilitySupport.ID_SCHEMA; }
}
