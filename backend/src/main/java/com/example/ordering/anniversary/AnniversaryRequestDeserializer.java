package com.example.ordering.anniversary;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Set;

/** Reject coercion locally so HTTP and internal capability calls use the same field types. */
public final class AnniversaryRequestDeserializer extends StdDeserializer<AnniversaryRequest> {

    private static final Set<String> FIELDS = Set.of("title", "date", "note", "repeatYearly", "type");

    public AnniversaryRequestDeserializer() {
        super(AnniversaryRequest.class);
    }

    @Override
    public AnniversaryRequest deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonNode body = parser.readValueAsTree();
        if (!body.isObject()) {
            throw JsonMappingException.from(parser, "纪念日请求体必须是对象");
        }
        var names = body.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!FIELDS.contains(name)) {
                throw JsonMappingException.from(parser, "不支持的参数: " + name);
            }
        }
        String title = text(parser, body, "title");
        String dateValue = text(parser, body, "date");
        String note = text(parser, body, "note");
        LocalDate date = null;
        if (dateValue != null) {
            if (!dateValue.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
                throw JsonMappingException.from(parser, "date 必须是有效的 ISO 日期（yyyy-MM-dd）");
            }
            try {
                date = LocalDate.parse(dateValue);
            } catch (DateTimeParseException ex) {
                throw JsonMappingException.from(parser, "date 必须是有效的 ISO 日期（yyyy-MM-dd）", ex);
            }
        }
        JsonNode repeat = body.get("repeatYearly");
        if (repeat != null && !repeat.isNull() && !repeat.isBoolean()) {
            throw JsonMappingException.from(parser, "repeatYearly 必须是布尔值");
        }
        String typeId = text(parser, body, "type");
        AnniversaryType type = null;
        if (typeId != null) {
            try {
                type = AnniversaryType.fromId(typeId);
            } catch (IllegalArgumentException ex) {
                throw JsonMappingException.from(parser, ex.getMessage(), ex);
            }
        }
        return new AnniversaryRequest(title, date, note,
                repeat == null || repeat.isNull() ? null : repeat.booleanValue(), type);
    }

    private static String text(JsonParser parser, JsonNode body, String field) throws JsonMappingException {
        JsonNode value = body.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw JsonMappingException.from(parser, field + " 必须是字符串");
        }
        return value.textValue();
    }
}
