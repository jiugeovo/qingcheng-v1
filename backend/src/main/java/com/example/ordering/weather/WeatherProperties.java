package com.example.ordering.weather;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.weather")
public class WeatherProperties {

    private String qweatherKey = "";

    private String qweatherHost = "https://example.invalid";

    private String ipLocationUrl = "http://ip-api.com/json/{ip}?fields=status,message,countryCode,regionName,city,lat,lon,query&lang=zh-CN";

    private Duration timeout = Duration.ofSeconds(12);

    private Duration cacheTtl = Duration.ofMinutes(10);

    public String getQweatherKey() {
        return qweatherKey;
    }

    public void setQweatherKey(String qweatherKey) {
        this.qweatherKey = qweatherKey;
    }

    public String getQweatherHost() {
        return qweatherHost;
    }

    public void setQweatherHost(String qweatherHost) {
        this.qweatherHost = qweatherHost;
    }

    public String getIpLocationUrl() {
        return ipLocationUrl;
    }

    public void setIpLocationUrl(String ipLocationUrl) {
        this.ipLocationUrl = ipLocationUrl;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public Duration getCacheTtl() {
        return cacheTtl;
    }

    public void setCacheTtl(Duration cacheTtl) {
        this.cacheTtl = cacheTtl;
    }
}
