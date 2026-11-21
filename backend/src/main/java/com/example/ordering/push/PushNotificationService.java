package com.example.ordering.push;

import com.example.ordering.order.OrderNotification;
import com.example.ordering.shop.ShopOrder;
import com.example.ordering.user.UserAccount;
import com.example.ordering.user.UserRepository;
import com.example.ordering.user.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class PushNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationService.class);
    private static final String JPUSH = "JPUSH";

    private final PushDeviceRepository pushDeviceRepository;
    private final UserRepository userRepository;
    private final JpushClient jpushClient;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public PushNotificationService(
            PushDeviceRepository pushDeviceRepository,
            UserRepository userRepository,
            JpushClient jpushClient
    ) {
        this.pushDeviceRepository = pushDeviceRepository;
        this.userRepository = userRepository;
        this.jpushClient = jpushClient;
    }

    @Transactional
    public void registerDevice(UserAccount user, PushDeviceRequest request) {
        String provider = normalizeProvider(request.provider());
        String registrationId = normalizeRegistrationId(request.registrationId());
        PushDeviceEntity device = pushDeviceRepository.findByProviderAndRegistrationId(provider, registrationId)
                .orElseGet(PushDeviceEntity::new);
        LocalDateTime now = LocalDateTime.now();
        device.setUserId(user.getId());
        device.setProvider(provider);
        device.setRegistrationId(registrationId);
        device.setPlatform(normalizePlatform(request.platform()));
        device.setEnabled(true);
        device.setUpdatedAt(now);
        device.setLastSeenAt(now);
        pushDeviceRepository.save(device);
    }

    @Transactional
    public void unregisterDevice(UserAccount user, PushDeviceRequest request) {
        String provider = normalizeProvider(request.provider());
        String registrationId = normalizeRegistrationId(request.registrationId());
        pushDeviceRepository.findByProviderAndRegistrationId(provider, registrationId)
                .filter(device -> user.getId().equals(device.getUserId()))
                .ifPresent(device -> {
                    device.setEnabled(false);
                    device.setUpdatedAt(LocalDateTime.now());
                    pushDeviceRepository.save(device);
                });
    }

    public void notifyMenuOrderCreated(OrderNotification order) {
        if (order.handlerUserId() != null) {
            String label = switch (order.orderKind()) {
                case "PIGGY_SHOP" -> "猪猪商店订单";
                case "MIXED_MENU" -> "点菜和猪猪商店订单";
                default -> "点菜订单";
            };
            notifyHandler(order.handlerUserId(), order.id(), "menu_order", label, order.nickname(), order.username());
            return;
        }
        afterCommit(() -> sendOrderNotification(
                List.of(UserRole.SUPER_ADMIN),
                "新的点菜订单",
                buyerName(order.nickname(), order.username()) + " 提交了点菜订单，金额 " + order.totalAmount() + " 青程币",
                Map.of(
                        "type", "menu_order",
                        "orderId", String.valueOf(order.id())
                )
        ));
    }

    public void notifyShopOrderCreated(ShopOrder order) {
        if (order.handlerUserId() != null) {
            notifyHandler(order.handlerUserId(), order.id(), "shop_order", "宝宝商店订单", order.nickname(), order.username());
            return;
        }
        afterCommit(() -> sendOrderNotification(
                List.of(UserRole.ADMIN),
                "新的宝宝商店订单",
                buyerName(order.nickname(), order.username()) + " 提交了宝宝商店订单，金额 " + order.totalAmount() + " 青程币",
                Map.of(
                        "type", "shop_order",
                        "orderId", String.valueOf(order.id())
                )
        ));
    }

    private void notifyHandler(Long handlerId, Long orderId, String type, String label, String nickname, String username) {
        afterCommit(() -> sendUserNotification(handlerId, "新的" + label,
                buyerName(nickname, username) + " 提交了" + label,
                Map.of("type", type, "orderId", String.valueOf(orderId))));
    }

    public void notifyFriendRequest(UserAccount requester, UserAccount addressee) {
        afterCommit(() -> sendUserNotification(
                addressee.getId(),
                "新的好友申请",
                displayName(requester) + " 想添加你为好友",
                Map.of(
                        "type", "friend_request",
                        "userId", String.valueOf(requester.getId())
                )
        ));
    }

    public void notifyFriendAccepted(UserAccount accepter, UserAccount requester) {
        afterCommit(() -> sendUserNotification(
                requester.getId(),
                "好友申请已通过",
                displayName(accepter) + " 已经同意你的好友申请",
                Map.of(
                        "type", "friend_accept",
                        "userId", String.valueOf(accepter.getId())
                )
        ));
    }

    public void notifyFriendMessage(UserAccount sender, UserAccount receiver, String content) {
        afterCommit(() -> sendUserNotification(
                receiver.getId(),
                displayName(sender),
                summarizeMessage(content),
                Map.of(
                        "type", "friend_message",
                        "userId", String.valueOf(sender.getId())
                )
        ));
    }

    public void notifyAiConversation(UserAccount user, Long conversationId, String title, String content) {
        if (user == null || user.getId() == null || conversationId == null) {
            return;
        }
        afterCommit(() -> sendUserNotification(
                user.getId(),
                StringUtils.hasText(title) ? title.trim() : "布布助手",
                summarizeMessage(content),
                Map.of(
                        "type", "ai_conversation",
                        "conversationId", String.valueOf(conversationId)
                )
        ));
    }

    @Transactional
    public void disableAllDevicesForUser(Long userId) {
        if (userId == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        pushDeviceRepository.findAllByUserIdAndEnabledTrue(userId).forEach(device -> {
            device.setEnabled(false);
            device.setUpdatedAt(now);
            pushDeviceRepository.save(device);
        });
    }

    public PushStatusResponse status() {
        return new PushStatusResponse(JPUSH, jpushClient.isConfigured());
    }

    private void sendOrderNotification(List<UserRole> roles, String title, String text, Map<String, String> extras) {
        try {
            List<Long> userIds = userRepository.findAllByRoleInAndEnabledTrue(roles).stream()
                    .map(UserAccount::getId)
                    .toList();
            if (userIds.isEmpty()) {
                return;
            }
            List<String> registrationIds = pushDeviceRepository
                    .findAllByProviderAndEnabledTrueAndUserIdIn(JPUSH, userIds)
                    .stream()
                    .map(PushDeviceEntity::getRegistrationId)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .toList();
            jpushClient.sendNotification(registrationIds, title, text, extras);
        } catch (Exception error) {
            log.warn("Order push notification skipped: {}", error.getMessage());
        }
    }

    private void sendUserNotification(Long userId, String title, String text, Map<String, String> extras) {
        try {
            List<String> registrationIds = pushDeviceRepository
                    .findAllByUserIdAndEnabledTrue(userId)
                    .stream()
                    .filter(device -> JPUSH.equals(device.getProvider()))
                    .map(PushDeviceEntity::getRegistrationId)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .toList();
            jpushClient.sendNotification(registrationIds, title, text, extras);
        } catch (Exception error) {
            log.warn("User push notification skipped: {}", error.getMessage());
        }
    }

    private void afterCommit(Runnable task) {
        Runnable asyncTask = () -> executor.execute(task);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    asyncTask.run();
                }
            });
        } else {
            asyncTask.run();
        }
    }

    private String normalizeProvider(String provider) {
        if (!StringUtils.hasText(provider)) {
            return JPUSH;
        }
        String normalized = provider.trim().toUpperCase();
        if (!JPUSH.equals(normalized)) {
            throw new IllegalArgumentException("暂不支持该推送通道");
        }
        return normalized;
    }

    private String normalizeRegistrationId(String registrationId) {
        String trimmed = registrationId == null ? "" : registrationId.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("推送设备标识不能为空");
        }
        if (trimmed.length() > 255) {
            throw new IllegalArgumentException("推送设备标识过长");
        }
        return trimmed;
    }

    private String normalizePlatform(String platform) {
        if (!StringUtils.hasText(platform)) {
            return "android";
        }
        String trimmed = platform.trim();
        return trimmed.length() > 30 ? trimmed.substring(0, 30) : trimmed;
    }

    private String buyerName(String nickname, String username) {
        if (StringUtils.hasText(nickname)) {
            return nickname.trim();
        }
        if (StringUtils.hasText(username)) {
            return username.trim();
        }
        return "有人";
    }

    private String displayName(UserAccount user) {
        if (user == null) {
            return "好友";
        }
        return buyerName(user.getNickname(), user.getUsername());
    }

    private String summarizeMessage(String content) {
        String trimmed = content == null ? "" : content.trim().replaceAll("\\s+", " ");
        if (trimmed.isEmpty()) {
            return "发来了一条消息";
        }
        return trimmed.length() > 80 ? trimmed.substring(0, 80) + "..." : trimmed;
    }
}
