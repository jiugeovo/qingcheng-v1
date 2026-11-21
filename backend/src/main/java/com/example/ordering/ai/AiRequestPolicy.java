package com.example.ordering.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Rebuilds app input; arbitrary Responses API options never become supplier instructions. */
final class AiRequestPolicy {
    static final int MAX_OUTPUT_TOKENS = 1200;
    static final int MAX_TEXT_CHARACTERS = 12000;
    static final int MAX_MESSAGES = 16;
    static final int MAX_PARTS_PER_MESSAGE = 16;
    static final int MAX_IMAGES = 4;
    static final int MAX_IMAGE_REFERENCE_CHARACTERS = 16 * 1024 * 1024;

    private final ObjectMapper mapper;

    AiRequestPolicy(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    ObjectNode normalize(ObjectNode body) {
        ObjectNode normalized = mapper.createObjectNode();
        copyConversationId(body, normalized);
        JsonNode model = body.get("model");
        if (model != null && !model.isNull()) {
            if (!model.isTextual() || !AiModelCatalog.MODEL_ID.matcher(model.textValue()).matches())
                throw invalid("模型名称格式不正确");
            normalized.put("model", model.textValue());
        }
        JsonNode displayText = body.get("displayText");
        if (displayText != null && !displayText.isNull()) {
            normalized.put("displayText", text(displayText, "displayText"));
        }
        normalized.put("max_output_tokens", outputTokens(body.get("max_output_tokens")));
        JsonNode input = body.get("input");
        if (input == null || input.isNull()) input = body.get("prompt");
        if (input == null || input.isNull()) throw invalid("缺少 AI 输入");

        Budget budget = new Budget();
        if (input.isTextual()) {
            normalized.put("input", budget.text(text(input, "input")));
        } else if (input.isArray() && !input.isEmpty() && input.size() <= MAX_MESSAGES) {
            ArrayNode messages = normalized.putArray("input");
            for (JsonNode message : input) messages.add(message(message, budget));
        } else {
            throw invalid("AI 输入格式或消息数量不正确");
        }
        if (!budget.hasContent) throw invalid("AI 输入不能为空");
        return normalized;
    }

    private ObjectNode message(JsonNode source, Budget budget) {
        if (!source.isObject() || !"user".equals(source.path("role").asText())
                || (source.has("type") && !"message".equals(source.path("type").asText()))) {
            throw invalid("只允许提交用户消息");
        }
        ObjectNode message = mapper.createObjectNode().put("role", "user");
        JsonNode content = source.get("content");
        if (content != null && content.isTextual()) {
            message.put("content", budget.text(text(content, "content")));
        } else if (content != null && content.isArray() && !content.isEmpty()
                && content.size() <= MAX_PARTS_PER_MESSAGE) {
            ArrayNode parts = message.putArray("content");
            for (JsonNode part : content) parts.add(part(part, budget));
        } else {
            throw invalid("AI 消息内容格式或数量不正确");
        }
        return message;
    }

    private ObjectNode part(JsonNode source, Budget budget) {
        if (!source.isObject()) throw invalid("AI 消息内容格式不正确");
        return switch (source.path("type").asText()) {
            case "input_text" -> mapper.createObjectNode().put("type", "input_text")
                    .put("text", budget.text(text(source.get("text"), "text")));
            case "input_image" -> {
                JsonNode reference = source.get("image_url");
                if (reference == null || !reference.isTextual() || reference.textValue().isBlank()) {
                    throw invalid("图片来源不受信任");
                }
                budget.image(reference.textValue());
                String detail = source.has("detail") ? source.path("detail").asText() : "auto";
                if (!"auto".equals(detail) && !"low".equals(detail) && !"high".equals(detail)) {
                    throw invalid("图片精度参数不正确");
                }
                yield mapper.createObjectNode().put("type", "input_image")
                        .put("image_url", reference.textValue()).put("detail", detail);
            }
            default -> throw invalid("不支持的 AI 消息类型");
        };
    }

    private String text(JsonNode value, String field) {
        if (value == null || !value.isTextual() || value.textValue().length() > MAX_TEXT_CHARACTERS) {
            throw invalid("AI 文本格式或长度不正确: " + field);
        }
        return value.textValue();
    }

    private int outputTokens(JsonNode requested) {
        if (requested == null || requested.isNull()) return MAX_OUTPUT_TOKENS;
        if (!requested.isIntegralNumber() || !requested.canConvertToInt() || requested.intValue() < 1) {
            throw invalid("AI 输出额度必须为正整数");
        }
        return Math.min(requested.intValue(), MAX_OUTPUT_TOKENS);
    }

    private void copyConversationId(ObjectNode source, ObjectNode target) {
        JsonNode id = source.get("conversationId");
        if (id == null || id.isNull()) return;
        if (!id.isIntegralNumber() || !id.canConvertToLong() || id.longValue() < 1) {
            throw invalid("对话编号不正确");
        }
        target.put("conversationId", id.longValue());
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    private static final class Budget {
        private int textCharacters;
        private int images;
        private long imageCharacters;
        private boolean hasContent;

        private String text(String value) {
            textCharacters += value.length();
            if (textCharacters > MAX_TEXT_CHARACTERS) throw invalid("AI 文本总长度超过限制");
            hasContent |= !value.isBlank();
            return value;
        }

        private void image(String reference) {
            imageCharacters += reference.length();
            if (++images > MAX_IMAGES || imageCharacters > MAX_IMAGE_REFERENCE_CHARACTERS) {
                throw invalid("AI 图片数量或总大小超过限制");
            }
            hasContent = true;
        }
    }
}
