package com.example.ordering.capability;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public final class AnniversaryCreateCapability implements Capability {
    public String id() { return "anniversary.create"; }
    public String name() { return "新增纪念日"; }
    public String description() { return "为当前用户保存一个纪念日，不发送提醒"; }
    public String category() { return CapabilityCategory.LIFESTYLE.id(); }
    public String route() { return "/api/anniversaries"; }
    public Map<String, Object> inputSchema() { return AnniversaryCapabilitySupport.WRITE_SCHEMA; }
    public Map<String, Object> normalizeInput(Map<String, Object> input) { return AnniversaryCapabilitySupport.normalize(input); }
}
