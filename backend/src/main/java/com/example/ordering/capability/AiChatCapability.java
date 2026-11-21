package com.example.ordering.capability;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component public final class AiChatCapability implements Capability {
    public String id() { return "ai.chat"; }
    public String name() { return "AI 对话"; }
    public String description() { return "与青程 AI 对话并保留历史记录"; }
    public String category() { return CapabilityCategory.INTELLIGENCE.id(); }
    public String route() { return "/api/ai/responses"; }
    public Map<String, Object> inputSchema() {
        // HTTP envelope stays compatible; AiRequestPolicy validates and rebuilds its contents.
        return Map.of("body", Map.of("type", "object", "required", true));
    }
}
