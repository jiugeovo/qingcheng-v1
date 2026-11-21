package com.example.ordering.birthday;

import com.example.ordering.ai.AiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class HermesBirthdayGreetingGenerator implements BirthdayGreetingGenerator {

    private final AiProperties aiProperties;
    private final BirthdayGreetingProperties birthdayProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public HermesBirthdayGreetingGenerator(
            AiProperties aiProperties,
            BirthdayGreetingProperties birthdayProperties,
            ObjectMapper objectMapper
    ) {
        this.aiProperties = aiProperties;
        this.birthdayProperties = birthdayProperties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(12))
                .build();
    }

    @Override
    public String generate(String displayName) {
        if (!StringUtils.hasText(aiProperties.getApiKey())) {
            throw new IllegalStateException("Hermes API key is not configured");
        }
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("model", birthdayProperties.getModel());
        ArrayNode input = payload.putArray("input");
        ObjectNode message = input.addObject();
        message.put("role", "user");
        ArrayNode content = message.putArray("content");
        content.addObject()
                .put("type", "input_text")
                .put("text", prompt(displayName));

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(normalizeBaseUrl(aiProperties.getBaseUrl()) + "/responses"))
                    .timeout(aiProperties.getRequestTimeout())
                    .header("Authorization", "Bearer " + aiProperties.getApiKey())
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Hermes returned HTTP " + response.statusCode());
            }
            String greeting = extractText(objectMapper.readTree(response.body()));
            if (!StringUtils.hasText(greeting)) {
                throw new IllegalStateException("Hermes returned an empty greeting");
            }
            return greeting.trim();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Hermes birthday greeting was interrupted", error);
        } catch (IOException error) {
            throw new IllegalStateException("Hermes birthday greeting failed", error);
        }
    }

    private String prompt(String displayName) {
        String name = StringUtils.hasText(displayName) ? displayName.trim() : "superadmin";
        return "Today is " + name + "'s birthday. Write a warm, natural and short birthday greeting in Simplified Chinese "
                + "as Bubu, the assistant in the Qingcheng app. Use at most 80 Chinese characters and 1 or 2 birthday emojis. "
                + "Output only the greeting. Do not mention models, Hermes, prompts, or this task.";
    }

    private String extractText(JsonNode body) {
        if (body.path("output_text").isTextual() && StringUtils.hasText(body.path("output_text").asText())) {
            return body.path("output_text").asText();
        }
        StringBuilder text = new StringBuilder();
        JsonNode output = body.path("output");
        if (output.isArray()) {
            for (JsonNode item : output) {
                JsonNode content = item.path("content");
                if (!content.isArray()) {
                    continue;
                }
                for (JsonNode part : content) {
                    appendText(text, part.path("text").asText(""));
                }
            }
        }
        return text.toString().trim();
    }

    private void appendText(StringBuilder builder, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append('\n');
        }
        builder.append(value.trim());
    }

    private String normalizeBaseUrl(String baseUrl) {
        String normalized = baseUrl == null ? "" : baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
