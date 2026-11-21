package com.example.ordering.weather;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

@Component
public class QweatherHttpClient implements WeatherHttpClient {

    private final WeatherProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public QweatherHttpClient(WeatherProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getTimeout())
                .build();
    }

    @Override
    public JsonNode getJson(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(properties.getTimeout())
                .GET()
                .header("Accept", "application/json")
                .header("Accept-Encoding", "gzip")
                .build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalArgumentException("天气接口暂时不可用，请稍后再试");
        }
        return objectMapper.readTree(decodeBody(response));
    }

    private byte[] decodeBody(HttpResponse<byte[]> response) throws IOException {
        byte[] body = response.body();
        String encoding = response.headers()
                .firstValue("Content-Encoding")
                .orElse("")
                .toLowerCase(Locale.ROOT);
        if (encoding.contains("gzip") || isGzip(body)) {
            try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(body));
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                gzip.transferTo(output);
                return output.toByteArray();
            }
        }
        return body;
    }

    private boolean isGzip(byte[] body) {
        return body != null
                && body.length >= 2
                && (body[0] & 0xff) == 0x1f
                && (body[1] & 0xff) == 0x8b;
    }
}
