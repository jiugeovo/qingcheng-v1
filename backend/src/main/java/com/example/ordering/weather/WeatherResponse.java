package com.example.ordering.weather;

public record WeatherResponse(
        String query,
        String locationId,
        String locationName,
        String weather,
        String temperature,
        String feelsLike,
        String humidity,
        String windDirection,
        String windScale,
        String windSpeed,
        String precipitation,
        String pressure,
        String visibility,
        String observationTime,
        String updatedAt
) {
}
