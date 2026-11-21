package com.example.ordering.friend;

import com.example.ordering.push.PushNotificationService;
import com.example.ordering.user.UserAccount;
import com.example.ordering.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FriendService {

    private static final int MAX_MESSAGE_LIMIT = 200;
    private static final int MAX_STICKERS_PER_USER = 100;

    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;
    private final FriendMessageRepository messageRepository;
    private final FriendStickerRepository stickerRepository;
    private final FriendMediaStorage mediaStorage;
    private final PushNotificationService pushNotificationService;

    public FriendService(
            UserRepository userRepository,
            FriendshipRepository friendshipRepository,
            FriendMessageRepository messageRepository,
            FriendStickerRepository stickerRepository,
            FriendMediaStorage mediaStorage,
            PushNotificationService pushNotificationService
    ) {
        this.userRepository = userRepository;
        this.friendshipRepository = friendshipRepository;
        this.messageRepository = messageRepository;
        this.stickerRepository = stickerRepository;
        this.mediaStorage = mediaStorage;
        this.pushNotificationService = pushNotificationService;
    }

    @Transactional(readOnly = true)
    public FriendCenterResponse center(UserAccount user) {
        List<FriendshipEntity> accepted = friendshipRepository.findAcceptedForUser(user.getId());
        Map<Long, UserAccount> users = usersById(accepted.stream()
                .map(friendship -> otherUserId(friendship, user.getId()))
                .toList());
        List<FriendResponse> friends = accepted.stream()
                .map(friendship -> toFriendResponse(user.getId(), friendship, users.get(otherUserId(friendship, user.getId()))))
                .filter(Objects::nonNull)
                .toList();
        List<FriendRequestResponse> incoming = friendshipRepository
                .findAllByAddresseeIdAndStatusOrderByCreatedAtDesc(user.getId(), FriendshipStatus.PENDING)
                .stream()
                .map(friendship -> toRequestResponse(user.getId(), friendship, "incoming"))
                .toList();
        List<FriendRequestResponse> outgoing = friendshipRepository
                .findAllByRequesterIdAndStatusOrderByCreatedAtDesc(user.getId(), FriendshipStatus.PENDING)
                .stream()
                .map(friendship -> toRequestResponse(user.getId(), friendship, "outgoing"))
                .toList();
        return new FriendCenterResponse(
                friends,
                incoming,
                outgoing,
                friends.stream().mapToLong(FriendResponse::unreadCount).sum()
        );
    }

    @Transactional(readOnly = true)
    public List<FriendUserResponse> searchUsers(UserAccount user, String query) {
        String normalized = query == null ? "" : query.trim();
        if (normalized.length() < 2) {
            throw new IllegalArgumentException("至少输入 2 个字再搜索");
        }
        return userRepository.searchEnabledUsers(user.getId(), normalized, null).stream()
                .map(FriendUserResponse::from)
                .toList();
    }

    @Transactional
    public FriendRequestResponse requestFriend(UserAccount user, Long targetUserId) {
        if (targetUserId == null) {
            throw new IllegalArgumentException("请选择要添加的好友");
        }
        if (user.getId().equals(targetUserId)) {
            throw new IllegalArgumentException("不能添加自己为好友");
        }
        UserAccount target = userRepository.findById(targetUserId)
                .filter(UserAccount::isEnabled)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        Long low = lowId(user.getId(), targetUserId);
        Long high = highId(user.getId(), targetUserId);
        FriendshipEntity friendship = friendshipRepository.findLockedByUserLowIdAndUserHighId(low, high)
                .orElseGet(FriendshipEntity::new);
        LocalDateTime now = LocalDateTime.now();
        if (friendship.getId() != null && friendship.getStatus() == FriendshipStatus.ACCEPTED) {
            throw new IllegalArgumentException("你们已经是好友了");
        }
        if (friendship.getId() != null && friendship.getStatus() == FriendshipStatus.PENDING) {
            if (user.getId().equals(friendship.getRequesterId())) {
                throw new IllegalArgumentException("好友申请已发送，等待对方确认");
            }
            friendship.setStatus(FriendshipStatus.ACCEPTED);
            friendship.setUpdatedAt(now);
            FriendshipEntity saved = friendshipRepository.save(friendship);
            pushNotificationService.notifyFriendAccepted(user, target);
            return toRequestResponse(user.getId(), saved, "accepted");
        }
        friendship.setUserLowId(low);
        friendship.setUserHighId(high);
        friendship.setRequesterId(user.getId());
        friendship.setAddresseeId(targetUserId);
        friendship.setStatus(FriendshipStatus.PENDING);
        if (friendship.getCreatedAt() == null) {
            friendship.setCreatedAt(now);
        }
        friendship.setUpdatedAt(now);
        FriendshipEntity saved = friendshipRepository.save(friendship);
        pushNotificationService.notifyFriendRequest(user, target);
        return toRequestResponse(user.getId(), saved, "outgoing");
    }

    @Transactional
    public FriendRequestResponse accept(UserAccount user, Long friendshipId) {
        FriendshipEntity friendship = findIncomingRequest(user, friendshipId);
        friendship.setStatus(FriendshipStatus.ACCEPTED);
        friendship.setUpdatedAt(LocalDateTime.now());
        FriendshipEntity saved = friendshipRepository.save(friendship);
        userRepository.findById(saved.getRequesterId())
                .ifPresent(requester -> pushNotificationService.notifyFriendAccepted(user, requester));
        return toRequestResponse(user.getId(), saved, "incoming");
    }

    @Transactional
    public FriendRequestResponse reject(UserAccount user, Long friendshipId) {
        FriendshipEntity friendship = findIncomingRequest(user, friendshipId);
        friendship.setStatus(FriendshipStatus.REJECTED);
        friendship.setUpdatedAt(LocalDateTime.now());
        return toRequestResponse(user.getId(), friendshipRepository.save(friendship), "incoming");
    }

    @Transactional(readOnly = true)
    public List<FriendMessageResponse> messages(UserAccount user, Long friendUserId, int limit) {
        FriendshipEntity friendship = findAcceptedFriendship(user.getId(), friendUserId);
        Long low = lowId(user.getId(), friendUserId);
        Long high = highId(user.getId(), friendUserId);
        int size = Math.min(Math.max(limit, 1), MAX_MESSAGE_LIMIT);
        LocalDateTime clearedAt = clearedAtFor(user.getId(), friendship);
        List<FriendMessageEntity> newest = clearedAt == null
                ? messageRepository.findAllByUserLowIdAndUserHighId(low, high, size)
                : messageRepository.findAllByUserLowIdAndUserHighIdAndCreatedAtAfter(low, high, clearedAt, size);
        return newest.stream()
                .sorted(Comparator.comparing(FriendMessageEntity::getCreatedAt).thenComparing(FriendMessageEntity::getId))
                .map(message -> toMessageResponse(user.getId(), message))
                .toList();
    }

    @Transactional
    public FriendMessageResponse sendMessage(UserAccount user, Long friendUserId, FriendMessageRequest request) {
        UserAccount friend = ensureAcceptedUser(user.getId(), friendUserId);
        String content = request.content() == null ? "" : request.content().trim();
        if (!StringUtils.hasText(content)) {
            throw new IllegalArgumentException("消息不能为空");
        }
        Long low = lowId(user.getId(), friendUserId);
        Long high = highId(user.getId(), friendUserId);
        FriendMessageEntity message = new FriendMessageEntity();
        message.setUserLowId(low);
        message.setUserHighId(high);
        message.setSenderId(user.getId());
        message.setReceiverId(friendUserId);
        message.setMessageType(FriendMessageType.TEXT);
        message.setContent(content);
        applyReply(message, request.replyToMessageId(), low, high);
        message.setCreatedAt(LocalDateTime.now());
        FriendMessageEntity saved = messageRepository.save(message);
        pushNotificationService.notifyFriendMessage(user, friend, content);
        return toMessageResponse(user.getId(), saved);
    }

    @Transactional
    public FriendMessageResponse sendImage(
            UserAccount user,
            Long friendUserId,
            MultipartFile file
    ) throws IOException {
        UserAccount friend = ensureAcceptedUser(user.getId(), friendUserId);
        FriendMediaStorage.StoredImage image = mediaStorage.store(file, user.getId(), "images");
        try {
            return saveMediaMessage(user, friend, FriendMessageType.IMAGE, image.url(), "[图片]");
        } catch (RuntimeException error) {
            mediaStorage.deleteQuietly(image);
            throw error;
        }
    }

    @Transactional(readOnly = true)
    public List<FriendStickerResponse> stickers(UserAccount user) {
        return stickerRepository.findAllByUserId(user.getId()).stream()
                .map(FriendStickerResponse::from)
                .toList();
    }

    @Transactional
    public FriendStickerResponse addSticker(UserAccount user, MultipartFile file) throws IOException {
        if (stickerRepository.countByUserId(user.getId()) >= MAX_STICKERS_PER_USER) {
            throw new IllegalArgumentException("自定义表情最多保存 100 个");
        }
        FriendMediaStorage.StoredImage image = mediaStorage.store(file, user.getId(), "stickers");
        try {
            FriendStickerEntity sticker = new FriendStickerEntity();
            sticker.setUserId(user.getId());
            sticker.setImageUrl(image.url());
            sticker.setCreatedAt(LocalDateTime.now());
            return FriendStickerResponse.from(stickerRepository.save(sticker));
        } catch (RuntimeException error) {
            mediaStorage.deleteQuietly(image);
            throw error;
        }
    }

    @Transactional
    public FriendMessageResponse sendSticker(UserAccount user, Long friendUserId, Long stickerId) {
        UserAccount friend = ensureAcceptedUser(user.getId(), friendUserId);
        FriendStickerEntity sticker = stickerRepository.findByIdAndUserId(stickerId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("自定义表情不存在"));
        return saveMediaMessage(user, friend, FriendMessageType.STICKER, sticker.getImageUrl(), "[表情]");
    }

    @Transactional(readOnly = true)
    public FriendMediaContent messageMedia(UserAccount user, Long messageId) throws IOException {
        FriendMessageEntity message = messageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("图片不存在"));
        boolean participant = user.getId().equals(message.getSenderId())
                || user.getId().equals(message.getReceiverId());
        if (!participant
                || message.getMessageType() == FriendMessageType.TEXT
                || !StringUtils.hasText(message.getMediaUrl())) {
            throw new IllegalArgumentException("图片不存在");
        }
        return mediaStorage.read(message.getMediaUrl());
    }

    @Transactional(readOnly = true)
    public FriendMediaContent stickerMedia(UserAccount user, Long stickerId) throws IOException {
        FriendStickerEntity sticker = stickerRepository.findByIdAndUserId(stickerId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("图片不存在"));
        return mediaStorage.read(sticker.getImageUrl());
    }

    @Transactional
    public void markRead(UserAccount user, Long friendUserId) {
        findAcceptedFriendship(user.getId(), friendUserId);
        messageRepository.markConversationRead(user.getId(), friendUserId, LocalDateTime.now());
    }

    @Transactional
    public void clearMessages(UserAccount user, Long friendUserId) {
        FriendshipEntity friendship = findLockedAcceptedFriendship(user.getId(), friendUserId);
        LocalDateTime now = LocalDateTime.now();
        if (user.getId().equals(friendship.getRequesterId())) {
            friendship.setRequesterClearedMessagesAt(now);
        } else {
            friendship.setAddresseeClearedMessagesAt(now);
        }
        friendship.setUpdatedAt(now);
        friendshipRepository.save(friendship);
        messageRepository.markConversationRead(user.getId(), friendUserId, now);
    }

    private FriendshipEntity findIncomingRequest(UserAccount user, Long friendshipId) {
        FriendshipEntity friendship = friendshipRepository.findLockedById(friendshipId)
                .orElseThrow(() -> new IllegalArgumentException("好友申请不存在"));
        if (!user.getId().equals(friendship.getAddresseeId()) || friendship.getStatus() != FriendshipStatus.PENDING) {
            throw new IllegalArgumentException("好友申请不存在或已处理");
        }
        return friendship;
    }

    private FriendshipEntity findAcceptedFriendship(Long currentUserId, Long friendUserId) {
        if (friendUserId == null || currentUserId.equals(friendUserId)) {
            throw new IllegalArgumentException("好友不存在");
        }
        Long low = lowId(currentUserId, friendUserId);
        Long high = highId(currentUserId, friendUserId);
        return friendshipRepository.findByUserLowIdAndUserHighId(low, high)
                .filter(friendship -> friendship.getStatus() == FriendshipStatus.ACCEPTED)
                .orElseThrow(() -> new IllegalArgumentException("你们还不是好友"));
    }

    private FriendshipEntity findLockedAcceptedFriendship(Long currentUserId, Long friendUserId) {
        if (friendUserId == null || currentUserId.equals(friendUserId)) {
            throw new IllegalArgumentException("好友不存在");
        }
        Long low = lowId(currentUserId, friendUserId);
        Long high = highId(currentUserId, friendUserId);
        return friendshipRepository.findLockedByUserLowIdAndUserHighId(low, high)
                .filter(friendship -> friendship.getStatus() == FriendshipStatus.ACCEPTED)
                .orElseThrow(() -> new IllegalArgumentException("你们还不是好友"));
    }

    private UserAccount ensureAcceptedUser(Long currentUserId, Long friendUserId) {
        findAcceptedFriendship(currentUserId, friendUserId);
        return userRepository.findById(friendUserId)
                .filter(UserAccount::isEnabled)
                .orElseThrow(() -> new IllegalArgumentException("好友不存在"));
    }

    private FriendResponse toFriendResponse(Long currentUserId, FriendshipEntity friendship, UserAccount friend) {
        if (friend == null) {
            return null;
        }
        Long low = lowId(currentUserId, friend.getId());
        Long high = highId(currentUserId, friend.getId());
        LocalDateTime clearedAt = clearedAtFor(currentUserId, friendship);
        FriendMessageEntity last = lastVisibleMessage(low, high, clearedAt);
        return new FriendResponse(
                friendship.getId(),
                FriendUserResponse.from(friend),
                visibleUnreadCount(currentUserId, friend.getId(), clearedAt),
                last == null ? "" : last.getContent(),
                last == null ? null : last.getCreatedAt(),
                friendship.getUpdatedAt()
        );
    }

    private FriendRequestResponse toRequestResponse(Long currentUserId, FriendshipEntity friendship, String direction) {
        Long userId = otherUserId(friendship, currentUserId);
        UserAccount user = userRepository.findById(userId).orElse(null);
        return new FriendRequestResponse(
                friendship.getId(),
                user == null ? null : FriendUserResponse.from(user),
                direction,
                friendship.getStatus(),
                friendship.getCreatedAt(),
                friendship.getUpdatedAt()
        );
    }

    private FriendMessageResponse toMessageResponse(Long currentUserId, FriendMessageEntity message) {
        String mediaUrl = message.getMessageType() == FriendMessageType.TEXT
                ? null : "/api/friends/media/messages/" + message.getId() + "/content";
        return new FriendMessageResponse(
                message.getId(),
                message.getSenderId(),
                message.getReceiverId(),
                message.getContent(),
                message.getMessageType(),
                mediaUrl,
                message.getReplyToMessageId(),
                message.getReplyToSenderId(),
                message.getReplyToType(),
                message.getReplyToPreview(),
                message.getCreatedAt(),
                message.getReadAt(),
                currentUserId.equals(message.getSenderId())
        );
    }

    private FriendMessageResponse saveMediaMessage(
            UserAccount sender,
            UserAccount receiver,
            FriendMessageType type,
            String mediaUrl,
            String preview
    ) {
        FriendMessageEntity message = new FriendMessageEntity();
        message.setUserLowId(lowId(sender.getId(), receiver.getId()));
        message.setUserHighId(highId(sender.getId(), receiver.getId()));
        message.setSenderId(sender.getId());
        message.setReceiverId(receiver.getId());
        message.setMessageType(type);
        message.setContent(preview);
        message.setMediaUrl(mediaUrl);
        message.setCreatedAt(LocalDateTime.now());
        FriendMessageEntity saved = messageRepository.save(message);
        pushNotificationService.notifyFriendMessage(sender, receiver, preview);
        return toMessageResponse(sender.getId(), saved);
    }

    private void applyReply(
            FriendMessageEntity message,
            Long replyToMessageId,
            Long low,
            Long high
    ) {
        if (replyToMessageId == null) {
            return;
        }
        FriendMessageEntity replied = messageRepository.findById(replyToMessageId)
                .filter(candidate -> low.equals(candidate.getUserLowId())
                        && high.equals(candidate.getUserHighId()))
                .orElseThrow(() -> new IllegalArgumentException("引用的消息不存在"));
        message.setReplyToMessageId(replied.getId());
        message.setReplyToSenderId(replied.getSenderId());
        message.setReplyToType(replied.getMessageType());
        message.setReplyToPreview(replyPreview(replied));
    }

    private String replyPreview(FriendMessageEntity message) {
        if (message.getMessageType() == FriendMessageType.IMAGE) {
            return "[图片]";
        }
        if (message.getMessageType() == FriendMessageType.STICKER) {
            return "[表情]";
        }
        String content = message.getContent() == null ? "" : message.getContent().trim();
        return content.length() <= 120 ? content : content.substring(0, 120) + "…";
    }

    private Map<Long, UserAccount> usersById(List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(UserAccount::getId, Function.identity()));
    }

    private Long otherUserId(FriendshipEntity friendship, Long userId) {
        return userId.equals(friendship.getUserLowId()) ? friendship.getUserHighId() : friendship.getUserLowId();
    }

    private Long lowId(Long left, Long right) {
        return Math.min(left, right);
    }

    private Long highId(Long left, Long right) {
        return Math.max(left, right);
    }

    private LocalDateTime clearedAtFor(Long currentUserId, FriendshipEntity friendship) {
        return currentUserId.equals(friendship.getRequesterId())
                ? friendship.getRequesterClearedMessagesAt()
                : friendship.getAddresseeClearedMessagesAt();
    }

    private FriendMessageEntity lastVisibleMessage(Long low, Long high, LocalDateTime clearedAt) {
        return clearedAt == null
                ? messageRepository.findTopByUserLowIdAndUserHighIdOrderByCreatedAtDesc(low, high)
                : messageRepository.findTopByUserLowIdAndUserHighIdAndCreatedAtAfterOrderByCreatedAtDesc(low, high, clearedAt);
    }

    private long visibleUnreadCount(Long currentUserId, Long friendUserId, LocalDateTime clearedAt) {
        return clearedAt == null
                ? messageRepository.countByReceiverIdAndSenderIdAndReadAtIsNull(currentUserId, friendUserId)
                : messageRepository.countByReceiverIdAndSenderIdAndReadAtIsNullAndCreatedAtAfter(currentUserId, friendUserId, clearedAt);
    }
}
