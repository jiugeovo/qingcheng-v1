package com.example.ordering.weather;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Objects;

@Service
public class WeatherService {

    private static final String DEFAULT_QUERY = "合肥瑶海区";

    private final WeatherProperties properties;
    private final WeatherHttpClient weatherHttpClient;
    private final Clock clock;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public WeatherService(WeatherProperties properties, WeatherHttpClient weatherHttpClient) {
        this(properties, weatherHttpClient, Clock.system(java.time.ZoneId.of("Asia/Shanghai")));
    }

    @Autowired
    public WeatherService(WeatherProperties properties, WeatherHttpClient weatherHttpClient, Clock clock) {
        this.properties = properties;
        this.weatherHttpClient = weatherHttpClient;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public WeatherResponse currentWeather(String query) {
        return currentWeather(query, null);
    }

    public WeatherResponse currentWeather(String query, String clientIp) {
        if (properties.getQweatherKey() == null || properties.getQweatherKey().isBlank()) {
            throw new IllegalArgumentException("天气服务未配置");
        }
        String normalized = normalizeQueryOrBlank(query);
        if (normalized.isBlank()) {
            normalized = DEFAULT_QUERY;
        }
        String cacheKey = "city:" + normalized;
        WeatherRequest weatherRequest = new WeatherRequest(normalized, splitQuery(normalized));
        Instant now = clock.instant();
        CacheEntry cached = cache.get(cacheKey);
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.response();
        }
        WeatherResponse response = fetchWeather(weatherRequest);
        cache.put(cacheKey, new CacheEntry(response, now.plus(properties.getCacheTtl())));
        return response;
    }

    private WeatherResponse fetchWeather(WeatherRequest weatherRequest) {
        try {
            JsonNode locationBody = weatherHttpClient.getJson(locationUri(weatherRequest.queryParts()));
            if (!"200".equals(locationBody.path("code").asText())) {
                throw new IllegalArgumentException("城市查询失败：" + locationBody.path("code").asText("未知错误"));
            }
            JsonNode locations = locationBody.path("location");
            if (!locations.isArray() || locations.isEmpty()) {
                throw new IllegalArgumentException("没有找到“" + weatherRequest.displayQuery() + "”对应的城市");
            }
            JsonNode location = locations.get(0);
            String locationId = requiredText(location, "id", "城市数据缺少 ID");
            String locationName = displayName(location);

            JsonNode weatherBody = weatherHttpClient.getJson(weatherUri(locationId));
            if (!"200".equals(weatherBody.path("code").asText())) {
                throw new IllegalArgumentException("天气查询失败：" + weatherBody.path("code").asText("未知错误"));
            }
            JsonNode now = weatherBody.path("now");
            if (now.isMissingNode() || now.isNull()) {
                throw new IllegalArgumentException(locationName + " 的天气数据暂时为空");
            }
            return new WeatherResponse(
                    weatherRequest.displayQuery(),
                    locationId,
                    locationName,
                    text(now, "text"),
                    text(now, "temp"),
                    text(now, "feelsLike"),
                    text(now, "humidity"),
                    text(now, "windDir"),
                    text(now, "windScale"),
                    text(now, "windSpeed"),
                    text(now, "precip"),
                    text(now, "pressure"),
                    text(now, "vis"),
                    formatObsTime(text(now, "obsTime")),
                    DateTimeFormatter.ISO_INSTANT.format(clock.instant())
            );
        } catch (IOException e) {
            throw new IllegalArgumentException("天气接口暂时不可用，请稍后再试");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("天气查询已中断，请稍后再试");
        }
    }

    private URI locationUri(QueryParts queryParts) {
        StringBuilder url = new StringBuilder(host())
                .append("/geo/v2/city/lookup?location=")
                .append(encode(queryParts.location()))
                .append("&key=")
                .append(encode(properties.getQweatherKey()))
                .append("&range=cn&number=5");
        if (!queryParts.adm().isBlank()) {
            url.append("&adm=").append(encode(queryParts.adm()));
        }
        return URI.create(url.toString());
    }

    private URI weatherUri(String locationId) {
        return URI.create(host()
                + "/v7/weather/now?location=" + encode(locationId)
                + "&key=" + encode(properties.getQweatherKey()));
    }

