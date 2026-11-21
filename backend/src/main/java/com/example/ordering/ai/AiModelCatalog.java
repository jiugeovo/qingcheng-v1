package com.example.ordering.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/** A bounded, cached provider catalog. Provider credentials and raw metadata never leave this service. */
@Service
public class AiModelCatalog {
    static final Pattern MODEL_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/-]{0,63}");
    private static final int MAX_BODY_BYTES = 512 * 1024;
    private final AiProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient http;
    private final LongSupplier nanoTime;
    private Catalog cached;
    private long loadedAt;

    public record Model(String id) { }
    public record Catalog(String defaultModel, List<Model> models) { }

    @Autowired
    public AiModelCatalog(AiProperties properties, ObjectMapper mapper) {
        this(properties, mapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build(), System::nanoTime);
    }

    AiModelCatalog(AiProperties properties, ObjectMapper mapper, HttpClient http, LongSupplier nanoTime) {
        this.properties = properties;
        this.mapper = mapper;
        this.http = http;
        this.nanoTime = nanoTime;
    }

    public synchronized Catalog list(boolean refresh) {
        long now = nanoTime.getAsLong();
        long ttl = TimeUnit.SECONDS.toNanos(refresh ? 5 : 60);
        if (cached != null && now - loadedAt < ttl) return cached;
        if (properties.getApiKey() == null || properties.getApiKey().isBlank())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI 助手密钥未配置");
        String base = properties.getBaseUrl().replaceAll("/+$", "");
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/models"))
                .timeout(Duration.ofSeconds(10)).header("Accept", "application/json")
                .header("Authorization", "Bearer " + properties.getApiKey()).GET().build();
        CompletableFuture<HttpResponse<byte[]>> pending = null;
        try {
            pending = http.sendAsync(request, ignored -> new LimitedBody());
            HttpResponse<byte[]> response = pending.get(10, TimeUnit.SECONDS);
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IllegalStateException();
            JsonNode root = mapper.readTree(response.body());
            JsonNode data = root.path("data");
            if (!data.isArray() || data.size() > 1000) throw new IllegalStateException();
            LinkedHashSet<String> ids = new LinkedHashSet<>();
            for (JsonNode model : data) {
                JsonNode id = model.get("id");
                if (id == null || !id.isTextual() || !MODEL_ID.matcher(id.textValue()).matches()) continue;
                // When the provider supplies capabilities, exclude models that cannot use Responses.
                JsonNode endpoints = model.get("supported_endpoint_types");
                if (endpoints != null && endpoints.isArray()) {
                    boolean responses = false;
                    for (JsonNode endpoint : endpoints) responses |= "openai-response".equals(endpoint.asText());
                    if (!responses) continue;
                }
                ids.add(id.textValue());
            }
            if (ids.isEmpty()) throw new IllegalStateException();
            String defaultModel = properties.getModel();
            if (!properties.isModelSelectionEnabled()) {
                defaultModel = root.path("default_model").asText(properties.getModel());
                if (!ids.contains(defaultModel)) throw new IllegalStateException("Missing provider default");
            }
            cached = new Catalog(defaultModel, properties.isModelSelectionEnabled()
                    ? ids.stream().sorted().map(Model::new).toList() : List.of(new Model(defaultModel)));
            loadedAt = nanoTime.getAsLong();
            return cached;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (Exception error) {
            // Do not expose supplier response bodies: they may contain authentication details.
            throw unavailable();
        } finally {
            if (pending != null && !pending.isDone()) pending.cancel(true);
        }
    }

    String resolve(JsonNode requested) {
        // Clients from earlier releases may still send their saved model. In fixed
        // mode the server follows the provider default or the configured model,
        // and ignores that legacy selection.
        if (!properties.isModelSelectionEnabled()) return list(false).defaultModel();
        // Legacy clients omit the model; preserve their configured default even during a catalog outage.
        if (requested == null || requested.isNull()) return properties.getModel();
        String id = requested.asText();
        if (list(false).models().stream().noneMatch(model -> model.id().equals(id)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "所选模型已不可用，请刷新模型列表后重新选择");
        return id;
    }

    static boolean reasoningSummarySupported(String model) {
        return model.startsWith("gpt-5") || model.startsWith("gpt-6");
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "模型列表暂时无法加载，请稍后刷新重试");
    }

    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        public CompletionStage<byte[]> getBody() { return result; }
        public void onSubscribe(Flow.Subscription value) { subscription = value; value.request(1); }
        public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > MAX_BODY_BYTES - bytes.size()) {
                    subscription.cancel(); result.completeExceptionally(new IllegalStateException()); return;
                }
                byte[] chunk = new byte[buffer.remaining()]; buffer.get(chunk); bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        public void onError(Throwable error) { result.completeExceptionally(error); }
        public void onComplete() { result.complete(bytes.toByteArray()); }
    }
}
