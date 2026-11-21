package com.example.ordering.capability;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** The supported schema dialect and its immutable registration/runtime boundary. */
final class CapabilitySchemas {
    private static final int MAX_DEPTH = 32;
    private static final Set<String> TYPES = Set.of("string", "integer", "number", "boolean", "object", "array");
    private static final Set<String> KEYS = Set.of("type", "required", "format", "minimum", "maximum",
            "minLength", "maxLength", "minItems", "maxItems", "enum", "items", "properties", "additionalProperties");

    private CapabilitySchemas() {
    }

    static Map<String, Object> freeze(Map<String, Object> schema) {
        if (schema == null) throw invalid("schema", "missing schema");
        return freezeProperties(schema, "schema", 0);
    }

    private static Map<String, Object> freezeProperties(Map<?, ?> properties, String path, int depth) {
        checkDepth(path, depth);
        Map<String, Object> result = new TreeMap<>();
        properties.forEach((key, definition) -> {
            if (!(key instanceof String name) || name.isBlank()) throw invalid(path, "invalid property name");
            if (!(definition instanceof Map<?, ?> fields)) throw invalid(path + "." + name, "definition must be an object");
            result.put(name, freezeDefinition(fields, path + "." + name, depth + 1));
        });
        return Collections.unmodifiableMap(result);
    }

    private static Map<String, Object> freezeDefinition(Map<?, ?> fields, String path, int depth) {
        checkDepth(path, depth);
        if (!(fields.get("type") instanceof String type) || !TYPES.contains(type)) {
            throw invalid(path, "unsupported or missing type");
        }
        Map<String, Object> result = new TreeMap<>();
        fields.forEach((key, value) -> {
            if (!(key instanceof String name) || !KEYS.contains(name)) throw invalid(path, "unsupported schema keyword: " + key);
            if (value == null) throw invalid(path + "." + name, "null definition");
            switch (name) {
                case "type" -> result.put(name, type);
                case "required" -> {
                    if (!(value instanceof Boolean)) throw invalid(path, "required must be boolean");
                    result.put(name, value);
                }
                case "format" -> {
                    if (!"string".equals(type) || !(value instanceof String format) || format.isBlank()) {
                        throw invalid(path, "format requires a nonempty string format");
                    }
                    result.put(name, value);
                }
                case "minimum", "maximum" -> {
                    if (!("integer".equals(type) || "number".equals(type)) || !(value instanceof Number number)
                            || !finite(number)) throw invalid(path, "numeric bound requires a finite number");
                    result.put(name, immutableNumber((Number) value));
                }
                case "minLength", "maxLength", "minItems", "maxItems" -> {
                    String expectedType = name.endsWith("Length") ? "string" : "array";
                    if (!expectedType.equals(type) || !(value instanceof Number number) || !finite(number)) {
                        throw invalid(path, "invalid length or item bound");
                    }
                    BigDecimal bound = decimal(number);
                    if (bound.signum() < 0 || bound.stripTrailingZeros().scale() > 0
                            || bound.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0) {
                        throw invalid(path, "length or item bound must be a nonnegative integer");
                    }
                    result.put(name, bound.intValueExact());
                }
                case "properties" -> {
                    if (!"object".equals(type) || !(value instanceof Map<?, ?> properties)) {
                        throw invalid(path, "properties requires an object schema");
                    }
                    result.put(name, freezeProperties(properties, path + ".properties", depth + 1));
                }
                case "additionalProperties" -> {
                    if (!"object".equals(type) || !(value instanceof Boolean)) {
                        throw invalid(path, "additionalProperties requires an object and boolean");
                    }
                    result.put(name, value);
                }
                case "items" -> {
                    if (!"array".equals(type) || !(value instanceof Map<?, ?> item)) {
                        throw invalid(path, "items requires an array and item definition");
                    }
                    result.put(name, freezeDefinition(item, path + ".items", depth + 1));
                }
                case "enum" -> {
                    if (!(value instanceof Collection<?> values) || values.isEmpty()) throw invalid(path, "enum must not be empty");
                    List<Object> choices = new ArrayList<>();
                    for (Object choice : values) {
                        if (choice == null || !(choice instanceof String || choice instanceof Boolean || choice instanceof Number)
                                || choice instanceof Number number && !finite(number)) {
                            throw invalid(path, "enum values must be finite scalars");
                        }
                        if (!hasType(type, choice, false)) throw invalid(path, "enum value does not match type");
                        choices.add(choice instanceof Number number ? immutableNumber(number) : choice);
                    }
                    result.put(name, List.copyOf(choices));
                }
                default -> throw invalid(path, "unsupported schema keyword");
            }
        });
        if ("array".equals(type) && !result.containsKey("items")) throw invalid(path, "array item definition is missing");
        checkBounds(result, "minimum", "maximum", path);
        checkBounds(result, "minLength", "maxLength", path);
        checkBounds(result, "minItems", "maxItems", path);
        return Collections.unmodifiableMap(result);
    }

