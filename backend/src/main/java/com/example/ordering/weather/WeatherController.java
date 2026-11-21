package com.example.ordering.weather;

import com.example.ordering.common.ApiResponse;
import com.example.ordering.capability.CapabilityExecutionContext;
import com.example.ordering.capability.CapabilityRegistry;
import com.example.ordering.security.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/weather")
public class WeatherController {

    private final CapabilityRegistry capabilities;
    private final ClientIpResolver clientIpResolver;

    public WeatherController(CapabilityRegistry capabilities, ClientIpResolver clientIpResolver) {
        this.capabilities = capabilities;
        this.clientIpResolver = clientIpResolver;
    }

    @GetMapping
    public ApiResponse<WeatherResponse> currentWeather(
            Authentication authentication,
            @RequestParam(required = false) String query,
            HttpServletRequest request
    ) {
        CapabilityExecutionContext context = CapabilityExecutionContext.from(authentication)
                .withClientIp(clientIpResolver.resolve(request));
        Map<String, Object> input = query == null ? Map.of() : Map.of("query", query);
        Map<String, Object> result = capabilities.execute("weather.current", input, context);
        return ApiResponse.ok(WeatherResponse.class.cast(result.get("weather")));
    }
}
