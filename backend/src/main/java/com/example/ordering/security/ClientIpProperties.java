package com.example.ordering.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Networks whose reverse proxies are allowed to provide client IP headers.
 * Direct clients are never allowed to choose their identity through headers.
 */
@ConfigurationProperties(prefix = "app.client-ip")
public class ClientIpProperties {

    private List<String> trustedProxies = new ArrayList<>(List.of("127.0.0.1/32", "::1/128"));

    public List<String> getTrustedProxies() {
        return List.copyOf(trustedProxies);
    }

    public void setTrustedProxies(List<String> trustedProxies) {
        this.trustedProxies = trustedProxies == null ? new ArrayList<>() : new ArrayList<>(trustedProxies);
    }
}
