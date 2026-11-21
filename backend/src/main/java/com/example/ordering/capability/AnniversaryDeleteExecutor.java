package com.example.ordering.capability;

import com.example.ordering.anniversary.AnniversaryService;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public final class AnniversaryDeleteExecutor implements CapabilityExecutor {
    private final AnniversaryService service;

    public AnniversaryDeleteExecutor(AnniversaryService service) {
        this.service = service;
    }

    public String capabilityId() { return "anniversary.delete"; }

    public Map<String, Object> execute(Map<String, Object> input, CapabilityExecutionContext context) {
        service.delete(context.userId(), AnniversaryCapabilitySupport.id(input));
        return Map.of();
    }
}
