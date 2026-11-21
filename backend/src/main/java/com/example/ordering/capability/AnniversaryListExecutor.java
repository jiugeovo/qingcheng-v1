package com.example.ordering.capability;

import com.example.ordering.anniversary.AnniversaryService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public final class AnniversaryListExecutor implements CapabilityExecutor {
    private final AnniversaryService service;

    public AnniversaryListExecutor(AnniversaryService service) {
        this.service = service;
    }

    public String capabilityId() { return "anniversary.list"; }

    public Map<String, Object> execute(Map<String, Object> input, CapabilityExecutionContext context) {
        return Map.of("items", List.copyOf(service.list(context.userId())));
    }
}
