package com.example.ordering.capability;

import com.example.ordering.anniversary.AnniversaryService;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public final class AnniversaryGetExecutor implements CapabilityExecutor {
    private final AnniversaryService service;

    public AnniversaryGetExecutor(AnniversaryService service) {
        this.service = service;
    }

    public String capabilityId() { return "anniversary.get"; }

    public Map<String, Object> execute(Map<String, Object> input, CapabilityExecutionContext context) {
        return Map.of("item", service.get(context.userId(), AnniversaryCapabilitySupport.id(input)));
    }
}
