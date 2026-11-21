package com.example.ordering.capability;

import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class WeatherCurrentCapability implements Capability {
    public String id() { return "weather.current"; }
    public String name() { return "查询天气"; }
    public String description() { return "查询指定地点的实时天气"; }
    public String category() { return CapabilityCategory.LIFESTYLE.id(); }
    public String route() { return "/api/weather"; }
    public Map<String, Object> inputSchema() { return Map.of("query", Map.of("type", "string", "required", false)); }
}
