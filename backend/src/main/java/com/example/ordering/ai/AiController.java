package com.example.ordering.ai;

import com.example.ordering.capability.CapabilityExecutionContext;
import com.example.ordering.capability.CapabilityRegistry;
import com.example.ordering.common.ApiResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/** HTTP compatibility adapter; response orchestration lives in the AI domain service. */
@RestController
public class AiController {
    private final AiConversationService conversations;
    private final CapabilityRegistry capabilities;

    public AiController(AiConversationService conversations, CapabilityRegistry capabilities) {
        this.conversations = conversations;
        this.capabilities = capabilities;
    }

    @GetMapping("/api/ai/conversations")
    public ApiResponse<List<AiConversationSummary>> listConversations(Authentication authentication) {
        return ApiResponse.ok(conversations.listConversations(CapabilityExecutionContext.from(authentication).toUserAccount()));
    }

    @GetMapping("/api/ai/conversations/page")
    public ApiResponse<AiConversationPage> listConversationsPage(
            Authentication authentication,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String cursor
    ) {
        return ApiResponse.ok(conversations.listConversationsPage(
                CapabilityExecutionContext.from(authentication).toUserAccount(), limit, cursor));
    }

    @GetMapping("/api/ai/conversations/{id}/messages")
    public ApiResponse<List<AiMessageResponse>> listMessages(Authentication authentication, @PathVariable Long id) {
        return ApiResponse.ok(conversations.listMessages(CapabilityExecutionContext.from(authentication).toUserAccount(), id));
    }

    @DeleteMapping("/api/ai/conversations/{id}")
    public ApiResponse<Void> deleteConversation(Authentication authentication, @PathVariable Long id) {
        conversations.deleteConversation(CapabilityExecutionContext.from(authentication).toUserAccount(), id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/ai/responses")
    public ApiResponse<JsonNode> createResponse(Authentication authentication, @RequestBody ObjectNode requestBody) {
        if (requestBody == null || requestBody.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求体不能为空");
        }
        CapabilityExecutionContext context = CapabilityExecutionContext.from(authentication);
        Map<String, Object> result = capabilities.execute("ai.chat", Map.of("body", requestBody), context);
        return ApiResponse.ok(JsonNode.class.cast(result.get("response")));
    }
}