    static void validate(Map<String, Object> schema, Map<String, Object> input) {
        if (input == null) throw new IllegalArgumentException("能力参数不能为空");
        validateProperties(schema, input, "", false, 0);
    }

    private static void validateProperties(Map<?, ?> properties, Map<?, ?> input, String path,
                                           boolean additional, int depth) {
        checkInputDepth(path, depth);
        input.keySet().forEach(key -> {
            if (!(key instanceof String) || (!additional && !properties.containsKey(key))) {
                throw new IllegalArgumentException("不支持的参数: " + path + key);
            }
        });
        properties.forEach((name, definition) -> validateValue(path + name, input.get(name), (Map<?, ?>) definition, depth + 1));
    }

    private static void validateValue(String name, Object value, Map<?, ?> fields, int depth) {
        checkInputDepth(name, depth);
        if (value == null) {
            if (Boolean.TRUE.equals(fields.get("required"))) throw new IllegalArgumentException("缺少参数: " + name);
            return;
        }
        String type = (String) fields.get("type");
        if (!hasType(type, value, "binary".equals(fields.get("format")))) {
            throw new IllegalArgumentException("参数类型错误: " + name);
        }
        if (value instanceof Number number) {
            if (!finite(number)) throw new IllegalArgumentException("参数必须为有限数值: " + name);
            checkValueBound(name, decimal(number), fields, "minimum", false);
            checkValueBound(name, decimal(number), fields, "maximum", true);
        }
        if (value instanceof String string) {
            checkValueBound(name, BigDecimal.valueOf(string.length()), fields, "minLength", false);
            checkValueBound(name, BigDecimal.valueOf(string.length()), fields, "maxLength", true);
        }
        if (fields.get("enum") instanceof Collection<?> choices && !choices.contains(value)) {
            throw new IllegalArgumentException("参数值不支持: " + name);
        }
        if (value instanceof Collection<?> values) {
            checkValueBound(name, BigDecimal.valueOf(values.size()), fields, "minItems", false);
            checkValueBound(name, BigDecimal.valueOf(values.size()), fields, "maxItems", true);
            int index = 0;
            for (Object item : values) {
                if (item == null) throw new IllegalArgumentException("参数元素不能为空: " + name + "[" + index + "]");
                validateValue(name + "[" + index++ + "]", item, (Map<?, ?>) fields.get("items"), depth + 1);
            }
        }
        if (value instanceof Map<?, ?> object && fields.get("properties") instanceof Map<?, ?> properties) {
            validateProperties(properties, object, name + ".", Boolean.TRUE.equals(fields.get("additionalProperties")), depth + 1);
        }
        if (value instanceof JsonNode && fields.containsKey("properties")) {
            throw new IllegalArgumentException("参数类型错误: " + name);
        }
        if ("object".equals(type) && !fields.containsKey("properties")
                && Boolean.FALSE.equals(fields.get("additionalProperties"))
                && (value instanceof Map<?, ?> object ? !object.isEmpty() : !((JsonNode) value).isEmpty())) {
            throw new IllegalArgumentException("不支持的参数: " + name);
        }
    }

