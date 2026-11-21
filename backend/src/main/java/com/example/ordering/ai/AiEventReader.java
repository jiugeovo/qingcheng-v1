package com.example.ordering.ai;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;

/** Bounded SSE framing, including UTF-8 text, comments, multiline data and a final EOF frame. */
final class AiEventReader {
    interface Handler { boolean event(String data) throws IOException; }
    static final int MAX_FRAME = 1024 * 1024;
    static final int MAX_STREAM = 8 * 1024 * 1024;

    static void read(Reader input, Handler handler) throws IOException {
        BufferedReader reader = new BufferedReader(input);
        StringBuilder line = new StringBuilder(), data = new StringBuilder();
        int total = 0;
        for (int ch; (ch = reader.read()) >= 0;) {
            if (++total > MAX_STREAM) throw new IOException("AI 响应过大");
            if (ch == '\n') {
                String value = line.toString();
                if (value.endsWith("\r")) value = value.substring(0, value.length() - 1);
                line.setLength(0);
                if (value.isEmpty()) {
                    if (!data.isEmpty()) {
                        String frame = data.substring(0, data.length() - 1);
                        data.setLength(0);
                        if (!handler.event(frame)) return;
                    }
                } else if (value.startsWith("data:")) {
                    String part = value.substring(5);
                    if (part.startsWith(" ")) part = part.substring(1);
                    data.append(part).append('\n');
                    if (data.length() > MAX_FRAME) throw new IOException("AI 响应事件过大");
                }
            } else if (!(total == 1 && ch == '\uFEFF')) {
                line.append((char) ch);
                if (line.length() > MAX_FRAME) throw new IOException("AI 响应事件过大");
            }
        }
        if (line.toString().startsWith("data:")) {
            String tail = line.substring(5);
            if (tail.startsWith(" ")) tail = tail.substring(1);
            data.append(tail).append('\n');
        }
        if (!data.isEmpty()) handler.event(data.substring(0, data.length() - 1));
    }
}
