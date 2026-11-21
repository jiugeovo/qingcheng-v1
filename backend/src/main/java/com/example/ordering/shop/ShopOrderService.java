package com.example.ordering.shop;

import com.example.ordering.admin.audit.AdminAuditService;
import com.example.ordering.couple.CoupleAccess;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import com.example.ordering.order.OrderAction;
import com.example.ordering.order.OrderStatus;
import com.example.ordering.order.StoreOrderNumbers;
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

@Service
public class ShopOrderService {

    private static final BigDecimal COMPLETION_REWARD_RATE = new BigDecimal("0.60");

    private final ShopItemRepository shopItemRepository;
    private final ShopOrderRepository shopOrderRepository;
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

    public ShopOrderService(
            ShopItemRepository shopItemRepository,
            ShopOrderRepository shopOrderRepository,
            UserRepository userRepository,
            WalletLedgerService walletLedgerService,
            PushNotificationService pushNotificationService,
            AdminAuditService adminAuditService
    ) {
        this(shopItemRepository, shopOrderRepository, userRepository, walletLedgerService,
                pushNotificationService, adminAuditService, null, Clock.system(ZoneId.of("Asia/Shanghai")));
    }

    @Autowired
    public ShopOrderService(
            ShopItemRepository shopItemRepository,
            ShopOrderRepository shopOrderRepository,
            UserRepository userRepository,
            WalletLedgerService walletLedgerService,
            PushNotificationService pushNotificationService,
            AdminAuditService adminAuditService,
            OrderIdempotencyService idempotency,
            Clock clock
    ) {
        this.shopItemRepository = shopItemRepository;
        this.shopOrderRepository = shopOrderRepository;
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
    public ShopOrder createOrder(UserAccount user, CreateShopOrderRequest request) {
        return createOrder(user, request, null);
    }

    @Transactional
    @Retryable(retryFor = {DeadlockLoserDataAccessException.class, CannotAcquireLockException.class,
            SQLTransactionRollbackException.class}, maxAttempts = 3,
            backoff = @Backoff(delay = 50, maxDelay = 500, multiplier = 2.0, random = true))
    public ShopOrder createOrder(UserAccount user, CreateShopOrderRequest request, String idempotencyKey) {
        couple.requireBuyer(user, false);
        if (couple.enabled() && idempotencyKey == null) {
            throw new IllegalArgumentException("需要下单提交编号");
        }
        OrderIdempotencyService.validateKey(idempotencyKey);
        UserAccount buyer = userRepository.findLockedById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("User does not exist"));
        if (idempotencyKey != null) {
            String requestHash = OrderRequestFingerprint.hash(request.items().stream()
                    .map(item -> new OrderRequestFingerprint.Line(item.shopItemId(), item.quantity())).toList(), request.remark());
            ShopOrder result = idempotency.execute(buyer.getId(), OrderIdempotencyService.SHOP_CREATE, idempotencyKey,
                    requestHash, ShopOrder.class, () -> createOrderLocked(buyer, request));
            if (!couple.enabled()) return result;
            return shopOrderRepository.findById(result.id())
                    .filter(order -> Objects.equals(order.getUserId(), buyer.getId()))
                    .map(order -> toDto(order, buyer))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "这次提交已处理，请查看订单历史"));
        }
        return createOrderLocked(buyer, request);
    }

    private ShopOrder createOrderLocked(UserAccount buyer, CreateShopOrderRequest request) {
        List<ShopOrderLineEntity> lines = new ArrayList<>();
        int totalQuantity = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (ShopOrderItemRequest itemRequest : request.items()) {
            ShopItem item = shopItemRepository.findById(itemRequest.shopItemId())
                    .orElseThrow(() -> new IllegalArgumentException("Shop item does not exist"));
            if (item.getPrice() == null) {
                throw new IllegalArgumentException("商品价格未设置");
            }
            BigDecimal unitPrice = item.getPrice();
            BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(itemRequest.quantity()));
            ShopOrderLineEntity line = new ShopOrderLineEntity();
            line.setShopItemId(item.getId());
            line.setName(item.getName());
            line.setQuantity(itemRequest.quantity());
            line.setUnitPrice(unitPrice);
            line.setSubtotal(subtotal);
            lines.add(line);
            totalQuantity += itemRequest.quantity();
            totalAmount = totalAmount.add(subtotal);
        }

        if (buyer.getQingchengCoins().compareTo(totalAmount) < 0) {
            throw new IllegalArgumentException("青程币余额不足");
        }
        buyer.setQingchengCoins(buyer.getQingchengCoins().subtract(totalAmount));

        ShopOrderEntity order = new ShopOrderEntity();
        order.setUserId(buyer.getId());
        order.setHandlerUserId(couple.handler(false));
        order.setUsername(buyer.getUsername());
        order.setNickname(buyer.getNickname());
        order.setItems(lines);
        order.setTotalQuantity(totalQuantity);
        order.setTotalAmount(totalAmount);
        order.setRemark(normalizeRemark(request.remark()));
        order.setStatus(OrderStatus.PENDING);
        LocalDateTime now = LocalDateTime.now(clock);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        if (couple.enabled()) {
            order.setDisplayNumber(shopOrderRepository.nextDisplayNumber());
        }
        UserAccount savedBuyer = userRepository.save(buyer);
        ShopOrderEntity savedOrder = shopOrderRepository.save(order);
        walletLedgerService.record(
                savedBuyer,
                WalletTransactionType.SHOP_ORDER_PAYMENT,
                totalAmount.negate(),
                "宝宝商店消费",
                summarizeLines(lines),
                "shop-order:" + savedOrder.getId()
        );
        ShopOrder result = toDto(savedOrder);
        pushNotificationService.notifyShopOrderCreated(result);
        return result;
    }

    @Transactional(readOnly = true)
    public List<ShopOrder> listOrders() {
        return shopOrderRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ShopOrder> listMyOrders(UserAccount user) {
        return shopOrderRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(order -> toDto(order, user))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ShopOrder> listReceivedOrders(UserAccount user) {
        couple.requireMember(user);
        return shopOrderRepository.findAllByHandlerUserId(user.getId()).stream()
                .map(order -> toDto(order, user))
                .toList();
    }

    @Transactional
    @Retryable(retryFor = {DeadlockLoserDataAccessException.class, CannotAcquireLockException.class,
            SQLTransactionRollbackException.class}, maxAttempts = 3,
            backoff = @Backoff(delay = 50, maxDelay = 500, multiplier = 2.0, random = true))
    public ShopOrder updateStatus(Long orderId, OrderStatus status) {
        return updateStatus(orderId, status, null);
    }

    @Transactional
    @Retryable(retryFor = {DeadlockLoserDataAccessException.class, CannotAcquireLockException.class,
            SQLTransactionRollbackException.class}, maxAttempts = 3,
            backoff = @Backoff(delay = 50, maxDelay = 500, multiplier = 2.0, random = true))
    public ShopOrder updateStatus(Long orderId, OrderStatus status, UserAccount actor) {
        if (couple.enabled()) throw new AccessDeniedException("请使用双人订单入口");
        return changeStatus(orderId, status, actor, null);
    }

    @Transactional
    @Retryable(retryFor = {DeadlockLoserDataAccessException.class, CannotAcquireLockException.class,
            SQLTransactionRollbackException.class}, maxAttempts = 3,
            backoff = @Backoff(delay = 50, maxDelay = 500, multiplier = 2.0, random = true))
    public ShopOrder act(Long orderId, OrderAction action, UserAccount actor) {
        couple.requireMember(actor);
        return changeStatus(orderId, null, actor, Objects.requireNonNull(action));
    }

    private ShopOrder changeStatus(Long orderId, OrderStatus status, UserAccount actor, OrderAction action) {
        lockOrderUsers(orderId, actor);
        ShopOrderEntity order = shopOrderRepository.findLockedById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("\u5b9d\u5b9d\u5546\u5e97\u8ba2\u5355\u4e0d\u5b58\u5728"));
        if (action != null) {
            status = action.transition(actor.getId(), order.getUserId(), order.getHandlerUserId(), order.getStatus());
            if (!couple.orderAssignment(order.getUserId(), order.getHandlerUserId(), false)) {
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
                        "shop-order:" + order.getId(),
                        "完成宝宝商店订单"
                );
            }
        } else if (status == OrderStatus.CANCELLED) {
            refund = refundBuyer(order);
        }
        order.setStatus(status);
        order.setUpdatedAt(LocalDateTime.now(clock));
        ShopOrderEntity saved = shopOrderRepository.save(order);
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
        ShopOrderEntity order = shopOrderRepository.findLockedById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("\u5b9d\u5b9d\u5546\u5e97\u8ba2\u5355\u4e0d\u5b58\u5728"));
        BigDecimal refund = BigDecimal.ZERO;
        if (shouldRefundOnDelete(order.getStatus())) {
            refund = refundBuyer(order);
        }
        adminAuditService.record(
                actor,
                "SHOP_ORDER_DELETE",
                "SHOP_ORDER",
                order.getId(),
                order.getStatus().name(),
                "DELETED",
                refund,
                "删除宝宝商店订单，buyer=" + order.getUsername() + ", refund=" + refund
        );
        shopOrderRepository.delete(order);
    }

    private void lockOrderUsers(Long orderId, UserAccount actor) {
        Long buyerId = shopOrderRepository.findUserIdById(orderId)
                .orElseThrow(() -> couple.enabled()
                        ? new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在")
                        : new IllegalArgumentException("宝宝商店订单不存在"));
        TreeSet<Long> userIds = new TreeSet<>();
        userIds.add(buyerId);
        if (actor != null && actor.getId() != null) userIds.add(actor.getId());
        for (Long userId : userIds) {
            userRepository.findLockedById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User does not exist"));
        }
    }

    private boolean isTerminal(OrderStatus status) {
        return status == OrderStatus.COMPLETED || status == OrderStatus.CANCELLED;
    }

    private boolean shouldRefundOnDelete(OrderStatus status) {
        return status == OrderStatus.PENDING || status == OrderStatus.PREPARING;
    }

    private boolean isBuyer(ShopOrderEntity order, UserAccount actor) {
        return actor != null && actor.getId() != null && actor.getId().equals(order.getUserId());
    }

    private void auditStatusChange(
            UserAccount actor,
            ShopOrderEntity order,
            OrderStatus beforeStatus,
            OrderStatus afterStatus,
            BigDecimal reward,
            BigDecimal refund,
            boolean selfRewardSkipped
    ) {
        BigDecimal amountDelta = refund.compareTo(BigDecimal.ZERO) > 0 ? refund : reward;
        String detail = "宝宝商店订单状态更新，buyer=" + order.getUsername()
                + ", reward=" + reward
                + ", refund=" + refund
                + (selfRewardSkipped ? ", selfRewardSkipped=true" : "");
        adminAuditService.record(
                actor,
                "SHOP_ORDER_STATUS",
                "SHOP_ORDER",
                order.getId(),
                beforeStatus.name(),
                afterStatus.name(),
                amountDelta,
                detail
        );
    }

    private BigDecimal refundBuyer(ShopOrderEntity order) {
        BigDecimal refund = order.getTotalAmount() == null ? BigDecimal.ZERO : order.getTotalAmount();
        if (refund.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        UserAccount buyer = userRepository.findLockedById(order.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User does not exist"));
        buyer.setQingchengCoins(buyer.getQingchengCoins().add(refund));
        UserAccount saved = userRepository.save(buyer);
        walletLedgerService.record(
                saved,
                WalletTransactionType.SHOP_ORDER_REFUND,
                refund,
                "订单取消退款",
                "宝宝商店订单已取消，青程币原路退回",
                "shop-order:" + order.getId()
        );
        return refund;
    }

    private void rewardCompleter(UserAccount completer, BigDecimal reward, String referenceId, String detail) {
        if (reward.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        UserAccount current = userRepository.findLockedById(completer.getId())
                .orElseThrow(() -> new IllegalArgumentException("User does not exist"));
        current.setQingchengCoins(current.getQingchengCoins().add(reward));
        UserAccount saved = userRepository.save(current);
        walletLedgerService.record(
                saved,
                WalletTransactionType.SHOP_ORDER_REWARD,
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

    private ShopOrder toDto(ShopOrderEntity entity) {
        return toDto(entity, null);
    }

    private ShopOrder toDto(ShopOrderEntity entity, UserAccount actor) {
        List<OrderAction> actions = couple.enabled() && actor != null
                ? (couple.orderAssignment(entity.getUserId(), entity.getHandlerUserId(), false)
                    ? OrderAction.allowed(actor.getId(), entity.getUserId(), entity.getHandlerUserId(), entity.getStatus())
                    : List.of())
                : null;
        return new ShopOrder(
                entity.getId(),
                entity.getUserId(),
                entity.getUsername(),
                entity.getNickname(),
                entity.getItems().stream()
                        .map(line -> new ShopOrder.OrderLine(
                                line.getShopItemId(),
                                line.getName(),
                                line.getQuantity(),
                                line.getUnitPrice(),
                                line.getSubtotal()
                        ))
                        .toList(),
                entity.getTotalQuantity(),
                entity.getTotalAmount(),
                entity.getRemark(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getHandlerUserId(),
                couple.enabled() ? "BABY_SHOP" : null,
                actions,
                StoreOrderNumbers.format(entity.getDisplayNumber())
        );
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
            throw new IllegalArgumentException("Remark cannot exceed 300 characters");
        }
        return trimmed;
    }

    private String summarizeLines(List<ShopOrderLineEntity> lines) {
        return lines.stream()
                .map(line -> line.getName() + " x" + line.getQuantity())
                .reduce((left, right) -> left + "、" + right)
                .orElse("宝宝商店订单");
    }
}
