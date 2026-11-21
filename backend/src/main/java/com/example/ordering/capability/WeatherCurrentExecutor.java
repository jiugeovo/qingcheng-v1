package com.example.ordering.capability;

import com.example.ordering.weather.WeatherService;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Routes authenticated weather reads to the existing domain service. */
@Component
public final class WeatherCurrentExecutor implements CapabilityExecutor {
    private final WeatherService service;

    public WeatherCurrentExecutor(WeatherService service) {
        this.service = service;
    }

    @Override
    public String capabilityId() {
        return "weather.current";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> input, CapabilityExecutionContext context) {
        return Map.of("weather", service.currentWeather((String) input.get("query"), context.clientIp()));
    }
}
