package com.example.ordering.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitConfig {

    private int authPerMinute = 12;

    private int uploadPerMinute = 30;

    private int friendMediaPerMinute = 300;

    private int aiPerMinute = 20;

    private int apiPerMinute = 120;

    public int getAuthPerMinute() {
        return authPerMinute;
    }

    public void setAuthPerMinute(int authPerMinute) {
        this.authPerMinute = authPerMinute;
    }

    public int getUploadPerMinute() {
        return uploadPerMinute;
    }

    public void setUploadPerMinute(int uploadPerMinute) {
        this.uploadPerMinute = uploadPerMinute;
    }

    public int getFriendMediaPerMinute() {
        return friendMediaPerMinute;
    }

    public void setFriendMediaPerMinute(int friendMediaPerMinute) {
        this.friendMediaPerMinute = friendMediaPerMinute;
    }

    public int getAiPerMinute() {
        return aiPerMinute;
    }

    public void setAiPerMinute(int aiPerMinute) {
        this.aiPerMinute = aiPerMinute;
    }

    public int getApiPerMinute() {
        return apiPerMinute;
    }

    public void setApiPerMinute(int apiPerMinute) {
        this.apiPerMinute = apiPerMinute;
    }
}
