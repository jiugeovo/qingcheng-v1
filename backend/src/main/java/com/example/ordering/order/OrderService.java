package com.example.ordering.order;

import com.example.ordering.admin.audit.AdminAuditService;
import com.example.ordering.couple.CoupleAccess;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import com.example.ordering.menu.MenuItem;
import com.example.ordering.menu.MenuItemRepository;
import com.example.ordering.push.PushNotificationService;
import com.example.ordering.user.UserAccount;
import com.example.ordering.user.UserRepository;
import com.example.ordering.idempotency.OrderIdempotencyService;
import com.example.ordering.idempotency.OrderRequestFingerprint;
import com.example.ordering.wallet.WalletLedgerService;
import com.example.ordering.wallet.WalletTransactionType;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import java.sql.SQLTransactionRollbackException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.Clock;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.Objects;
import java.util.Map;

@Service
public class OrderService {

    private static final BigDecimal COMPLETION_REWARD_RATE = new BigDecimal("0.80");

    private final MenuItemRepository menuItemRepository;
    private final OrderNotificationRepository orderNotificationRepository;
    private final UserRepository userRepository;
    private final WalletLedgerService walletLedgerService;
    private final PushNotificationService pushNotificationService;
    private final AdminAuditService adminAuditService;
    private final OrderIdempotencyService idempotency;
    private final Clock clock;
    private CoupleAccess couple = CoupleAccess.legacy();

    @Autowired(required = false)
    void configureCouple(CoupleAccess couple) {
        this.couple = couple;
    }

    public OrderService(
            MenuItemRepository menuItemRepository,
            OrderNotificationRepository orderNotificationRepository,
            UserRepository userRepository,
            WalletLedgerService walletLedgerService,
            PushNotificationService pushNotificationService,
            AdminAuditService adminAuditService
    ) {
        this(menuItemRepository, orderNotificationRepository, userRepository, walletLedgerService,
                pushNotificationService, adminAuditService, null, Clock.system(ZoneId.of("Asia/Shanghai")));
    }

