package com.example.ordering.capability;

import com.example.ordering.ai.AiResponseService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public final class AiChatExecutor implements CapabilityExecutor {
    private final AiResponseService service;

    public AiChatExecutor(AiResponseService service) {
        this.service = service;
    }

    public String capabilityId() { return "ai.chat"; }

    public Map<String, Object> execute(Map<String, Object> input, CapabilityExecutionContext context) {
        if (context == null) {
            throw new AccessDeniedException("请先登录");
        }
        new AiChatCapability().validate(input);
        return Map.of("response", service.createResponse(context.toUserAccount(), input.get("body")));
    }
}
