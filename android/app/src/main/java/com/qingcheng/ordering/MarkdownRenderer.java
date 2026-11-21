package com.qingcheng.ordering;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;

final class MarkdownRenderer {
    private MarkdownRenderer() {
    }

    static CharSequence render(String markdown) {
        String source = markdown == null ? "" : markdown;
        SpannableStringBuilder builder = new SpannableStringBuilder();
        String[] lines = source.replace("\r\n", "\n").split("\n", -1);
        for (String line : lines) {
            int start = builder.length();
            ParsedMarkdownLine parsed = MarkdownParser.parseLine(line);
            RenderedMarkdownLine rendered = new RenderedMarkdownLine(
                    renderInline(parsed.text()),
                    parsed.heading(),
                    parsed.listItem()
            );
            builder.append(rendered.text()).append('\n');
            int end = Math.max(start, builder.length() - 1);
            if (rendered.heading()) {
                builder.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                builder.setSpan(new RelativeSizeSpan(1.18f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (rendered.listItem()) {
                builder.setSpan(new ForegroundColorSpan(Ui.TEXT), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return builder;
    }

    private static CharSequence renderInline(String text) {
        String source = text == null ? "" : text;
        SpannableStringBuilder builder = new SpannableStringBuilder();
        int index = 0;
        while (index < source.length()) {
            if (source.startsWith("**", index)) {
                int end = source.indexOf("**", index + 2);
                if (end > index + 2) {
                    int start = builder.length();
                    builder.append(source, index + 2, end);
                    builder.setSpan(new StyleSpan(Typeface.BOLD), start, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    index = end + 2;
                    continue;
                }
            }
            if (source.charAt(index) == '`') {
                int end = source.indexOf('`', index + 1);
                if (end > index + 1) {
                    int start = builder.length();
                    builder.append(source, index + 1, end);
                    builder.setSpan(new TypefaceSpan("monospace"), start, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    builder.setSpan(new ForegroundColorSpan(Ui.GREEN), start, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    index = end + 1;
                    continue;
                }
            }
            builder.append(source.charAt(index++));
        }
        return builder;
    }
}
