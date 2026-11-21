package com.example.ordering.ai;

import com.example.ordering.capability.CapabilityExecutionContext;
import com.example.ordering.common.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AiModelController {
    private final AiModelCatalog catalog;
    public AiModelController(AiModelCatalog catalog) { this.catalog = catalog; }

    @GetMapping("/api/ai/models")
    public ResponseEntity<ApiResponse<AiModelCatalog.Catalog>> models(Authentication authentication,
            @RequestParam(defaultValue = "false") boolean refresh) {
        CapabilityExecutionContext.from(authentication);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(catalog.list(refresh)));
    }
}
