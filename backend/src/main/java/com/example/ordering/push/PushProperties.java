package com.example.ordering.push;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.push")
public class PushProperties {

    private boolean enabled = false;
    private final Jpush jpush = new Jpush();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Jpush getJpush() {
        return jpush;
    }

    public static class Jpush {
        private String appKey = "";
        private String masterSecret = "";
        private String baseUrl = "https://api.jpush.cn/v3/push";
        private Duration timeout = Duration.ofSeconds(8);

        public String getAppKey() {
            return appKey;
        }

        public void setAppKey(String appKey) {
            this.appKey = appKey;
        }

        public String getMasterSecret() {
            return masterSecret;
        }

        public void setMasterSecret(String masterSecret) {
            this.masterSecret = masterSecret;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }
    }
}
