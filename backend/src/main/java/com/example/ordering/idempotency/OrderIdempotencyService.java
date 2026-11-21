package com.example.ordering.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.Objects;

@Service
public class OrderIdempotencyService {
    public static final String MENU_CREATE = "menu-order.create";
    public static final String SHOP_CREATE = "shop-order.create";
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9._:-]{1,128}");
    private final OrderIdempotencyRepository repository;
    private final ObjectMapper mapper;
    private final Clock clock;

    public OrderIdempotencyService(OrderIdempotencyRepository repository, ObjectMapper mapper, Clock clock) {
        this.repository = repository;
        this.mapper = mapper;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public static void validateKey(String key) {
        if (key != null && !KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Idempotency-Key 必须为 1 至 128 位字母、数字或 . _ : -");
        }
    }

    /** Caller holds the authenticated user's row lock in this same transaction before entering. */
    @Transactional(propagation = Propagation.MANDATORY)
    public <T> T execute(long userId, String operation, String key, String requestHash,
                         Class<T> responseType, Supplier<T> action) {
        validateKey(key);
        if (key == null) return action.get();
        LocalDateTime now = LocalDateTime.now(clock);
        var receipt = repository.findLocked(userId, operation, key);
        if (receipt.isPresent()) {
            var previous = receipt.get();
            if (previous.expiresAt().isAfter(now)) {
                if (!previous.requestHash().equals(requestHash)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "该提交标识已用于不同订单，请重新确认订单");
                }
                return deserialize(previous.responseJson(), responseType);
            }
            repository.delete(userId, operation, key);
        }
        T result = action.get();
        repository.save(userId, operation, key, requestHash, serialize(result), now, now.plusDays(30));
        return result;
    }

    /** Bounded cleanup avoids a long transaction or a full scan on each request. */
    @Scheduled(initialDelayString = "${app.orders.receipt-cleanup-initial-delay-ms:60000}",
            fixedDelayString = "${app.orders.receipt-cleanup-delay-ms:60000}")
    @Transactional
    public void removeExpiredReceipts() {
        repository.deleteExpiredBatch(LocalDateTime.now(clock));
    }

    private String serialize(Object response) {
        try {
            return mapper.writeValueAsString(response);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not persist order response", ex);
        }
    }

    private <T> T deserialize(String json, Class<T> responseType) {
        try {
            return mapper.readValue(json, responseType);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not restore order response", ex);
        }
    }
}
