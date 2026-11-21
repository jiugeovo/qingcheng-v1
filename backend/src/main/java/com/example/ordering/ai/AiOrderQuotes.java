package com.example.ordering.ai;

import com.example.ordering.order.CreateOrderRequest;
import com.example.ordering.order.OrderItemRequest;
import com.example.ordering.user.UserAccount;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Short-lived, account-bound quotes. The model never receives these tokens. */
@Service
public class AiOrderQuotes {
    public record Line(long menuItemId, String name, BigDecimal price, int quantity) {
        public BigDecimal subtotal() { return price.multiply(BigDecimal.valueOf(quantity)); }
    }
    public record Quote(long userId, String requestId, long expiresAt, List<Line> items, String remark) {
        public CreateOrderRequest request() {
            return new CreateOrderRequest(items.stream()
                    .map(line -> new OrderItemRequest(line.menuItemId(), line.quantity())).toList(), remark);
        }
        public BigDecimal total() { return items.stream().map(Line::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add); }
        public Map<Long, BigDecimal> prices() {
            return items.stream().collect(Collectors.toUnmodifiableMap(Line::menuItemId, Line::price));
        }
    }

    private final ObjectMapper mapper;
    private final Clock clock;
    private final byte[] secret;

    @Autowired
    public AiOrderQuotes(ObjectMapper mapper, Clock clock) {
        this(mapper, clock, new SecureRandom().generateSeed(32));
    }

    AiOrderQuotes(ObjectMapper mapper, Clock clock, byte[] secret) {
        this.mapper = mapper;
        this.clock = clock;
        this.secret = secret.clone();
    }

    Quote create(UserAccount user, List<Line> items, String remark) {
        if (items == null || items.isEmpty() || items.size() > 50)
            throw new IllegalArgumentException("请选择 1 到 50 道菜");
        return new Quote(user.getId(), "ai-order-" + UUID.randomUUID(),
                Instant.now(clock).plusSeconds(15 * 60).getEpochSecond(), List.copyOf(items), remark);
    }

    String sign(Quote quote) {
        try {
            byte[] payload = mapper.writeValueAsBytes(quote);
            return encode(payload) + "." + encode(mac(payload));
        } catch (java.io.IOException error) {
            throw new IllegalStateException("订单草稿生成失败", error);
        }
    }

    Quote verify(UserAccount user, String token) {
        try {
            if (token == null || token.length() > 24000) throw new IllegalArgumentException();
            String[] parts = token.split("\\.", -1);
            if (parts.length != 2) throw new IllegalArgumentException();
            byte[] payload = Base64.getUrlDecoder().decode(parts[0]);
            if (!MessageDigest.isEqual(mac(payload), Base64.getUrlDecoder().decode(parts[1])))
                throw new IllegalArgumentException();
            Quote quote = mapper.readValue(payload, Quote.class);
            if (quote.userId() != user.getId())
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "不能确认其他账号的订单草稿");
            if (quote.expiresAt() <= Instant.now(clock).getEpochSecond())
                throw new IllegalArgumentException("订单草稿已过期，请重新生成；如已确认请先查看订单");
            return quote;
        } catch (ResponseStatusException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalArgumentException("订单草稿无效或已过期，请重新生成；如已确认请先查看订单");
        }
    }

    private byte[] mac(byte[] payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(payload);
        } catch (java.security.GeneralSecurityException error) {
            throw new IllegalStateException(error);
        }
    }

    private String encode(byte[] bytes) { return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
}
