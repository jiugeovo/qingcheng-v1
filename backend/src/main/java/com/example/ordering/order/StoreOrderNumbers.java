package com.example.ordering.order;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Locale;

/** Allocates a display number in the same transaction as the order and its wallet payment. */
public final class StoreOrderNumbers {
    public enum Store { PIGGY_SHOP, BABY_SHOP }

    private StoreOrderNumbers() { }

    public static long next(JdbcTemplate jdbc, Store store) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("A store order number requires an order transaction");
        }
        Long last = jdbc.queryForObject("""
                SELECT last_number FROM store_order_sequences WHERE store_kind = ? FOR UPDATE
                """, Long.class, store.name());
        long next = Math.incrementExact(last);
        jdbc.update("""
                UPDATE store_order_sequences SET last_number = ? WHERE store_kind = ?
                """, next, store.name());
        return next;
    }

    public static String format(Long number) {
        return number == null ? null : String.format(Locale.ROOT, "%03d", number);
    }
}
