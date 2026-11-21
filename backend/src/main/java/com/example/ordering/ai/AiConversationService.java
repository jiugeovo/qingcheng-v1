package com.example.ordering.ai;

import com.example.ordering.user.UserAccount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

@Service
public class AiConversationService {

    private final AiConversationRepository conversationRepository;
    private final AiMessageRepository messageRepository;

    public AiConversationService(
            AiConversationRepository conversationRepository,
            AiMessageRepository messageRepository
    ) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }

    @Transactional(readOnly = true)
    public List<AiConversationSummary> listConversations(UserAccount user) {
        return conversationRepository.findSummariesByUserId(user.getId());
    }

    @Transactional(readOnly = true)
    public AiConversationPage listConversationsPage(UserAccount user, int requestedLimit, String cursor) {
        int limit = Math.max(1, Math.min(100, requestedLimit));
        Cursor position = decodeCursor(cursor);
        List<AiConversationSummary> fetched = conversationRepository.findPageByUserId(user.getId(),
                position == null ? null : position.updatedAt(), position == null ? null : position.id(), limit + 1);
        boolean hasMore = fetched.size() > limit;
        List<AiConversationSummary> items = hasMore ? List.copyOf(fetched.subList(0, limit)) : fetched;
        String nextCursor = hasMore ? encodeCursor(items.get(items.size() - 1)) : "";
        return new AiConversationPage(items, nextCursor, hasMore);
    }

    @Transactional(readOnly = true)
    public List<AiMessageResponse> listMessages(UserAccount user, Long conversationId) {
        AiConversationEntity conversation = findOwnedConversation(user, conversationId);
        return messageRepository.findAllByConversationIdOrderByCreatedAtAsc(conversation.getId()).stream()
                .map(this::toMessageResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AiMessageResponse> recentMessages(UserAccount user, Long conversationId, int limit) {
        AiConversationEntity conversation = findOwnedConversation(user, conversationId);
        if (limit <= 0) {
            return List.of();
        }
        return messageRepository.findRecentByConversationIdOrderByCreatedAtAsc(conversation.getId(), limit).stream()
                .map(this::toMessageResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public String findPreviousResponseId(UserAccount user, Long conversationId) {
        if (conversationId == null) {
            return null;
        }
        return findOwnedConversation(user, conversationId).getPreviousResponseId();
    }

    @Transactional(readOnly = true)
    public List<String> recentImageRefs(UserAccount user, Long conversationId, int limit) {
        AiConversationEntity conversation = findOwnedConversation(user, conversationId);
        return messageRepository.findTop12ByConversationIdAndImageRefsIsNotNullOrderByCreatedAtDesc(conversation.getId())
                .stream()
                .flatMap(message -> parseImageRefs(message.getImageRefs()).stream())
                .limit(Math.max(limit, 0))
                .toList();
    }

    @Transactional
    public AiExchangeResult saveExchange(
            UserAccount user,
            Long conversationId,
            String model,
            String userContent,
            int imageCount,
            List<String> imageRefs,
            String assistantContent,
            String responseId
    ) {
        AiConversationEntity conversation = conversationId == null
                ? createConversation(user, model, userContent)
                : findOwnedConversation(user, conversationId);

        LocalDateTime now = LocalDateTime.now();
        conversation.setModel(model);
        conversation.setPreviousResponseId(responseId);
        conversation.setUpdatedAt(now);

        AiMessageEntity userMessage = new AiMessageEntity();
        userMessage.setConversationId(conversation.getId());
        userMessage.setRole("user");
        userMessage.setContent(normalizeContent(userContent, imageCount));
        userMessage.setModel(model);
        userMessage.setImageCount(Math.max(imageCount, 0));
        userMessage.setImageRefs(serializeImageRefs(imageRefs));
        userMessage.setCreatedAt(now);

        AiMessageEntity assistantMessage = new AiMessageEntity();
        assistantMessage.setConversationId(conversation.getId());
        assistantMessage.setRole("assistant");
        List<String> assistantImageRefs = extractMarkdownImageUrls(assistantContent);
        assistantMessage.setContent(normalizeContent(assistantContent, assistantImageRefs.size()));
        assistantMessage.setModel(model);
        assistantMessage.setImageCount(assistantImageRefs.size());
        assistantMessage.setImageRefs(serializeImageRefs(assistantImageRefs));
        assistantMessage.setCreatedAt(now.plusNanos(1));

        conversationRepository.save(conversation);
        AiMessageEntity savedUser = messageRepository.save(userMessage);
        AiMessageEntity savedAssistant = messageRepository.save(assistantMessage);
        return new AiExchangeResult(
                conversation.getId(),
                toMessageResponse(savedUser),
                toMessageResponse(savedAssistant)
        );
    }

    @Transactional
    public Long saveProactiveAssistantMessage(
            UserAccount user,
            String title,
            String model,
            String assistantContent,
            LocalDateTime createdAt
    ) {
        LocalDateTime timestamp = createdAt == null ? LocalDateTime.now() : createdAt;
        AiConversationEntity conversation = new AiConversationEntity();
        conversation.setUserId(user.getId());
        conversation.setUsername(user.getUsername());
        conversation.setTitle(normalizeTitle(title));
        conversation.setModel(model);
        conversation.setCreatedAt(timestamp);
        conversation.setUpdatedAt(timestamp);
        conversationRepository.save(conversation);

        AiMessageEntity assistantMessage = new AiMessageEntity();
        assistantMessage.setConversationId(conversation.getId());
        assistantMessage.setRole("assistant");
        List<String> imageRefs = extractMarkdownImageUrls(assistantContent);
        assistantMessage.setContent(normalizeContent(assistantContent, imageRefs.size()));
        assistantMessage.setModel(model);
        assistantMessage.setImageCount(imageRefs.size());
        assistantMessage.setImageRefs(serializeImageRefs(imageRefs));
        assistantMessage.setCreatedAt(timestamp);
        messageRepository.save(assistantMessage);
        return conversation.getId();
    }

    @Transactional
    public void deleteConversation(UserAccount user, Long conversationId) {
        AiConversationEntity conversation = findOwnedConversation(user, conversationId);
        messageRepository.deleteAllByConversationId(conversation.getId());
        conversationRepository.delete(conversation);
    }

    private AiConversationEntity createConversation(UserAccount user, String model, String userContent) {
        AiConversationEntity conversation = new AiConversationEntity();
        conversation.setUserId(user.getId());
        conversation.setUsername(user.getUsername());
        conversation.setTitle(toTitle(userContent));
        conversation.setModel(model);
        return conversationRepository.save(conversation);
    }

    private AiConversationEntity findOwnedConversation(UserAccount user, Long conversationId) {
        return conversationRepository.findByIdAndUserId(conversationId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("对话不存在"));
    }

    private AiMessageResponse toMessageResponse(AiMessageEntity message) {
        return new AiMessageResponse(
                message.getId(),
                message.getConversationId(),
                message.getRole(),
                message.getContent(),
                message.getModel(),
                message.getImageCount(),
                message.getCreatedAt()
        );
    }

    private String normalizeContent(String content, int imageCount) {
        String trimmed = content == null ? "" : content.trim();
        if (!trimmed.isEmpty()) {
            return trimmed;
        }
        if (imageCount > 0) {
            return "[图片]";
        }
        return "";
    }

    private String serializeImageRefs(List<String> imageRefs) {
        if (imageRefs == null || imageRefs.isEmpty()) {
            return null;
        }
        return imageRefs.stream()
                .filter(ref -> ref != null && !ref.isBlank())
                .map(String::trim)
                .limit(6)
                .reduce((left, right) -> left + "\n" + right)
                .orElse(null);
    }

    private List<String> parseImageRefs(String imageRefs) {
        if (imageRefs == null || imageRefs.isBlank()) {
            return List.of();
        }
        return imageRefs.lines()
                .map(String::trim)
                .filter(ref -> !ref.isBlank())
                .toList();
    }

    private List<String> extractMarkdownImageUrls(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("!\\[[^\\]]*\\]\\(([^)]+)\\)")
                .matcher(content);
        java.util.ArrayList<String> urls = new java.util.ArrayList<>();
        while (matcher.find()) {
            urls.add(matcher.group(1));
        }
        return urls;
    }

    private String toTitle(String userContent) {
        String normalized = normalizeContent(userContent, 0).replaceAll("\\s+", " ");
        if (normalized.isBlank()) {
            return "图片对话";
        }
        return normalized.length() > 30 ? normalized.substring(0, 30) : normalized;
    }

    private String normalizeTitle(String title) {
        String normalized = normalizeContent(title, 0).replaceAll("\\s+", " ");
        if (normalized.isBlank()) {
            return "生日祝福";
        }
        return normalized.length() > 120 ? normalized.substring(0, 120) : normalized;
    }

    private static String encodeCursor(AiConversationSummary summary) {
        String value = summary.updatedAt() + "\n" + summary.id();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static Cursor decodeCursor(String encoded) {
        if (encoded == null || encoded.isBlank()) return null;
        if (encoded.length() > 128 || !encoded.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("对话分页游标无效");
        }
        try {
            String value = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            int separator = value.lastIndexOf('\n');
            if (separator <= 0) throw new IllegalArgumentException("对话分页游标无效");
            LocalDateTime updatedAt = LocalDateTime.parse(value.substring(0, separator));
            long id = Long.parseLong(value.substring(separator + 1));
            if (id <= 0) throw new IllegalArgumentException("对话分页游标无效");
            return new Cursor(updatedAt, id);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("对话分页游标无效");
        }
    }

    private record Cursor(LocalDateTime updatedAt, Long id) { }
}
