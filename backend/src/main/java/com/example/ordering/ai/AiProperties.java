package com.example.ordering.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    private String baseUrl = "https://example.invalid/v1";

    private String apiKey = "";

    private String model = "gpt-5.6-sol";

    private boolean modelSelectionEnabled = true;

    public boolean isModelSelectionEnabled() { return modelSelectionEnabled; }
    public void setModelSelectionEnabled(boolean enabled) { this.modelSelectionEnabled = enabled; }

    public String getModel() { return model; }

    public void setModel(String model) {
        if (model == null || !AiModelCatalog.MODEL_ID.matcher(model).matches())
            throw new IllegalArgumentException("app.ai.model is not a valid model ID");
        this.model = model;
    }

    private Duration requestTimeout = Duration.ofSeconds(120);

    private int maxConcurrentRequests = 8;

    private Duration streamTimeout = Duration.ofSeconds(360);

    public Duration getStreamTimeout() { return streamTimeout; }

    public void setStreamTimeout(Duration timeout) {
        if (timeout == null || timeout.compareTo(Duration.ofSeconds(30)) < 0
                || timeout.compareTo(Duration.ofSeconds(900)) > 0) {
            throw new IllegalArgumentException("app.ai.stream-timeout must be between 30s and 900s");
        }
        this.streamTimeout = timeout;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        if (requestTimeout == null || requestTimeout.compareTo(Duration.ofSeconds(1)) < 0
                || requestTimeout.compareTo(Duration.ofSeconds(120)) > 0) {
            throw new IllegalArgumentException("app.ai.request-timeout must be between 1s and 120s");
        }
        this.requestTimeout = requestTimeout;
    }

    public int getMaxConcurrentRequests() {
        return maxConcurrentRequests;
    }

    public void setMaxConcurrentRequests(int maxConcurrentRequests) {
        if (maxConcurrentRequests < 1 || maxConcurrentRequests > 16) {
            throw new IllegalArgumentException("app.ai.max-concurrent-requests must be between 1 and 16");
        }
        this.maxConcurrentRequests = maxConcurrentRequests;
    }

}
