package com.example.ordering.ai;

import com.example.ordering.capability.CapabilityExecutionContext;
import com.example.ordering.common.ApiResponse;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai/ordering")
public class AiOrderingController {
    public record Message(@NotBlank @Size(max = 12000) String message, @Positive Long conversationId) { }
    public record Confirmation(@NotBlank @Size(max = 24000) String quoteToken) { }
    private final AiOrderingService service;
    public AiOrderingController(AiOrderingService service) { this.service = service; }

    @PostMapping("/messages")
    public ApiResponse<ObjectNode> message(Authentication auth, @Valid @RequestBody Message request) {
        return ApiResponse.ok(service.respond(CapabilityExecutionContext.from(auth).toUserAccount(),
                request.message(), request.conversationId()));
    }

    @PostMapping("/confirm")
    public ApiResponse<ObjectNode> confirm(Authentication auth, @Valid @RequestBody Confirmation request) {
        return ApiResponse.ok(service.confirm(CapabilityExecutionContext.from(auth).toUserAccount(), request.quoteToken()));
    }
}
