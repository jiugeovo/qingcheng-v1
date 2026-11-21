package com.qingcheng.ordering;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MarkdownParser {
    private static final Pattern ORDERED_LIST = Pattern.compile("^\\d+[.)]\\s+(.+)$");

    private MarkdownParser() {
    }

    static ParsedMarkdownLine parseLine(String line) {
        String text = line == null ? "" : line;
        boolean heading = false;
        boolean listItem = false;
        if (text.startsWith("### ")) {
            heading = true;
            text = text.substring(4);
        } else if (text.startsWith("## ")) {
            heading = true;
            text = text.substring(3);
        } else if (text.startsWith("# ")) {
            heading = true;
            text = text.substring(2);
        } else if (text.startsWith("- ") || text.startsWith("* ")) {
            listItem = true;
            text = "• " + text.substring(2);
        } else {
            Matcher ordered = ORDERED_LIST.matcher(text);
            if (ordered.matches()) {
                listItem = true;
                text = "• " + ordered.group(1);
            }
        }
        return new ParsedMarkdownLine(text, heading, listItem);
    }

    static String plainText(String text) {
        String source = text == null ? "" : text;
        StringBuilder builder = new StringBuilder();
        int index = 0;
        while (index < source.length()) {
            if (source.startsWith("**", index)) {
                int end = source.indexOf("**", index + 2);
                if (end > index + 2) {
                    builder.append(source, index + 2, end);
                    index = end + 2;
                    continue;
                }
            }
            if (source.charAt(index) == '`') {
                int end = source.indexOf('`', index + 1);
                if (end > index + 1) {
                    builder.append(source, index + 1, end);
                    index = end + 1;
                    continue;
                }
            }
            builder.append(source.charAt(index++));
        }
        return builder.toString();
    }
}
