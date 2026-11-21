package com.qingcheng.ordering;

final class WeatherDisplayFormatter {
    private WeatherDisplayFormatter() {
    }

    static String displayPlace(String locationName, String query) {
        String place = firstNonBlank(locationName, query, "合肥");
        String normalized = place.trim().replaceAll("\\s+", " · ");
        return normalized.isBlank() ? "合肥" : normalized;
    }

    static String queryText(String locationName, String query) {
        String search = query == null ? "" : query.trim().replaceAll("\\s+", " ");
        String lastSearchPart = lastPart(search);
        if (!lastSearchPart.isBlank()) {
            return lastSearchPart;
        }
        String lastLocationPart = lastPart(locationName == null ? "" : locationName.trim().replaceAll("\\s+", " "));
        return lastLocationPart.isBlank() ? "合肥" : lastLocationPart;
    }

    private static String firstNonBlank(String first, String second, String fallback) {
        if (first != null && !first.trim().isBlank()) {
            return first.trim();
        }
        if (second != null && !second.trim().isBlank()) {
            return second.trim();
        }
        return fallback;
    }

    private static String lastPart(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String[] parts = value.trim().split("\\s+");
        return parts.length == 0 ? "" : parts[parts.length - 1].trim();
    }
}
