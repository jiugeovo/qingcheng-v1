package com.qingcheng.ordering;

import org.json.JSONObject;
import java.io.IOException;

/** Accumulates only public answer and reasoning-summary events. */
final class AiStreamState {
    record Update(String answer, String summary, String status, boolean completed) { }
    private String answer = "", summary = "";
    private boolean completed;
    private JSONObject response;
    private static final int MAX_TEXT = 256 * 1024;

    synchronized Update accept(JSONObject event) throws IOException {
        switch (event.optString("type")) {
            case "response.output_text.delta" -> answer += event.optString("delta");
            case "response.reasoning_summary_text.delta" -> summary += event.optString("delta");
            case "response.completed" -> {
                response = event.optJSONObject("response");
                if (response == null) throw new IOException("AI 完成事件格式不正确");
                answer = response.optString("output_text", answer);
                summary = response.optString("reasoning_summary", summary);
                completed = true;
            }
            case "error", "response.failed", "response.incomplete" ->
                    throw new IOException(event.optString("message", "AI 回复中断，已保留收到的内容"));
            default -> { }
        }
        if (answer.length() + summary.length() > MAX_TEXT) throw new IOException("AI 回复过长，已停止接收");
        return snapshot();
    }

    synchronized Update snapshot() {
        return new Update(answer, summary, completed ? "" : answer.isEmpty() ? "正在思考…" : "正在回复…", completed);
    }

    synchronized JSONObject result() throws IOException {
        if (!completed || response == null) throw new IOException("AI 连接提前中断，已保留收到的内容");
        return response;
    }
}
