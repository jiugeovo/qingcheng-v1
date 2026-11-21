package com.example.ordering.capability;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public final class AnniversaryUpdateCapability implements Capability {
    public String id() { return "anniversary.update"; }
    public String name() { return "修改纪念日"; }
    public String description() { return "修改当前用户指定纪念日的标题、日期和备注"; }
    public String category() { return CapabilityCategory.LIFESTYLE.id(); }
    public String route() { return "/api/anniversaries/{id}"; }
    public Map<String, Object> inputSchema() { return AnniversaryCapabilitySupport.UPDATE_SCHEMA; }
    public Map<String, Object> normalizeInput(Map<String, Object> input) { return AnniversaryCapabilitySupport.normalize(input); }
}
