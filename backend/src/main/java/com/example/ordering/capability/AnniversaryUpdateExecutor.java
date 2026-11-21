package com.example.ordering.capability;

import com.example.ordering.anniversary.AnniversaryService;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public final class AnniversaryUpdateExecutor implements CapabilityExecutor {
    private final AnniversaryService service;
    private final Validator validator;

    public AnniversaryUpdateExecutor(AnniversaryService service, Validator validator) {
        this.service = service;
        this.validator = validator;
    }

    public String capabilityId() { return "anniversary.update"; }

    public Map<String, Object> execute(Map<String, Object> input, CapabilityExecutionContext context) {
        return Map.of("item", service.update(context.userId(), AnniversaryCapabilitySupport.id(input),
                AnniversaryCapabilitySupport.request(input, validator)));
    }
}
