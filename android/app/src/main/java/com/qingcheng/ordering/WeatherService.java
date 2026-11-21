package com.qingcheng.ordering;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

/** Authenticated weather-query boundary. */
final class WeatherService {
    private final ApiClient api;
    WeatherService(ApiClient api) { this.api = api; }
    JSONObject current(String place, String token) throws IOException, JSONException {
        return api.weather(place, token);
    }
}
