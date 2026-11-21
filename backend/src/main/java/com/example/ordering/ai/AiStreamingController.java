package com.example.ordering.ai;

import com.example.ordering.capability.CapabilityExecutionContext;
import com.example.ordering.common.ApiResponse;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PreDestroy;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Authenticated app SSE adapter. The legacy JSON endpoint stays available for old clients. */
@RestController
public class AiStreamingController {
    private final AiResponseService service;
    private final AiProperties properties;
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(16, 16, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(16), task -> {
                Thread thread = new Thread(task, "ai-stream-worker"); thread.setDaemon(true); return thread;
            });
    private final ScheduledExecutorService heartbeats = Executors.newScheduledThreadPool(2, task -> {
        Thread thread = new Thread(task, "ai-stream-heartbeat"); thread.setDaemon(true); return thread;
    });

    public AiStreamingController(AiResponseService service, AiProperties properties) {
        this.service = service; this.properties = properties;
    }

    @PreDestroy void shutdown() { workers.shutdownNow(); heartbeats.shutdownNow(); }

    @PostMapping(value = "/api/ai/responses/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream(Authentication authentication, @RequestBody ObjectNode body) {
        var operation = service.prepareStream(CapabilityExecutionContext.from(authentication).toUserAccount(), body);
        SseEmitter emitter = new SseEmitter(properties.getStreamTimeout().toMillis() + 15000);
        AtomicReference<java.util.concurrent.ScheduledFuture<?>> heartbeat = new AtomicReference<>();
        Runnable cleanup = () -> {
            var task = heartbeat.get();
            if (task != null) task.cancel(false);
            operation.close();
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(ignored -> cleanup.run());
        try {
            emitter.send(SseEmitter.event().name("response.in_progress")
                    .data(java.util.Map.of("type", "response.in_progress"), MediaType.APPLICATION_JSON));
            heartbeat.set(heartbeats.scheduleAtFixedRate(() -> {
                try { emitter.send(SseEmitter.event().comment("keepalive")); }
                catch (IOException | IllegalStateException error) { cleanup.run(); }
            }, 10, 10, TimeUnit.SECONDS));
            workers.execute(() -> {
                try {
                    operation.run(event -> emitter.send(SseEmitter.event()
                            .name(event.path("type").asText()).data(event, MediaType.APPLICATION_JSON)));
                } catch (Exception error) {
                    String message = error instanceof ResponseStatusException status ? status.getReason()
                            : error instanceof IOException ? error.getMessage() : "AI 服务暂时不可用，请稍后再试";
                    if (message == null) message = "AI 服务暂时不可用";
                    message = message.replaceAll("sk-[A-Za-z0-9_-]+", "[redacted]");
                    try { emitter.send(SseEmitter.event().name("error").data(
                            java.util.Map.of("type", "error", "message", message), MediaType.APPLICATION_JSON)); }
                    catch (IOException | IllegalStateException ignored) { }
                } finally {
                    cleanup.run();
                    emitter.complete();
                }
            });
        } catch (IOException | RuntimeException error) {
            cleanup.run();
            emitter.completeWithError(error);
        }
        return ResponseEntity.ok().header("X-Accel-Buffering", "no")
                .header("Cache-Control", "no-cache, no-transform")
                .contentType(MediaType.parseMediaType("text/event-stream;charset=UTF-8")).body(emitter);
    }

    @ExceptionHandler({ResponseStatusException.class, IllegalArgumentException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class})
    public ResponseEntity<ApiResponse<Void>> preflightError(Exception error) {
        HttpStatusCode status = error instanceof ResponseStatusException response ? response.getStatusCode() : HttpStatus.BAD_REQUEST;
        String message = error instanceof ResponseStatusException response ? response.getReason()
                : error instanceof IllegalArgumentException ? error.getMessage() : "AI 请求格式不正确";
        String safe = message == null ? "AI 请求暂时无法完成，请稍后重试" : message.replaceAll("sk-[A-Za-z0-9_-]+", "[redacted]");
        // SSE clients still need a JSON error before streaming starts. Pin the type so content
        // negotiation cannot trigger an error dispatch and obscure the original status as 401.
        return ResponseEntity.status(status).header("Cache-Control", "no-store")
                .header("X-Error-Code", status.is4xxClientError() ? "INVALID_ARGUMENT" : "INTERNAL_ERROR")
                .contentType(MediaType.APPLICATION_JSON).body(ApiResponse.fail(safe));
    }
}
