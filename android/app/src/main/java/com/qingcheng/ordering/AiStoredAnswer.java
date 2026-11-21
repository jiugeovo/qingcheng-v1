package com.qingcheng.ordering;

/** Readable history format also renders correctly in previous app versions. */
record AiStoredAnswer(String answer, String summary) {
    static AiStoredAnswer parse(String text) {
        String value = text == null ? "" : text;
        String prefix = "### 思考摘要\n", separator = "\n\n### 回答\n";
        int split = value.startsWith(prefix) ? value.indexOf(separator, prefix.length()) : -1;
        return split >= 0 ? new AiStoredAnswer(value.substring(split + separator.length()),
                value.substring(prefix.length(), split)) : new AiStoredAnswer(value, "");
    }
}