    static Map<String, Object> freezeInput(Map<String, Object> input) {
        if (input == null) throw new IllegalArgumentException("能力参数不能为空");
        Map<String, Object> copy = new LinkedHashMap<>();
        input.forEach((key, value) -> copy.put(key, copyInput(value, key, 0)));
        return Collections.unmodifiableMap(copy);
    }

    private static Object copyInput(Object value, String path, int depth) {
        checkInputDepth(path, depth);
        if (value instanceof Map<?, ?> values) {
            Map<String, Object> copy = new LinkedHashMap<>();
            values.forEach((key, item) -> {
                if (!(key instanceof String name)) throw new IllegalArgumentException("参数键必须为字符串: " + path);
                copy.put(name, copyInput(item, path + "." + name, depth + 1));
            });
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof Collection<?> values) {
            List<Object> copy = new ArrayList<>();
            for (Object item : values) copy.add(copyInput(item, path + "[]", depth + 1));
            return Collections.unmodifiableList(copy);
        }
        // Binary requests are consumed synchronously; opaque JSON bodies receive a defensive copy.
        if (value instanceof JsonNode json) {
            validateJson(json, path, depth);
            return json.deepCopy();
        }
        if (value instanceof Number number) {
            if (!finite(number)) throw new IllegalArgumentException("参数必须为有限数值: " + path);
            return immutableNumber(number);
        }
        return value;
    }

    private static void validateJson(JsonNode value, String path, int depth) {
        checkInputDepth(path, depth);
        if (value.isNumber() && !finite(value.numberValue())) {
            throw new IllegalArgumentException("参数必须为有限数值: " + path);
        }
        if (value.isObject()) {
            value.fields().forEachRemaining(field -> validateJson(field.getValue(), path + "." + field.getKey(), depth + 1));
        } else if (value.isArray()) {
            for (JsonNode item : value) validateJson(item, path + "[]", depth + 1);
        }
    }

    private static boolean hasType(String type, Object value, boolean binary) {
        return switch (type) {
            case "string" -> binary ? value instanceof MultipartFile : value instanceof String;
            case "integer" -> value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long;
            case "number" -> value instanceof Number;
            case "boolean" -> value instanceof Boolean;
            case "object" -> value instanceof Map<?, ?> || value instanceof JsonNode json && json.isObject();
            case "array" -> value instanceof Collection<?>;
            default -> false;
        };
    }

    private static void checkBounds(Map<String, Object> fields, String lower, String upper, String path) {
        if (fields.get(lower) instanceof Number min && fields.get(upper) instanceof Number max
                && decimal(min).compareTo(decimal(max)) > 0) throw invalid(path, "inverted bounds");
    }

    private static void checkValueBound(String name, BigDecimal value, Map<?, ?> fields, String boundName, boolean upper) {
        if (fields.get(boundName) instanceof Number bound) {
            int comparison = value.compareTo(decimal(bound));
            if (upper ? comparison > 0 : comparison < 0) {
                throw new IllegalArgumentException("参数超出允许范围: " + name + " (" + boundName + ")");
            }
        }
    }

    private static Number immutableNumber(Number value) {
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long
                || value instanceof Float || value instanceof Double || value instanceof BigDecimal
                || value instanceof java.math.BigInteger) return value;
        return decimal(value);
    }

    private static boolean finite(Number value) {
        return !(value instanceof Double d && !Double.isFinite(d)) && !(value instanceof Float f && !Float.isFinite(f));
    }

    private static BigDecimal decimal(Number value) {
        return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
    }

    private static void checkDepth(String path, int depth) {
        if (depth > MAX_DEPTH) throw invalid(path, "schema nesting is too deep or cyclic");
    }

    private static void checkInputDepth(String path, int depth) {
        if (depth > MAX_DEPTH) throw new IllegalArgumentException("参数嵌套过深: " + path);
    }

    private static IllegalStateException invalid(String path, String detail) {
        return new IllegalStateException("Invalid capability schema at " + path + ": " + detail);
    }
}
