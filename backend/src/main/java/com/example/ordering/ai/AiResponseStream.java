package com.example.ordering.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/** A single admitted generation. Disconnect/timeout closes its upstream and releases its slot. */
final class AiResponseStream implements AutoCloseable {
    interface Sink { void send(ObjectNode event) throws IOException; }
    private final HttpClient client;
    private final HttpRequest request;
    private final ObjectMapper mapper;
    private final Function<ObjectNode, ObjectNode> complete;
    private final Runnable release;
    private final Duration timeout;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean cancelled = new AtomicBoolean(), released = new AtomicBoolean();
    private volatile InputStream upstream;
    private volatile Thread worker;
    private volatile boolean timedOut;
    private boolean finished;
    private final StringBuilder answer = new StringBuilder(), summary = new StringBuilder();

    AiResponseStream(HttpClient client, HttpRequest request, ObjectMapper mapper,
                     Function<ObjectNode, ObjectNode> complete, Runnable release,
                     Duration timeout, ScheduledExecutorService scheduler) {
        this.client = client; this.request = request; this.mapper = mapper;
        this.complete = complete; this.release = release;
        this.timeout = timeout; this.scheduler = scheduler;
    }

    void run(Sink sink) throws IOException, InterruptedException {
        worker = Thread.currentThread();
        var deadline = scheduler.schedule(() -> { timedOut = true; close(); },
                timeout.toMillis(), TimeUnit.MILLISECONDS);
        try {
            checkActive();
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            upstream = response.body();
            try (InputStream body = upstream) {
                checkActive();
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    // Supplier error payloads may contain credentials or internal details: do not relay them.
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                            response.statusCode() == 429 ? "AI 服务繁忙或额度不足，请稍后再试"
                                    : "AI 上游请求失败（" + response.statusCode() + "）");
                }
                String contentType = response.headers().firstValue("Content-Type").orElse("");
                if (contentType.contains("application/json")) {
                    byte[] bytes = body.readNBytes(AiEventReader.MAX_STREAM + 1);
                    if (bytes.length > AiEventReader.MAX_STREAM) throw new IOException("AI 响应过大");
                    JsonNode result = mapper.readTree(bytes);
                    if (!(result instanceof ObjectNode object)) throw new IOException("AI 响应格式不正确");
                    finish(object, sink);
                } else {
                    AiEventReader.read(new InputStreamReader(body, StandardCharsets.UTF_8),
                            data -> consume(data, sink));
                }
                if (!finished) throw new IOException("AI 连接提前中断，已保留收到的内容，请勿重复提交");
            }
        } catch (IOException | InterruptedException error) {
            if (timedOut) throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,
                    "AI 回复等待超时，已保留收到的内容", error);
            throw error;
        } finally {
            deadline.cancel(false);
            upstream = null;
            worker = null;
            releaseOnce();
        }
    }

    private boolean consume(String data, Sink sink) throws IOException {
        checkActive();
        if ("[DONE]".equals(data)) return false;
        JsonNode event = mapper.readTree(data);
        String type = event.path("type").asText("");
        switch (type) {
            case "response.output_text.delta", "response.refusal.delta" -> {
                String delta = event.path("delta").asText("");
                answer.append(delta);
                sink.send(mapper.createObjectNode().put("type", "response.output_text.delta").put("delta", delta));
            }
            case "response.reasoning_summary_text.delta" -> {
                String delta = event.path("delta").asText("");
                summary.append(delta);
                sink.send(mapper.createObjectNode().put("type", type).put("delta", delta));
            }
            case "response.output_text.done" -> {
                if (answer.isEmpty()) {
                    String text = event.path("text").asText("");
                    answer.append(text);
                    sink.send(mapper.createObjectNode().put("type", "response.output_text.delta").put("delta", text));
                }
            }
            case "response.reasoning_summary_text.done" -> {
                if (summary.isEmpty()) {
                    String text = event.path("text").asText("");
                    summary.append(text);
                    sink.send(mapper.createObjectNode().put("type", "response.reasoning_summary_text.delta").put("delta", text));
                }
            }
            case "response.created", "response.in_progress" ->
                    sink.send(mapper.createObjectNode().put("type", type));
            case "response.completed" -> {
                if (!(event.path("response") instanceof ObjectNode response))
                    throw new IOException("AI 完成事件格式不正确");
                finish(response, sink);
                return false;
            }
            case "response.failed", "error" -> throw new IOException("AI 上游生成失败，已保留收到的内容");
            case "response.incomplete" -> throw new IOException("AI 回复未完成，已保留收到的内容");
            default -> { /* Never forward encrypted/private reasoning, obfuscation or tool internals. */ }
        }
        return true;
    }

    private void finish(ObjectNode response, Sink sink) throws IOException {
        checkActive();
        String status = response.path("status").asText("completed");
        if (!"completed".equals(status)) throw new IOException("AI 回复未完成，已保留收到的内容");
        if (response.path("output").isArray()) {
            for (JsonNode item : response.path("output")) {
                if (item instanceof ObjectNode object) object.remove("encrypted_content");
            }
        }
        if (AiReasoningSummary.extract(response).isBlank() && !summary.isEmpty()) {
            response.withArray("output").addObject().put("type", "reasoning")
                    .putArray("summary").addObject().put("type", "summary_text").put("text", summary.toString());
        }
        if (!answer.isEmpty()) response.put("output_text", answer.toString());
        ObjectNode saved = complete.apply(response);
        checkActive();
        ObjectNode event = mapper.createObjectNode().put("type", "response.completed");
        event.set("response", saved);
        sink.send(event);
        finished = true;
    }

    private void checkActive() throws IOException {
        if (cancelled.get() || Thread.currentThread().isInterrupted())
            throw new IOException("AI 请求已停止");
    }

    @Override public void close() {
        cancelled.set(true);
        InputStream stream = upstream;
        if (stream != null) try { stream.close(); } catch (IOException ignored) { }
        Thread thread = worker;
        if (thread != null && thread != Thread.currentThread()) thread.interrupt();
        if (thread == null) releaseOnce();
    }

    private void releaseOnce() { if (released.compareAndSet(false, true)) release.run(); }
}