    private WeatherRequest resolveIpLocation(String clientIp) {
        try {
            JsonNode body = weatherHttpClient.getJson(ipLocationUri(clientIp));
            String status = text(body, "status");
            if (!status.isBlank() && !"success".equalsIgnoreCase(status)) {
                throw new IllegalArgumentException("无法根据当前 IP 定位，请手动输入城市");
            }
            String countryCode = text(body, "countryCode");
            if (!countryCode.isBlank() && !"CN".equalsIgnoreCase(countryCode)) {
                throw new IllegalArgumentException("当前 IP 暂不支持自动定位，请手动输入城市");
            }
            String city = text(body, "city").trim();
            String region = text(body, "regionName").trim();
            double lat = body.path("lat").asDouble(Double.NaN);
            double lon = body.path("lon").asDouble(Double.NaN);
            String lookupLocation;
            if (Double.isFinite(lat) && Double.isFinite(lon)) {
                lookupLocation = trimCoordinate(lon) + "," + trimCoordinate(lat);
            } else if (!city.isBlank()) {
                lookupLocation = city;
            } else {
                throw new IllegalArgumentException("无法根据当前 IP 定位，请手动输入城市");
            }
            String display = ((region.isBlank() ? "" : region + " ") + city).trim();
            if (display.isBlank()) {
                display = "当前 IP";
            }
            return new WeatherRequest(display, new QueryParts(lookupLocation, ""));
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && (e.getMessage().contains("无法根据当前 IP 定位")
                    || e.getMessage().contains("暂不支持自动定位"))) {
                throw e;
            }
            throw new IllegalArgumentException("无法根据当前 IP 定位，请手动输入城市");
        } catch (IOException e) {
            throw new IllegalArgumentException("无法根据当前 IP 定位，请手动输入城市");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("天气查询已中断，请稍后再试");
        }
    }

    private URI ipLocationUri(String clientIp) {
        String template = properties.getIpLocationUrl();
        if (template == null || template.isBlank()) {
            template = "http://ip-api.com/json/{ip}?fields=status,message,countryCode,regionName,city,lat,lon,query&lang=zh-CN";
        }
        String url = template.contains("{ip}")
                ? template.replace("{ip}", encode(clientIp))
                : template + (template.contains("?") ? "&" : "?") + "ip=" + encode(clientIp);
        return URI.create(url);
    }

    private String host() {
        String host = properties.getQweatherHost() == null || properties.getQweatherHost().isBlank()
                ? "https://example.invalid"
                : properties.getQweatherHost().trim();
        return host.endsWith("/") ? host.substring(0, host.length() - 1) : host;
    }

    private String normalizeQueryOrBlank(String query) {
        String normalized = query == null ? "" : query.trim().replaceAll("\\s+", " ");
        if (normalized.length() > 60) {
            throw new IllegalArgumentException("城市名称过长");
        }
        return normalized;
    }

    private String normalizeClientIp(String clientIp) {
        String normalized = clientIp == null ? "" : clientIp.trim();
        if (normalized.startsWith("[") && normalized.contains("]")) {
            normalized = normalized.substring(1, normalized.indexOf(']'));
        }
        if (normalized.startsWith("::ffff:")) {
            normalized = normalized.substring("::ffff:".length());
        }
        if (normalized.indexOf(':') == normalized.lastIndexOf(':') && normalized.contains(".") && normalized.contains(":")) {
            normalized = normalized.substring(0, normalized.lastIndexOf(':'));
        }
        return normalized;
    }

    private boolean isPublicIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        if (!isIpLiteral(ip)) {
            return false;
        }
        try {
            InetAddress address = InetAddress.getByName(ip);
            byte[] bytes = address.getAddress();
            if (address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress()
                    || address.isMulticastAddress()) {
                return false;
            }
            if (bytes.length == 4) {
                int first = bytes[0] & 0xff;
                int second = bytes[1] & 0xff;
                return !(first == 100 && second >= 64 && second <= 127);
            }
            if (bytes.length == 16) {
                int first = bytes[0] & 0xff;
                return (first & 0xfe) != 0xfc;
            }
            return true;
        } catch (UnknownHostException e) {
            return false;
        }
    }

    private boolean isIpLiteral(String value) {
        if (value.matches("\\d{1,3}(\\.\\d{1,3}){3}")) {
            return true;
        }
        return value.contains(":") && value.matches("[0-9a-fA-F:.%]+");
    }

    private QueryParts splitQuery(String normalized) {
        String[] parts = normalized.split(" ");
        String location = parts[parts.length - 1];
        StringBuilder adm = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) {
            adm.append(parts[i]);
        }
        return new QueryParts(location, adm.toString());
    }

    private String displayName(JsonNode location) {
        String adm1 = text(location, "adm1");
        String adm2 = text(location, "adm2");
        String name = text(location, "name");
        return (adm1 + " " + adm2 + " " + name).trim().replaceAll("\\s+", " ");
    }

    private String requiredText(JsonNode node, String field, String message) {
        String value = text(node, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private String text(JsonNode node, String field) {
        return node.path(field).asText("");
    }

    private String formatObsTime(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        try {
            return OffsetDateTime.parse(value).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.CHINA));
        } catch (DateTimeParseException e) {
            return value;
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String trimCoordinate(double value) {
        return String.format(Locale.US, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private record WeatherRequest(String displayQuery, QueryParts queryParts) {
    }

    private record QueryParts(String location, String adm) {
    }

    private record CacheEntry(WeatherResponse response, Instant expiresAt) {
    }
}
