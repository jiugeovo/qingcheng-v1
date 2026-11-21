package com.example.ordering.rules;

import com.example.ordering.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/love-rules")
public class LoveRuleController {

    private final LoveRuleService loveRuleService;

    public LoveRuleController(LoveRuleService loveRuleService) {
        this.loveRuleService = loveRuleService;
    }

    @GetMapping
    public ApiResponse<List<LoveRuleResponse>> listRules() {
        return ApiResponse.ok(loveRuleService.listRules());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN') or hasAuthority('COUPLE_MEMBER')")
    public ApiResponse<LoveRuleResponse> createRule(@Valid @RequestBody LoveRuleRequest request) {
        return ApiResponse.ok("守则已添加", loveRuleService.createRule(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN') or hasAuthority('COUPLE_MEMBER')")
    public ApiResponse<LoveRuleResponse> updateRule(
            @PathVariable Long id,
            @Valid @RequestBody LoveRuleRequest request
    ) {
        return ApiResponse.ok("守则已更新", loveRuleService.updateRule(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN') or hasAuthority('COUPLE_MEMBER')")
    public ApiResponse<Void> deleteRule(@PathVariable Long id) {
        loveRuleService.deleteRule(id);
        return ApiResponse.ok("守则已删除", null);
    }
}
