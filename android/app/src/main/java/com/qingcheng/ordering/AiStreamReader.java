package com.qingcheng.ordering;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import org.json.JSONException;
import org.json.JSONObject;

final class AiStreamReader {
    static final int MAX_FRAME = 1024 * 1024;

    static JSONObject read(InputStream input, AiStreamState state, AiStreamSession session,
                           Consumer<AiStreamState.Update> listener) throws IOException, JSONException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        StringBuilder line = new StringBuilder(), data = new StringBuilder();
        int total = 0;
        long started = System.nanoTime();
        for (int ch; (ch = reader.read()) >= 0;) {
            if (session.isCancelled()) throw new IOException("已停止生成");
            if (System.nanoTime() - started > 390_000_000_000L)
                throw new IOException("AI 回复等待超时，已保留收到的内容");
            if (++total > ApiClient.MAX_RESPONSE_BYTES) throw new IOException("AI 响应过大");
            if (ch == '\n') {
                String value = line.toString();
                if (value.endsWith("\r")) value = value.substring(0, value.length() - 1);
                line.setLength(0);
                if (value.isEmpty()) {
                    if (data.length() > 0) {
                        boolean done = emit(data.substring(0, data.length() - 1), state, listener);
                        data.setLength(0);
                        if (done) return state.result();
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
        if (data.length() > 0) emit(data.substring(0, data.length() - 1), state, listener);
        return state.result();
    }

    private static boolean emit(String data, AiStreamState state, Consumer<AiStreamState.Update> listener)
            throws IOException, JSONException {
        if ("[DONE]".equals(data)) return true;
        AiStreamState.Update update = state.accept(new JSONObject(data));
        listener.accept(update);
        return update.completed();
    }
}
