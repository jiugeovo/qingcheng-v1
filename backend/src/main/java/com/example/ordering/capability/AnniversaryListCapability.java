package com.example.ordering.capability;

import org.springframework.stereotype.Component;

@Component
public final class AnniversaryListCapability implements Capability {
    public String id() { return "anniversary.list"; }
    public String name() { return "查看纪念日"; }
    public String description() { return "查看所有登录用户共享的纪念日及下次日期"; }
    public String category() { return CapabilityCategory.LIFESTYLE.id(); }
    public String route() { return "/api/anniversaries"; }
}
