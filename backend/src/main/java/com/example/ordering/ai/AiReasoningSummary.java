package com.example.ordering.ai;

import com.fasterxml.jackson.databind.JsonNode;

/** Only the supplier's public summaries are displayed or stored, never encrypted reasoning. */
final class AiReasoningSummary {
    static final String PREFIX = "### 思考摘要\n";
    static final String ANSWER = "\n\n### 回答\n";

    static String extract(JsonNode response) {
        StringBuilder summary = new StringBuilder();
        for (JsonNode item : response.path("output")) {
            if (!"reasoning".equals(item.path("type").asText())) continue;
            for (JsonNode part : item.path("summary")) {
                if (!"summary_text".equals(part.path("type").asText())) continue;
                String text = part.path("text").asText("");
                if (!text.isBlank()) {
                    if (!summary.isEmpty()) summary.append("\n\n");
                    summary.append(text);
                }
            }
        }
        return summary.toString();
    }

    static String combine(String summary, String answer) {
        return summary == null || summary.isBlank() ? answer : PREFIX + summary + ANSWER + answer;
    }

    static String answer(String stored) {
        if (stored != null && stored.startsWith(PREFIX)) {
            int split = stored.indexOf(ANSWER, PREFIX.length());
            if (split >= 0) return stored.substring(split + ANSWER.length());
        }
        return stored;
    }
}
