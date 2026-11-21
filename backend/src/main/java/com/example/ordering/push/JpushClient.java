package com.example.ordering.push;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Component
public class JpushClient {

    private static final Logger log = LoggerFactory.getLogger(JpushClient.class);

    private final PushProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public JpushClient(PushProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public boolean isConfigured() {
        PushProperties.Jpush jpush = properties.getJpush();
        return properties.isEnabled()
                && StringUtils.hasText(jpush.getAppKey())
                && StringUtils.hasText(jpush.getMasterSecret())
                && StringUtils.hasText(jpush.getBaseUrl());
    }

    public void sendNotification(List<String> registrationIds, String title, String text, Map<String, String> extras) {
        if (!isConfigured() || registrationIds == null || registrationIds.isEmpty()) {
            return;
        }
        PushProperties.Jpush jpush = properties.getJpush();
        Map<String, Object> payload = Map.of(
                "platform", "all",
                "audience", Map.of("registration_id", registrationIds),
                "notification", Map.of("android", Map.of(
                        "title", title,
                        "alert", text,
                        "extras", extras == null ? Map.of() : extras
                )),
                "message", Map.of(
                        "title", title,
                        "msg_content", text,
                        "extras", extras == null ? Map.of() : extras
                ),
                "options", Map.of(
                        "time_to_live", 86400,
                        "apns_production", false
                )
        );

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(jpush.getBaseUrl()))
                    .timeout(jpush.getTimeout())
                    .header("Content-Type", "application/json")
                    .header("Authorization", basicAuth(jpush.getAppKey(), jpush.getMasterSecret()))
                    .POST(HttpRequest.BodyPublishers.ofString(toJson(payload), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("JPush notification failed: status={}, body={}", response.statusCode(), response.body());
            }
        } catch (IOException error) {
            log.warn("JPush notification request failed: {}", error.getMessage());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            log.warn("JPush notification interrupted");
        }
    }

    private String basicAuth(String appKey, String masterSecret) {
        String token = appKey + ":" + masterSecret;
        return "Basic " + Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
    }

    private String toJson(Map<String, Object> payload) throws JsonProcessingException {
        return objectMapper.writeValueAsString(payload);
    }
}
