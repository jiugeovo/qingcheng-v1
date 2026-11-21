package com.example.ordering.common;

import com.example.ordering.admin.audit.AdminAuditLogEntity;
import com.example.ordering.ai.AiConversationEntity;
import com.example.ordering.ai.AiMessageEntity;
import com.example.ordering.auth.AuthTokenEntity;
import com.example.ordering.friend.FriendMessageEntity;
import com.example.ordering.friend.FriendMessageType;
import com.example.ordering.friend.FriendStickerEntity;
import com.example.ordering.friend.FriendshipEntity;
import com.example.ordering.friend.FriendshipStatus;
import com.example.ordering.menu.MenuItem;
import com.example.ordering.order.OrderNotificationEntity;
import com.example.ordering.order.OrderStatus;
import com.example.ordering.push.PushDeviceEntity;
import com.example.ordering.rules.LoveRuleEntity;
import com.example.ordering.shop.ShopItem;
import com.example.ordering.shop.ShopOrderEntity;
import com.example.ordering.user.UserAccount;
import com.example.ordering.user.UserRole;
import com.example.ordering.wallet.WalletTransactionEntity;
import com.example.ordering.wallet.WalletTransactionType;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class JdbcEntityMappers {

    private JdbcEntityMappers() {
    }

    public static final RowMapper<UserAccount> USER = (rs, rowNum) -> {
        UserAccount user = new UserAccount();
        user.setId(rs.getLong("id"));
        user.setUsername(rs.getString("username"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setNickname(rs.getString("nickname"));
        user.setPhone(rs.getString("phone"));
        user.setRole(UserRole.valueOf(rs.getString("role")));
        user.setEnabled(rs.getBoolean("enabled"));
        user.setQingchengCoins(rs.getBigDecimal("qingcheng_coins"));
        user.setLastCheckInDate(localDate(rs, "last_check_in_date"));
        user.setCreatedAt(localDateTime(rs, "created_at"));
        user.setLastLoginAt(localDateTime(rs, "last_login_at"));
        return user;
    };

    public static final RowMapper<MenuItem> MENU_ITEM = (rs, rowNum) -> {
        MenuItem item = new MenuItem();
        item.setId(rs.getLong("id"));
        item.setName(rs.getString("name"));
        item.setDescription(rs.getString("description"));
        item.setImageUrl(rs.getString("image_url"));
        item.setCategory(rs.getString("category"));
        item.setPrice(rs.getBigDecimal("price"));
        item.setAvailable(rs.getBoolean("available"));
        item.setCreatedAt(localDateTime(rs, "created_at"));
        return item;
    };

    public static final RowMapper<ShopItem> SHOP_ITEM = (rs, rowNum) -> {
        ShopItem item = new ShopItem();
        item.setId(rs.getLong("id"));
        item.setName(rs.getString("name"));
        item.setDescription(rs.getString("description"));
        item.setImageUrl(rs.getString("image_url"));
        item.setCategory(rs.getString("category"));
        item.setPrice(rs.getBigDecimal("price"));
        item.setCreatedAt(localDateTime(rs, "created_at"));
        return item;
    };

    public static final RowMapper<OrderNotificationEntity> MENU_ORDER = (rs, rowNum) -> {
        OrderNotificationEntity order = new OrderNotificationEntity();
        order.setId(rs.getLong("id"));
        order.setUserId(rs.getLong("user_id"));
        order.setHandlerUserId(rs.getObject("handler_user_id", Long.class));
        order.setDisplayNumber(rs.getObject("display_number", Long.class));
        order.setUsername(rs.getString("username"));
        order.setNickname(rs.getString("nickname"));
        order.setTotalAmount(rs.getBigDecimal("total_amount"));
        order.setRemark(rs.getString("remark"));
        order.setStatus(OrderStatus.valueOf(rs.getString("status")));
        order.setCreatedAt(localDateTime(rs, "created_at"));
        order.setUpdatedAt(localDateTime(rs, "updated_at"));
        return order;
    };

    public static final RowMapper<ShopOrderEntity> SHOP_ORDER = (rs, rowNum) -> {
        ShopOrderEntity order = new ShopOrderEntity();
        order.setId(rs.getLong("id"));
        order.setUserId(rs.getLong("user_id"));
        order.setHandlerUserId(rs.getObject("handler_user_id", Long.class));
        order.setDisplayNumber(rs.getObject("display_number", Long.class));
        order.setUsername(rs.getString("username"));
        order.setNickname(rs.getString("nickname"));
        order.setTotalQuantity(rs.getInt("total_quantity"));
        order.setTotalAmount(rs.getBigDecimal("total_amount"));
        order.setRemark(rs.getString("remark"));
        order.setStatus(OrderStatus.valueOf(rs.getString("status")));
        order.setCreatedAt(localDateTime(rs, "created_at"));
        order.setUpdatedAt(localDateTime(rs, "updated_at"));
        return order;
    };

    public static final RowMapper<AuthTokenEntity> AUTH_TOKEN = (rs, rowNum) -> {
        AuthTokenEntity token = new AuthTokenEntity();
        token.setToken(rs.getString("token"));
        token.setTokenHash(rs.getString("token_hash"));
        token.setUserId(rs.getLong("user_id"));
        token.setExpiresAt(instant(rs, "expires_at"));
        token.setCreatedAt(instant(rs, "created_at"));
        return token;
    };

    public static final RowMapper<WalletTransactionEntity> WALLET_TRANSACTION = (rs, rowNum) -> {
        WalletTransactionEntity transaction = new WalletTransactionEntity();
        transaction.setId(rs.getLong("id"));
        transaction.setUserId(rs.getLong("user_id"));
        transaction.setType(WalletTransactionType.valueOf(rs.getString("type")));
        transaction.setAmount(rs.getBigDecimal("amount"));
        transaction.setBalanceAfter(rs.getBigDecimal("balance_after"));
        transaction.setTitle(rs.getString("title"));
        transaction.setDetail(rs.getString("detail"));
        transaction.setReferenceId(rs.getString("reference_id"));
        transaction.setCreatedAt(localDateTime(rs, "created_at"));
        return transaction;
    };

    public static final RowMapper<PushDeviceEntity> PUSH_DEVICE = (rs, rowNum) -> {
        PushDeviceEntity device = new PushDeviceEntity();
        device.setId(rs.getLong("id"));
        device.setUserId(rs.getLong("user_id"));
        device.setProvider(rs.getString("provider"));
        device.setRegistrationId(rs.getString("registration_id"));
        device.setPlatform(rs.getString("platform"));
        device.setEnabled(rs.getBoolean("enabled"));
        device.setCreatedAt(localDateTime(rs, "created_at"));
        device.setUpdatedAt(localDateTime(rs, "updated_at"));
        device.setLastSeenAt(localDateTime(rs, "last_seen_at"));
        return device;
    };

    public static final RowMapper<AiConversationEntity> AI_CONVERSATION = (rs, rowNum) -> {
        AiConversationEntity conversation = new AiConversationEntity();
        conversation.setId(rs.getLong("id"));
        conversation.setUserId(rs.getLong("user_id"));
        conversation.setUsername(rs.getString("username"));
        conversation.setTitle(rs.getString("title"));
        conversation.setModel(rs.getString("model"));
        conversation.setPreviousResponseId(rs.getString("previous_response_id"));
        conversation.setCreatedAt(localDateTime(rs, "created_at"));
        conversation.setUpdatedAt(localDateTime(rs, "updated_at"));
        return conversation;
    };

    public static final RowMapper<AiMessageEntity> AI_MESSAGE = (rs, rowNum) -> {
        AiMessageEntity message = new AiMessageEntity();
        message.setId(rs.getLong("id"));
        message.setConversationId(rs.getLong("conversation_id"));
        message.setRole(rs.getString("role"));
        message.setContent(rs.getString("content"));
        message.setModel(rs.getString("model"));
        message.setImageCount(rs.getInt("image_count"));
        message.setImageRefs(rs.getString("image_refs"));
        message.setCreatedAt(localDateTime(rs, "created_at"));
        return message;
    };

    public static final RowMapper<FriendshipEntity> FRIENDSHIP = (rs, rowNum) -> {
        FriendshipEntity friendship = new FriendshipEntity();
        friendship.setId(rs.getLong("id"));
        friendship.setUserLowId(rs.getLong("user_low_id"));
        friendship.setUserHighId(rs.getLong("user_high_id"));
        friendship.setRequesterId(rs.getLong("requester_id"));
        friendship.setAddresseeId(rs.getLong("addressee_id"));
        friendship.setStatus(FriendshipStatus.valueOf(rs.getString("status")));
        friendship.setCreatedAt(localDateTime(rs, "created_at"));
        friendship.setUpdatedAt(localDateTime(rs, "updated_at"));
        friendship.setRequesterClearedMessagesAt(localDateTime(rs, "requester_cleared_messages_at"));
        friendship.setAddresseeClearedMessagesAt(localDateTime(rs, "addressee_cleared_messages_at"));
        return friendship;
    };

    public static final RowMapper<FriendMessageEntity> FRIEND_MESSAGE = (rs, rowNum) -> {
        FriendMessageEntity message = new FriendMessageEntity();
        message.setId(rs.getLong("id"));
        message.setUserLowId(rs.getLong("user_low_id"));
        message.setUserHighId(rs.getLong("user_high_id"));
        message.setSenderId(rs.getLong("sender_id"));
        message.setReceiverId(rs.getLong("receiver_id"));
        message.setMessageType(FriendMessageType.valueOf(rs.getString("message_type")));
        message.setContent(rs.getString("content"));
        message.setMediaUrl(rs.getString("media_url"));
        message.setReplyToMessageId(nullableLong(rs, "reply_to_message_id"));
        message.setReplyToSenderId(nullableLong(rs, "reply_to_sender_id"));
        String replyType = rs.getString("reply_to_type");
        message.setReplyToType(replyType == null ? null : FriendMessageType.valueOf(replyType));
        message.setReplyToPreview(rs.getString("reply_to_preview"));
        message.setCreatedAt(localDateTime(rs, "created_at"));
        message.setReadAt(localDateTime(rs, "read_at"));
        return message;
    };

    public static final RowMapper<FriendStickerEntity> FRIEND_STICKER = (rs, rowNum) -> {
        FriendStickerEntity sticker = new FriendStickerEntity();
        sticker.setId(rs.getLong("id"));
        sticker.setUserId(rs.getLong("user_id"));
        sticker.setImageUrl(rs.getString("image_url"));
        sticker.setCreatedAt(localDateTime(rs, "created_at"));
        return sticker;
    };

    public static final RowMapper<LoveRuleEntity> LOVE_RULE = (rs, rowNum) -> {
        LoveRuleEntity rule = new LoveRuleEntity();
        rule.setId(rs.getLong("id"));
        rule.setContent(rs.getString("content"));
        rule.setSortOrder(rs.getInt("sort_order"));
        rule.setCreatedAt(localDateTime(rs, "created_at"));
        rule.setUpdatedAt(localDateTime(rs, "updated_at"));
        return rule;
    };

    public static final RowMapper<AdminAuditLogEntity> ADMIN_AUDIT_LOG = (rs, rowNum) -> {
        AdminAuditLogEntity log = new AdminAuditLogEntity();
        log.setId(rs.getLong("id"));
        log.setActorUserId(nullableLong(rs, "actor_user_id"));
        log.setActorUsername(rs.getString("actor_username"));
        log.setAction(rs.getString("action"));
        log.setTargetType(rs.getString("target_type"));
        log.setTargetId(rs.getString("target_id"));
        log.setBeforeValue(rs.getString("before_value"));
        log.setAfterValue(rs.getString("after_value"));
        log.setAmountDelta(rs.getBigDecimal("amount_delta"));
        log.setDetail(rs.getString("detail"));
        log.setCreatedAt(localDateTime(rs, "created_at"));
        return log;
    };

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static LocalDate localDate(ResultSet rs, String column) throws SQLException {
        var value = rs.getDate(column);
        return value == null ? null : value.toLocalDate();
    }

    private static LocalDateTime localDateTime(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

}
