package com.example.ordering.ai;

import com.example.ordering.upload.ImageFileType;
import com.example.ordering.upload.UploadProperties;
import com.example.ordering.user.UserAccount;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import jakarta.annotation.PreDestroy;
import java.util.regex.Pattern;

@Service
public class AiResponseService {

    private static final int CONTEXT_MESSAGE_LIMIT = 30;
    private static final int CONTEXT_TEXT_LIMIT = 12000;
    private static final int CONTEXT_SINGLE_MESSAGE_LIMIT = 1200;
    private static final long MAX_AI_IMAGE_BYTES = 8L * 1024 * 1024;

    private final AiProperties aiProperties;
    private final UploadProperties uploadProperties;
    private final AiConversationService aiConversationService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final AiRequestPolicy requestPolicy;
    private final AiModelCatalog models;
    private final Semaphore requestSlots;
    private final ScheduledExecutorService streamDeadlines = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "ai-stream-deadlines");
        thread.setDaemon(true);
        return thread;
    });

    @PreDestroy void closeStreamDeadlines() { streamDeadlines.shutdownNow(); }

    AiResponseStream prepareStream(UserAccount user, ObjectNode body) {
        if (body == null || body.isEmpty()) throw new IllegalArgumentException("请求体不能为空");
        if (!StringUtils.hasText(aiProperties.getApiKey()))
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI 助手密钥未配置");
        if (!requestSlots.tryAcquire())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI 正忙，请稍后再试");
        try {
            ObjectNode normalized = requestPolicy.normalize(body);
            ObjectNode payload = buildValidatedOpenAiPayload(user, normalized);
            payload.put("stream", true);
            String model = payload.path("model").asText();
            if (AiModelCatalog.reasoningSummarySupported(model))
                payload.putObject("reasoning").put("effort", "low").put("summary", "auto");
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(normalizeBaseUrl(aiProperties.getBaseUrl()) + "/responses"))
                    .timeout(aiProperties.getStreamTimeout())
                    .header("Authorization", "Bearer " + aiProperties.getApiKey())
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            return new AiResponseStream(httpClient, request, objectMapper, response -> {
                String text = extractResponseText(response);
                if (text.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "AI 未返回回答内容，请稍后再试");
                String actualModel = effectiveModel(model, response);
                attachSavedExchange(user, normalized, actualModel, response);
                // Keep the client envelope small and omit supplier-private metadata and reasoning.
                ObjectNode result = objectMapper.createObjectNode();
                result.put("id", response.path("id").asText(""));
                result.put("status", "completed");
                result.put("model", actualModel);
                result.put("output_text", text);
                result.put("reasoning_summary", AiReasoningSummary.extract(response));
                result.set("conversationId", response.get("conversationId"));
                result.set("savedMessages", response.get("savedMessages"));
                return result;
            }, requestSlots::release, aiProperties.getStreamTimeout(), streamDeadlines);
        } catch (IOException | RuntimeException error) {
            requestSlots.release();
            if (error instanceof RuntimeException runtime) throw runtime;
            throw new UncheckedIOException((IOException) error);
        }
    }

    @Autowired
    public AiResponseService(
            AiProperties aiProperties,
            UploadProperties uploadProperties,
            AiConversationService aiConversationService,
            ObjectMapper objectMapper,
            AiModelCatalog models
    ) {
        this(aiProperties, uploadProperties, aiConversationService, objectMapper, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(12))
                .build(), models);
    }

    AiResponseService(
            AiProperties aiProperties,
            UploadProperties uploadProperties,
            AiConversationService aiConversationService,
            ObjectMapper objectMapper,
            HttpClient httpClient
    ) {
        this(aiProperties, uploadProperties, aiConversationService, objectMapper, httpClient,
                new AiModelCatalog(aiProperties, objectMapper, httpClient, System::nanoTime));
    }

    AiResponseService(AiProperties aiProperties, UploadProperties uploadProperties,
            AiConversationService aiConversationService, ObjectMapper objectMapper,
            HttpClient httpClient, AiModelCatalog models
    ) {
        this.aiProperties = aiProperties;
        this.uploadProperties = uploadProperties;
        this.aiConversationService = aiConversationService;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.requestPolicy = new AiRequestPolicy(objectMapper);
        this.models = models;
        this.requestSlots = new Semaphore(aiProperties.getMaxConcurrentRequests());
    }

    public JsonNode createResponse(UserAccount user, Object request) {
        JsonNode body = request instanceof JsonNode json ? json
                : request == null ? null : objectMapper.valueToTree(request);
        if (body != null && !body.isObject()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求体格式不正确");
        }
        ObjectNode requestBody = (ObjectNode) body;
        if (requestBody == null || requestBody.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求体不能为空");
        }
        if (!StringUtils.hasText(aiProperties.getApiKey())) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "AI 助手密钥未配置");
        }

        // Admission is non-blocking and covers validation, image processing, HTTP and history writes.
        if (!requestSlots.tryAcquire()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI 正忙，请稍后再试");
        }
        try {
            return createOpenAiResponse(user, requestPolicy.normalize(requestBody));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AI service request interrupted", error);
        } catch (IOException error) {
            throw new UncheckedIOException("AI service request failed", error);
        } finally {
            requestSlots.release();
        }
    }

    private JsonNode createOpenAiResponse(UserAccount user, ObjectNode requestBody)
            throws IOException, InterruptedException {
        ObjectNode payload = buildValidatedOpenAiPayload(user, requestBody);
        JsonNode body = requestModel(payload);
        attachSavedExchange(user, requestBody, effectiveModel(payload.path("model").asText(), body), body);
        return body;
    }

    record OrderingDecision(String text, String model) { }

    /** Server-owned ordering protocol. No client tools are accepted. */
    OrderingDecision orderingDecision(UserAccount user, Long conversationId, String message, String instructions) {
        if (!StringUtils.hasText(aiProperties.getApiKey()))
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI 助手密钥未配置");
        if (!requestSlots.tryAcquire())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI 正忙，请稍后再试");
        try {
            ObjectNode body = objectMapper.createObjectNode().put("input", message);
            if (conversationId != null) body.put("conversationId", conversationId);
            ObjectNode payload = buildValidatedOpenAiPayload(user, requestPolicy.normalize(body));
            ArrayNode input = objectMapper.createArrayNode();
            input.add(openAiTextMessage("system", instructions));
            JsonNode current = payload.get("input");
            if (current.isArray()) current.forEach(input::add);
            else input.add(openAiTextMessage("user", current.asText()));
            payload.set("input", input);
            if (AiModelCatalog.reasoningSummarySupported(payload.path("model").asText()))
                payload.putObject("reasoning").put("effort", "low");
            JsonNode response = requestModel(payload, aiProperties.getStreamTimeout());
            if (!"completed".equals(response.path("status").asText("completed"))
                    || extractResponseText(response).isBlank())
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI 未返回完整点菜指令，请重试");
            return new OrderingDecision(extractResponseText(response),
                    effectiveModel(payload.path("model").asText(), response));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AI 点菜请求已停止", error);
        } catch (IOException error) {
            throw new UncheckedIOException("AI 点菜服务连接失败", error);
        } finally {
            requestSlots.release();
        }
    }

    private JsonNode requestModel(ObjectNode payload) throws IOException, InterruptedException {
        return requestModel(payload, aiProperties.getRequestTimeout());
    }

    private JsonNode requestModel(ObjectNode payload, Duration timeout) throws IOException, InterruptedException {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(normalizeBaseUrl(aiProperties.getBaseUrl()) + "/responses"))
                .timeout(timeout)
                .header("Authorization", "Bearer " + aiProperties.getApiKey())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException error) {
            throw new ResponseStatusException(
                    HttpStatus.GATEWAY_TIMEOUT,
                    "AI 服务响应超时，请稍后再试。",
                    error
            );
        }
        JsonNode body = parseBody(response.body());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    extractErrorMessage(body, response.body())
            );
        }
        return body;
    }

    private String effectiveModel(String requested, JsonNode response) {
        if (aiProperties.isModelSelectionEnabled()) return requested;
        String actual = response.path("model").asText("");
        return AiModelCatalog.MODEL_ID.matcher(actual).matches() ? actual : requested;
    }

    ObjectNode buildOpenAiPayload(UserAccount user, ObjectNode requestBody) {
        return buildValidatedOpenAiPayload(user, requestPolicy.normalize(requestBody));
    }

    private ObjectNode buildValidatedOpenAiPayload(UserAccount user, ObjectNode requestBody) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.set("input", requestBody.get("input").deepCopy());
        payload.put("max_output_tokens", requestBody.path("max_output_tokens").intValue());
        payload.put("store", false);
        payload.put("stream", false);
        sanitizePayloadInputImages(payload);
        String model = models.resolve(requestBody.get("model"));
        payload.put("model", model);
        Long conversationId = resolveConversationId(requestBody);
        attachOpenAiSystemContext(user, conversationId, requestBody, payload);
        payload.put("prompt_cache_key", buildPromptCacheKey(user, conversationId, model));
        if (countImages(requestBody) == 0
                && conversationId != null
                && isContextImageReadRequest(requestBody)) {
            attachRecentContextImage(user, conversationId, payload);
        }
        return payload;
    }

    private void attachOpenAiSystemContext(UserAccount user, Long conversationId, ObjectNode requestBody, ObjectNode payload) {
        String historyContext = "";
        if (conversationId != null) {
            List<AiMessageResponse> history = aiConversationService.recentMessages(user, conversationId, CONTEXT_MESSAGE_LIMIT);
            historyContext = buildConversationContext(history);
        }
        if (!StringUtils.hasText(historyContext)) {
            return;
        }
        JsonNode currentInput = payload.get("input");
        ArrayNode mergedInput = objectMapper.createArrayNode();
        mergedInput.add(openAiTextMessage("system", historyContext.trim()));
        appendCurrentOpenAiInput(mergedInput, currentInput, payload.path("prompt").asText(""));
        payload.set("input", mergedInput);
    }

    private String buildConversationContext(List<AiMessageResponse> history) {
        if (history == null || history.isEmpty()) {
            return "";
        }
        StringBuilder context = new StringBuilder();
        context.append("Conversation history for context only. Use it to answer the final user message. Older user messages are not new requests.");
        for (AiMessageResponse message : history) {
            String content = normalizeHistoryContent(message);
            if (!StringUtils.hasText(content)) {
                continue;
            }
            String role = "assistant".equals(message.role()) ? "Assistant" : "User";
            String line = "\n" + role + ": " + truncate(content, CONTEXT_SINGLE_MESSAGE_LIMIT);
            if (context.length() + line.length() > CONTEXT_TEXT_LIMIT) {
                break;
            }
            context.append(line);
        }
        return context.toString();
    }

    private String normalizeHistoryContent(AiMessageResponse message) {
        String content = message.content() == null ? "" : AiReasoningSummary.answer(message.content()).trim();
        if (message.imageCount() > 0) {
            String imageNote = "[attached images: " + message.imageCount() + "]";
            content = StringUtils.hasText(content) ? content + " " + imageNote : imageNote;
        }
        return content.replaceAll("\\s+", " ");
    }

    private String truncate(String text, int limit) {
        if (text.length() <= limit) {
            return text;
        }
        return text.substring(0, limit) + "...";
    }

    private void appendCurrentOpenAiInput(ArrayNode mergedInput, JsonNode currentInput, String fallbackPrompt) {
        if (currentInput != null && currentInput.isArray()) {
            for (JsonNode item : currentInput) {
                mergedInput.add(item.deepCopy());
            }
            return;
        }
        if (currentInput != null && currentInput.isTextual()) {
            mergedInput.add(openAiTextMessage("user", currentInput.asText()));
            return;
        }
        if (StringUtils.hasText(fallbackPrompt)) {
            mergedInput.add(openAiTextMessage("user", fallbackPrompt));
        }
    }

    private ObjectNode openAiTextMessage(String role, String text) {
        ObjectNode message = objectMapper.createObjectNode();
        message.put("role", role);
        ArrayNode content = objectMapper.createArrayNode();
        ObjectNode textPart = objectMapper.createObjectNode();
        textPart.put("type", "input_text");
        textPart.put("text", text);
        content.add(textPart);
        message.set("content", content);
        return message;
    }

    private boolean isContextImageReadRequest(ObjectNode requestBody) {
        String text = extractUserText(requestBody).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        return StringUtils.hasText(text) && isImageReadIntent(text);
    }

    private boolean isImageReadIntent(String text) {
        String zhReadVerb = "(?:读图|看图|识图|分析|描述|解释|看看|看一下|这张图|这张图片|这张照片|图片里|图里|照片里|有什么|是什么|能看出|评价|判断)";
        return Pattern.compile(zhReadVerb).matcher(text).find()
                || Pattern.compile("\\b(describe|analy[sz]e|read|identify|explain|caption|inspect|recognize)\\b").matcher(text).find()
                || Pattern.compile("\\b(what|who|where)\\b.{0,32}\\b(in|on)\\b.{0,16}\\b(image|picture|photo)\\b").matcher(text).find()
                || Pattern.compile("\\b(image|picture|photo)\\b.{0,24}\\b(show|contain|depict|mean)\\b").matcher(text).find();
    }

    private boolean attachRecentContextImage(UserAccount user, Long conversationId, ObjectNode payload) {
        List<String> imageRefs = aiConversationService.recentImageRefs(user, conversationId, 1);
        for (String imageRef : imageRefs) {
            String imageUrl = toInputImageUrl(imageRef);
            if (StringUtils.hasText(imageUrl)) {
                appendInputImage(payload, imageUrl);
                return true;
            }
        }
        return false;
    }

    private String toInputImageUrl(String imageRef) {
        Path imagePath = resolveLocalUploadImage(imageRef);
        if (imagePath == null) {
            return "";
        }
        try {
            if (Files.size(imagePath) > MAX_AI_IMAGE_BYTES) {
                return "";
            }
            byte[] bytes;
            // The bounded read also covers a file growing after the size check. Do not
            // follow a last-component symlink introduced between resolution and opening.
            try (InputStream input = Files.newInputStream(imagePath, LinkOption.NOFOLLOW_LINKS)) {
                bytes = readImageBytes(input);
            }
            ImageFileType imageType = ImageFileType.detect(bytes).orElse(null);
            if (imageType == null) {
                return "";
            }
            return "data:" + imageType.mimeType() + ";base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (IOException error) {
            return "";
        }
    }

    private void sanitizePayloadInputImages(ObjectNode payload) {
        JsonNode input = payload.get("input");
        if (input != null && input.isArray()) {
            for (JsonNode item : input) {
                JsonNode content = item.get("content");
                if (content != null && content.isArray()) {
                    for (JsonNode part : content) {
                        if ("input_image".equals(part.path("type").asText()) && part instanceof ObjectNode imagePart) {
                            String imageUrl = imagePart.path("image_url").asText("");
                            imagePart.put("image_url", toCurrentRequestImageUrl(imageUrl));
                        }
                    }
                }
            }
        }
    }

    private String toCurrentRequestImageUrl(String imageUrl) {
        if (!StringUtils.hasText(imageUrl)) {
            throw new IllegalArgumentException("图片来源不受信任");
        }
        String trimmed = imageUrl.trim();
        if (trimmed.startsWith("data:image/")) {
            validateDataImage(trimmed);
            return trimmed;
        }
        String cleanPath = trustedUploadRef(trimmed);
        if (!StringUtils.hasText(cleanPath)) {
            throw new IllegalArgumentException("图片来源不受信任");
        }
        String converted = toInputImageUrl(cleanPath);
        if (!StringUtils.hasText(converted)) {
            throw new IllegalArgumentException("图片来源不受信任");
        }
        return converted;
    }

    private String toTrustedHistoryImageRef(String imageUrl) {
        if (!StringUtils.hasText(imageUrl)) {
            return "";
        }
        String trimmed = imageUrl.trim();
        String uploadRef = trustedUploadRef(trimmed);
        if (StringUtils.hasText(uploadRef)) {
            return uploadRef;
        }
        if (trimmed.startsWith("data:image/")) {
            return saveInputDataImage(trimmed);
        }
        throw new IllegalArgumentException("图片来源不受信任");
    }

    private String trustedUploadRef(String imageRef) {
        if (resolveLocalUploadImage(imageRef) == null) {
            return "";
        }
        String cleanPath = imageRef.trim().replace('\\', '/');
        return cleanPath.startsWith("/") ? cleanPath.substring(1) : cleanPath;
    }

    private Path resolveLocalUploadImage(String imageRef) {
        if (!StringUtils.hasText(imageRef)) {
            return null;
        }
        String cleanPath = imageRef.trim().replace('\\', '/');
        cleanPath = cleanPath.startsWith("/") ? cleanPath.substring(1) : cleanPath;
        if (!cleanPath.startsWith("uploads/") || cleanPath.contains("..")) {
            return null;
        }
        try {
            Path baseDir = Paths.get(uploadProperties.getDir()).toAbsolutePath().normalize();
            Path imagePath = baseDir.resolve(cleanPath.substring("uploads/".length())).normalize();
            Path privateDir = baseDir.resolve("friends");
            if (!imagePath.startsWith(baseDir) || imagePath.startsWith(privateDir)) {
                return null;
            }
            Path realBase = baseDir.toRealPath();
            Path realImage = imagePath.toRealPath();
            if (!realImage.startsWith(realBase) || realImage.startsWith(realBase.resolve("friends"))
                    || !Files.isRegularFile(realImage, LinkOption.NOFOLLOW_LINKS)) {
                return null;
            }
            // A public-looking alias must not expose the private tree even when that
            // tree itself is an administrator-provisioned directory link.
            if (Files.exists(privateDir) && realImage.startsWith(privateDir.toRealPath())) {
                return null;
            }
            return realImage;
        } catch (IOException | InvalidPathException | SecurityException error) {
            return null;
        }
    }

    static byte[] readImageBytes(InputStream input) throws IOException {
        byte[] bytes = input.readNBytes(Math.toIntExact(MAX_AI_IMAGE_BYTES + 1));
        if (bytes.length > MAX_AI_IMAGE_BYTES) {
            throw new IOException("AI image exceeds size limit");
        }
        return bytes;
    }

    private void validateDataImage(String dataImage) {
        ParsedDataImage parsed = parseDataImage(dataImage);
        byte[] bytes = decodeBase64Image(parsed.base64());
        ImageFileType detected = ImageFileType.detect(bytes)
                .orElseThrow(() -> new IllegalArgumentException("图片来源不受信任"));
        if (!detected.matchesDataMime(parsed.mimeType())) {
            throw new IllegalArgumentException("图片来源不受信任");
        }
    }

    private String saveInputDataImage(String dataImage) {
        ParsedDataImage parsed = parseDataImage(dataImage);
        byte[] bytes = decodeBase64Image(parsed.base64());
        ImageFileType detected = ImageFileType.detect(bytes)
                .orElseThrow(() -> new IllegalArgumentException("图片来源不受信任"));
        if (!detected.matchesDataMime(parsed.mimeType())) {
            throw new IllegalArgumentException("图片来源不受信任");
        }
        try {
            return saveImage(bytes, "ai-input/" + LocalDate.now(), "input-image", detected);
        } catch (IOException error) {
            throw new IllegalArgumentException("图片来源不受信任");
        }
    }

    private ParsedDataImage parseDataImage(String dataImage) {
        int commaIndex = dataImage.indexOf(',');
        if (commaIndex < 0) {
            throw new IllegalArgumentException("图片来源不受信任");
        }
        String header = dataImage.substring(0, commaIndex).toLowerCase(Locale.ROOT);
        if (!header.startsWith("data:image/") || !header.endsWith(";base64")) {
            throw new IllegalArgumentException("图片来源不受信任");
        }
        String mimeType = header.substring("data:".length(), header.length() - ";base64".length());
        ImageFileType.fromDataMime(mimeType)
                .orElseThrow(() -> new IllegalArgumentException("图片来源不受信任"));
        return new ParsedDataImage(mimeType, dataImage.substring(commaIndex + 1).trim());
    }

    private byte[] decodeBase64Image(String base64) {
        String normalized = base64 == null ? "" : base64.replaceAll("\\s+", "");
        long estimatedBytes = (normalized.length() * 3L) / 4L;
        if (estimatedBytes > MAX_AI_IMAGE_BYTES + 2) {
            throw new IllegalArgumentException("AI 图片超过大小限制");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(normalized);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("图片来源不受信任");
        }
        if (bytes.length > MAX_AI_IMAGE_BYTES) {
            throw new IllegalArgumentException("AI 图片超过大小限制");
        }
        return bytes;
    }

    private record ParsedDataImage(String mimeType, String base64) {
    }

    private void appendInputImage(ObjectNode payload, String imageUrl) {
        JsonNode input = payload.get("input");
        ArrayNode inputArray;
        if (input != null && input.isArray()) {
            inputArray = (ArrayNode) input;
        } else {
            inputArray = objectMapper.createArrayNode();
            payload.set("input", inputArray);
        }

        ObjectNode userMessage = null;
        for (int index = inputArray.size() - 1; index >= 0; index--) {
            JsonNode item = inputArray.get(index);
            if (item instanceof ObjectNode objectItem && "user".equals(objectItem.path("role").asText())) {
                userMessage = objectItem;
                break;
            }
        }
        if (userMessage == null) {
            userMessage = objectMapper.createObjectNode();
            userMessage.put("role", "user");
            inputArray.add(userMessage);
        }

        ArrayNode content;
        JsonNode currentContent = userMessage.get("content");
        if (currentContent != null && currentContent.isArray()) {
            content = (ArrayNode) currentContent;
        } else {
            content = objectMapper.createArrayNode();
            if (currentContent != null && currentContent.isTextual()) {
                ObjectNode textPart = objectMapper.createObjectNode();
                textPart.put("type", "input_text");
                textPart.put("text", currentContent.asText());
                content.add(textPart);
            }
            userMessage.set("content", content);
        }

        ObjectNode imagePart = objectMapper.createObjectNode();
        imagePart.put("type", "input_image");
        imagePart.put("image_url", imageUrl);
        imagePart.put("detail", "auto");
        content.add(imagePart);
    }

    private Long resolveConversationId(ObjectNode requestBody) {
        return requestBody.hasNonNull("conversationId")
                ? requestBody.path("conversationId").asLong()
                : null;
    }

    private String buildPromptCacheKey(UserAccount user, Long conversationId, String model) {
        String scope = conversationId == null ? "new" : conversationId.toString();
        return "qingcheng-user-" + user.getId() + "-" + model + "-default-" + scope;
    }

    private void attachSavedExchange(UserAccount user, ObjectNode requestBody, String model, JsonNode responseBody) {
        if (!(responseBody instanceof ObjectNode responseObject)) {
            return;
        }
        Long conversationId = requestBody.hasNonNull("conversationId")
                ? requestBody.path("conversationId").asLong()
                : null;
        String displayText = requestBody.path("displayText").asText("");
        String userContent = StringUtils.hasText(displayText) ? displayText : extractUserText(requestBody);
        int imageCount = countImages(requestBody);
        List<String> imageRefs = extractImageRefs(requestBody);
        String assistantContent = AiReasoningSummary.combine(
                AiReasoningSummary.extract(responseBody), extractResponseText(responseBody));
        String responseId = responseBody.path("id").asText(null);
        AiExchangeResult saved = aiConversationService.saveExchange(
                user,
                conversationId,
                model,
                userContent,
                imageCount,
                imageRefs,
                assistantContent,
                responseId
        );
        responseObject.put("conversationId", saved.conversationId());
        responseObject.set("savedMessages", objectMapper.valueToTree(List.of(
                saved.userMessage(),
                saved.assistantMessage()
        )));
    }

    private String extractUserText(ObjectNode requestBody) {
        JsonNode input = requestBody.get("input");
        if (input != null && input.isTextual()) {
            return input.asText();
        }
        StringBuilder text = new StringBuilder();
        if (input != null && input.isArray()) {
            for (JsonNode item : input) {
                JsonNode content = item.get("content");
                if (content == null) {
                    continue;
                }
                if (content.isTextual()) {
                    appendText(text, content.asText());
                    continue;
                }
                if (content.isArray()) {
                    for (JsonNode part : content) {
                        appendText(text, part.path("text").asText(""));
                    }
                }
            }
        }
        return text.toString().trim();
    }

    private int countImages(ObjectNode requestBody) {
        int count = 0;
        JsonNode input = requestBody.get("input");
        if (input != null && input.isArray()) {
            for (JsonNode item : input) {
                JsonNode content = item.get("content");
                if (content != null && content.isArray()) {
                    for (JsonNode part : content) {
                        if ("input_image".equals(part.path("type").asText())) {
                            count++;
                        }
                    }
                }
            }
        }
        return count;
    }

    private List<String> extractImageRefs(ObjectNode requestBody) {
        List<String> imageRefs = new ArrayList<>();
        JsonNode input = requestBody.get("input");
        if (input != null && input.isArray()) {
            for (JsonNode item : input) {
                JsonNode content = item.get("content");
                if (content != null && content.isArray()) {
                    for (JsonNode part : content) {
                        if ("input_image".equals(part.path("type").asText())) {
                            String imageUrl = part.path("image_url").asText("");
                            if (StringUtils.hasText(imageUrl)) {
                                String imageRef = toTrustedHistoryImageRef(imageUrl);
                                if (StringUtils.hasText(imageRef)) {
                                    imageRefs.add(imageRef);
                                }
                            }
                        }
                    }
                }
            }
        }
        return imageRefs;
    }

    private String extractResponseText(JsonNode body) {
        if (body.path("output_text").isTextual() && StringUtils.hasText(body.path("output_text").asText())) {
            return body.path("output_text").asText();
        }
        StringBuilder text = new StringBuilder();
        JsonNode output = body.path("output");
        if (output.isArray()) {
            for (JsonNode item : output) {
                JsonNode content = item.path("content");
                if (content.isArray()) {
                    for (JsonNode part : content) {
                        appendText(text, part.path("text").asText(""));
                    }
                }
            }
        }
        return text.toString().trim();
    }

    private String saveImage(byte[] bytes, String folder, String filenamePrefix, ImageFileType imageType) throws IOException {
        Path baseDir = Paths.get(uploadProperties.getDir()).toAbsolutePath().normalize();
        Path uploadDir = baseDir.resolve(folder).normalize();
        Files.createDirectories(uploadDir);

        String filename = filenamePrefix + "-" + UUID.randomUUID().toString().replace("-", "") + "." + imageType.extension();
        Path target = uploadDir.resolve(filename).normalize();
        if (!target.startsWith(baseDir)) {
            throw new IllegalArgumentException("AI image upload directory is invalid");
        }
        Files.write(target, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        return "uploads/" + folder + "/" + filename;
    }

    private void appendText(StringBuilder builder, String text) {
        if (!StringUtils.hasText(text)) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append('\n');
        }
        builder.append(text.trim());
    }

    private JsonNode parseBody(String body) throws IOException {
        if (!StringUtils.hasText(body)) {
            return objectMapper.createObjectNode();
        }
        return objectMapper.readTree(body);
    }

    private String extractErrorMessage(JsonNode body, String rawBody) {
        JsonNode errorMessage = body.at("/error/message");
        if (errorMessage.isTextual()) {
            return friendlyUpstreamMessage(errorMessage.asText());
        }
        if (StringUtils.hasText(rawBody)) {
            String message = rawBody.length() > 500 ? rawBody.substring(0, 500) : rawBody;
            return friendlyUpstreamMessage(message);
        }
        return "AI 助手请求失败";
    }

    private String friendlyUpstreamMessage(String message) {
        if (message.contains("Insufficient balance") || message.contains("insufficient_quota")) {
            return "AI 服务额度不足，请检查 relay 账号余额或每日额度。";
        }
        if (message.contains("content filter")) {
            return "AI 服务拦截了这次请求，请换一种问法或稍后再试。";
        }
        return message;
    }

    private String normalizeBaseUrl(String baseUrl) {
        String normalized = StringUtils.hasText(baseUrl) ? baseUrl.trim() : "https://example.invalid/v1";
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
