package com.example.ordering.weather;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.URI;

public interface WeatherHttpClient {

    JsonNode getJson(URI uri) throws IOException, InterruptedException;
}
