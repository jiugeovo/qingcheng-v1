package com.example.ordering.anniversary;

import com.example.ordering.capability.CapabilityExecutionContext;
import com.example.ordering.capability.CapabilityRegistry;
import com.example.ordering.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/anniversaries")
public class AnniversaryController {
    private final CapabilityRegistry registry;

    public AnniversaryController(CapabilityRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    public ApiResponse<List<AnniversaryResponse>> list(Authentication authentication) {
        Map<String, Object> result = registry.execute("anniversary.list", Map.of(), context(authentication));
        List<?> items = (List<?>) result.get("items");
        return ApiResponse.ok(items.stream().map(AnniversaryResponse.class::cast).toList());
    }

    @GetMapping("/{id}")
    public ApiResponse<AnniversaryResponse> get(Authentication authentication, @PathVariable long id) {
        return item(registry.execute("anniversary.get", Map.of("id", id), context(authentication)));
    }

    @PostMapping
    public ApiResponse<AnniversaryResponse> create(Authentication authentication,
                                                   @Valid @RequestBody AnniversaryRequest request) {
        CapabilityExecutionContext context = context(authentication);
        return item(registry.execute("anniversary.create", input(request), context));
    }

    @PutMapping("/{id}")
    public ApiResponse<AnniversaryResponse> update(Authentication authentication, @PathVariable long id,
                                                   @Valid @RequestBody AnniversaryRequest request) {
        CapabilityExecutionContext context = context(authentication);
        Map<String, Object> input = new HashMap<>(input(request));
        input.put("id", id);
        return item(registry.execute("anniversary.update", Map.copyOf(input), context));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(Authentication authentication, @PathVariable long id) {
        registry.execute("anniversary.delete", Map.of("id", id), context(authentication));
        return ApiResponse.ok(null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> invalidId(MethodArgumentTypeMismatchException exception) {
        return ResponseEntity.badRequest().body(ApiResponse.fail("纪念日 ID 必须为正整数"));
    }

    private static Map<String, Object> input(AnniversaryRequest request) {
        Map<String, Object> input = new HashMap<>(Map.of(
                "title", request.title(), "date", request.date().toString(),
                "note", request.note(), "repeatYearly", request.repeatYearly()));
        if (request.type() != null) {
            input.put("type", request.type().id());
        }
        return Map.copyOf(input);
    }

    private static ApiResponse<AnniversaryResponse> item(Map<String, Object> result) {
        return ApiResponse.ok(AnniversaryResponse.class.cast(result.get("item")));
    }

    private static CapabilityExecutionContext context(Authentication authentication) {
        return CapabilityExecutionContext.from(authentication);
    }
}