    @Autowired
    public OrderService(
            MenuItemRepository menuItemRepository,
            OrderNotificationRepository orderNotificationRepository,
            UserRepository userRepository,
            WalletLedgerService walletLedgerService,
            PushNotificationService pushNotificationService,
            AdminAuditService adminAuditService,
            OrderIdempotencyService idempotency,
            Clock clock
    ) {
        this.menuItemRepository = menuItemRepository;
        this.orderNotificationRepository = orderNotificationRepository;
        this.userRepository = userRepository;
        this.walletLedgerService = walletLedgerService;
        this.pushNotificationService = pushNotificationService;
        this.adminAuditService = adminAuditService;
        this.idempotency = idempotency;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional
    @Retryable(retryFor = {DeadlockLoserDataAccessException.class, CannotAcquireLockException.class,
            SQLTransactionRollbackException.class}, maxAttempts = 3,
            backoff = @Backoff(delay = 50, maxDelay = 500, multiplier = 2.0, random = true))
    public OrderNotification createOrder(UserAccount user, CreateOrderRequest request) {
        return createOrder(user, request, null);
    }

    @Transactional
    @Retryable(retryFor = {DeadlockLoserDataAccessException.class, CannotAcquireLockException.class,
            SQLTransactionRollbackException.class}, maxAttempts = 3,
            backoff = @Backoff(delay = 50, maxDelay = 500, multiplier = 2.0, random = true))
    public OrderNotification createOrder(UserAccount user, CreateOrderRequest request, String idempotencyKey) {
        return createWithQuote(user, request, idempotencyKey, Map.of());
    }

    @Transactional
    @Retryable(retryFor = {DeadlockLoserDataAccessException.class, CannotAcquireLockException.class,
            SQLTransactionRollbackException.class}, maxAttempts = 3,
            backoff = @Backoff(delay = 50, maxDelay = 500, multiplier = 2.0, random = true))
    public OrderNotification createConfirmedOrder(UserAccount user, CreateOrderRequest request,
                                                  String idempotencyKey, Map<Long, BigDecimal> quotedPrices) {
        if (idempotencyKey == null || quotedPrices == null || quotedPrices.isEmpty())
            throw new IllegalArgumentException("请先生成订单草稿");
        return createWithQuote(user, request, idempotencyKey, Map.copyOf(quotedPrices));
    }

    private OrderNotification createWithQuote(UserAccount user, CreateOrderRequest request,
                                               String idempotencyKey, Map<Long, BigDecimal> quotedPrices) {
        couple.requireBuyer(user, true);
        if (couple.enabled() && idempotencyKey == null) {
            throw new IllegalArgumentException("需要下单提交编号");
        }
        OrderIdempotencyService.validateKey(idempotencyKey);
        UserAccount buyer = userRepository.findLockedById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        if (idempotencyKey != null) {
            String requestHash = OrderRequestFingerprint.hash(request.items().stream()
                    .map(item -> new OrderRequestFingerprint.Line(item.menuItemId(), item.quantity())).toList(), request.remark());
            OrderNotification result = idempotency.execute(buyer.getId(), OrderIdempotencyService.MENU_CREATE, idempotencyKey,
                    requestHash, OrderNotification.class, () -> createOrderLocked(buyer, request, quotedPrices));
            if (!couple.enabled()) return result;
            return orderNotificationRepository.findById(result.id())
                    .filter(order -> Objects.equals(order.getUserId(), buyer.getId()))
                    .map(order -> toDto(order, buyer))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "这次提交已处理，请查看订单历史"));
        }
        return createOrderLocked(buyer, request, quotedPrices);
    }

    private OrderNotification createOrderLocked(UserAccount buyer, CreateOrderRequest request,
                                                 Map<Long, BigDecimal> quotedPrices) {
        List<OrderNotificationLineEntity> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (OrderItemRequest itemRequest : request.items()) {
            MenuItem item = menuItemRepository.findById(itemRequest.menuItemId())
                    .filter(MenuItem::isAvailable)
                    .orElseThrow(() -> new IllegalArgumentException("菜品不存在或已下架"));
            if (!quotedPrices.isEmpty() && (quotedPrices.get(item.getId()) == null
                    || quotedPrices.get(item.getId()).compareTo(item.getPrice()) != 0)) {
                throw new IllegalArgumentException("菜品价格已变化，请重新生成订单草稿");
            }
            BigDecimal subtotal = item.getPrice().multiply(BigDecimal.valueOf(itemRequest.quantity()));
            total = total.add(subtotal);

            OrderNotificationLineEntity line = new OrderNotificationLineEntity();
            line.setMenuItemId(item.getId());
            line.setName(item.getName());
            if (couple.enabled()) line.setItemKind("猪猪商店".equals(item.getCategory()) ? "PIGGY_SHOP" : "MENU");
            line.setPrice(item.getPrice());
            line.setQuantity(itemRequest.quantity());
            line.setSubtotal(subtotal);
            lines.add(line);
        }

        if (buyer.getQingchengCoins().compareTo(total) < 0) {
            throw new IllegalArgumentException("青程币余额不足");
        }
        buyer.setQingchengCoins(buyer.getQingchengCoins().subtract(total));

        OrderNotificationEntity notification = new OrderNotificationEntity();
        notification.setUserId(buyer.getId());
        notification.setHandlerUserId(couple.handler(true));
        notification.setUsername(buyer.getUsername());
        notification.setNickname(buyer.getNickname());
        notification.setItems(lines);
        notification.setTotalAmount(total);
        notification.setRemark(normalizeRemark(request.remark()));
        notification.setStatus(OrderStatus.PENDING);
        LocalDateTime now = LocalDateTime.now(clock);
        notification.setCreatedAt(now);
        notification.setUpdatedAt(now);
        if (couple.enabled() && "PIGGY_SHOP".equals(orderKind(notification))) {
            notification.setDisplayNumber(orderNotificationRepository.nextDisplayNumber());
        }
        UserAccount savedBuyer = userRepository.save(buyer);
        OrderNotificationEntity savedOrder = orderNotificationRepository.save(notification);
        walletLedgerService.record(
                savedBuyer,
                WalletTransactionType.MENU_ORDER_PAYMENT,
                total.negate(),
                "点菜消费",
                summarizeLines(lines),
                "menu-order:" + savedOrder.getId()
        );
        OrderNotification result = toDto(savedOrder);
        pushNotificationService.notifyMenuOrderCreated(result);
        return result;
    }

    @Transactional(readOnly = true)
    public List<OrderNotification> listNotifications() {
        return orderNotificationRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OrderNotification> listMyOrders(UserAccount user) {
        return orderNotificationRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(order -> toDto(order, user))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OrderNotification> listReceivedOrders(UserAccount user) {
        couple.requireMember(user);
        return orderNotificationRepository.findAllByHandlerUserId(user.getId()).stream()
                .map(order -> toDto(order, user))
                .toList();
    }

    @Transactional
    @Retryable(retryFor = {DeadlockLoserDataAccessException.class, CannotAcquireLockException.class,
            SQLTransactionRollbackException.class}, maxAttempts = 3,
            backoff = @Backoff(delay = 50, maxDelay = 500, multiplier = 2.0, random = true))
    public OrderNotification updateStatus(Long orderId, OrderStatus status) {
        return updateStatus(orderId, status, null);
    }

    @Transactional
    @Retryable(retryFor = {DeadlockLoserDataAccessException.class, CannotAcquireLockException.class,
            SQLTransactionRollbackException.class}, maxAttempts = 3,
            backoff = @Backoff(delay = 50, maxDelay = 500, multiplier = 2.0, random = true))
    public OrderNotification updateStatus(Long orderId, OrderStatus status, UserAccount actor) {
        if (couple.enabled()) throw new AccessDeniedException("请使用双人订单入口");
        return changeStatus(orderId, status, actor, null);
    }

    @Transactional
    @Retryable(retryFor = {DeadlockLoserDataAccessException.class, CannotAcquireLockException.class,
            SQLTransactionRollbackException.class}, maxAttempts = 3,
            backoff = @Backoff(delay = 50, maxDelay = 500, multiplier = 2.0, random = true))
    public OrderNotification act(Long orderId, OrderAction action, UserAccount actor) {
        couple.requireMember(actor);
        return changeStatus(orderId, null, actor, Objects.requireNonNull(action));
    }

    private OrderNotification changeStatus(Long orderId, OrderStatus status, UserAccount actor, OrderAction action) {
        lockOrderUsers(orderId, actor);
        OrderNotificationEntity order = orderNotificationRepository.findLockedById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("\u8ba2\u5355\u4e0d\u5b58\u5728"));
        if (action != null) {
            status = action.transition(actor.getId(), order.getUserId(), order.getHandlerUserId(), order.getStatus());
            if (!couple.orderAssignment(order.getUserId(), order.getHandlerUserId(), true)) {
                throw new AccessDeniedException("这笔旧订单只可查看");
            }
        } else if (isTerminal(order.getStatus())) {
            throw new IllegalArgumentException("\u8ba2\u5355\u5df2\u7ecf\u7ed3\u675f\uff0c\u4e0d\u80fd\u518d\u4fee\u6539\u72b6\u6001");
        }
        OrderStatus beforeStatus = order.getStatus();
        BigDecimal reward = BigDecimal.ZERO;
        BigDecimal refund = BigDecimal.ZERO;
        boolean selfRewardSkipped = false;
        if (status == OrderStatus.COMPLETED && actor != null) {
            reward = calculateReward(order.getTotalAmount(), COMPLETION_REWARD_RATE);
            if (isBuyer(order, actor)) {
                selfRewardSkipped = reward.compareTo(BigDecimal.ZERO) > 0;
                reward = BigDecimal.ZERO;
            } else {
                rewardCompleter(
                        actor,
                        reward,
                        "menu-order:" + order.getId(),
                        "完成点菜订单"
                );
            }
        } else if (status == OrderStatus.CANCELLED) {
            refund = refundBuyer(order);
        }
        order.setStatus(status);
        order.setUpdatedAt(LocalDateTime.now(clock));
        OrderNotificationEntity saved = orderNotificationRepository.save(order);
        auditStatusChange(actor, saved, beforeStatus, status, reward, refund, selfRewardSkipped);
        return toDto(saved, actor);
    }

    @Transactional
    @Retryable(retryFor = {DeadlockLoserDataAccessException.class, CannotAcquireLockException.class,
            SQLTransactionRollbackException.class}, maxAttempts = 3,
            backoff = @Backoff(delay = 50, maxDelay = 500, multiplier = 2.0, random = true))
    public void deleteOrder(Long orderId) {
        deleteOrder(orderId, null);
    }

    @Transactional
    @Retryable(retryFor = {DeadlockLoserDataAccessException.class, CannotAcquireLockException.class,
            SQLTransactionRollbackException.class}, maxAttempts = 3,
            backoff = @Backoff(delay = 50, maxDelay = 500, multiplier = 2.0, random = true))
    public void deleteOrder(Long orderId, UserAccount actor) {
        if (couple.enabled()) throw new AccessDeniedException("订单记录需保留");
        lockOrderUsers(orderId, actor);
        OrderNotificationEntity order = orderNotificationRepository.findLockedById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("\u8ba2\u5355\u4e0d\u5b58\u5728"));
        BigDecimal refund = BigDecimal.ZERO;
        if (shouldRefundOnDelete(order.getStatus())) {
            refund = refundBuyer(order);
        }
        adminAuditService.record(
                actor,
                "MENU_ORDER_DELETE",
                "MENU_ORDER",
                order.getId(),
                order.getStatus().name(),
                "DELETED",
                refund,
                "删除点菜订单，buyer=" + order.getUsername() + ", refund=" + refund
        );
        orderNotificationRepository.delete(order);
    }

    private void lockOrderUsers(Long orderId, UserAccount actor) {
        Long buyerId = orderNotificationRepository.findUserIdById(orderId)
                .orElseThrow(() -> couple.enabled()
                        ? new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在")
                        : new IllegalArgumentException("订单不存在"));
        TreeSet<Long> userIds = new TreeSet<>();
        userIds.add(buyerId);
        if (actor != null && actor.getId() != null) userIds.add(actor.getId());
        for (Long userId : userIds) {
            userRepository.findLockedById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        }
    }

    private boolean isTerminal(OrderStatus status) {
        return status == OrderStatus.COMPLETED || status == OrderStatus.CANCELLED;
    }

    private boolean shouldRefundOnDelete(OrderStatus status) {
        return status == OrderStatus.PENDING || status == OrderStatus.PREPARING;
    }

    private boolean isBuyer(OrderNotificationEntity order, UserAccount actor) {
        return actor != null && actor.getId() != null && actor.getId().equals(order.getUserId());
    }

    private void auditStatusChange(
            UserAccount actor,
            OrderNotificationEntity order,
            OrderStatus beforeStatus,
            OrderStatus afterStatus,
            BigDecimal reward,
            BigDecimal refund,
            boolean selfRewardSkipped
    ) {
        BigDecimal amountDelta = refund.compareTo(BigDecimal.ZERO) > 0 ? refund : reward;
        String detail = "点菜订单状态更新，buyer=" + order.getUsername()
                + ", reward=" + reward
                + ", refund=" + refund
                + (selfRewardSkipped ? ", selfRewardSkipped=true" : "");
        adminAuditService.record(
                actor,
                "MENU_ORDER_STATUS",
                "MENU_ORDER",
                order.getId(),
                beforeStatus.name(),
                afterStatus.name(),
                amountDelta,
                detail
        );
    }

    private BigDecimal refundBuyer(OrderNotificationEntity order) {
        BigDecimal refund = order.getTotalAmount() == null ? BigDecimal.ZERO : order.getTotalAmount();
        if (refund.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        UserAccount buyer = userRepository.findLockedById(order.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        buyer.setQingchengCoins(buyer.getQingchengCoins().add(refund));
        UserAccount saved = userRepository.save(buyer);
        walletLedgerService.record(
                saved,
                WalletTransactionType.MENU_ORDER_REFUND,
                refund,
                "订单取消退款",
                "点菜订单已取消，青程币原路退回",
                "menu-order:" + order.getId()
        );
        return refund;
    }

    private void rewardCompleter(UserAccount completer, BigDecimal reward, String referenceId, String detail) {
        if (reward.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        UserAccount current = userRepository.findLockedById(completer.getId())
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        current.setQingchengCoins(current.getQingchengCoins().add(reward));
        UserAccount saved = userRepository.save(current);
        walletLedgerService.record(
                saved,
                WalletTransactionType.MENU_ORDER_REWARD,
                reward,
                "订单完成奖励",
                detail,
                referenceId
        );
    }

    private BigDecimal calculateReward(BigDecimal totalAmount, BigDecimal rewardRate) {
        if (totalAmount == null) {
            return BigDecimal.ZERO;
        }
        return totalAmount.multiply(rewardRate).setScale(2, RoundingMode.HALF_UP);
    }

    private OrderNotification toDto(OrderNotificationEntity entity) {
        return toDto(entity, null);
    }

    private OrderNotification toDto(OrderNotificationEntity entity, UserAccount actor) {
        List<OrderAction> actions = couple.enabled() && actor != null
                ? (couple.orderAssignment(entity.getUserId(), entity.getHandlerUserId(), true)
                    ? OrderAction.allowed(actor.getId(), entity.getUserId(), entity.getHandlerUserId(), entity.getStatus())
                    : List.of())
                : null;
        return new OrderNotification(
                entity.getId(),
                entity.getUserId(),
                entity.getUsername(),
                entity.getNickname(),
                entity.getItems().stream()
                        .map(line -> new OrderNotification.OrderLine(
                                line.getMenuItemId(),
                                line.getName(),
                                line.getPrice(),
                                line.getQuantity(),
                                line.getSubtotal()
                        ))
                        .toList(),
                entity.getTotalAmount(),
                entity.getRemark(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getHandlerUserId(),
                couple.enabled() ? orderKind(entity) : null,
                actions,
                StoreOrderNumbers.format(entity.getDisplayNumber())
        );
    }

    private String orderKind(OrderNotificationEntity order) {
        List<String> kinds = order.getItems().stream().map(OrderNotificationLineEntity::getItemKind).distinct().toList();
        if (kinds.isEmpty() || kinds.contains(null)) return "LEGACY_MENU";
        return kinds.size() == 1 ? kinds.get(0) : "MIXED_MENU";
    }

    private String normalizeRemark(String remark) {
        if (remark == null) {
            return null;
        }
        String trimmed = remark.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > 300) {
            throw new IllegalArgumentException("备注不能超过300个字符");
        }
        return trimmed;
    }

    private String summarizeLines(List<OrderNotificationLineEntity> lines) {
        return lines.stream()
                .map(line -> line.getName() + " x" + line.getQuantity())
                .reduce((left, right) -> left + "、" + right)
                .orElse("点菜订单");
    }
}
