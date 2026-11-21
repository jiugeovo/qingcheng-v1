package com.example.ordering.capability;

import com.example.ordering.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/capabilities")
public class CapabilityController {
    private final CapabilityRegistry registry;

    public CapabilityController(CapabilityRegistry registry) { this.registry = registry; }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> catalog(Authentication authentication) {
        return ApiResponse.ok(registry.catalogFor(CapabilityExecutionContext.from(authentication)));
    }
}
