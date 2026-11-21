package com.example.ordering.wallet;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record WalletTransaction(
        Long id,
        WalletTransactionType type,
        BigDecimal amount,
        BigDecimal balanceAfter,
        String title,
        String detail,
        String referenceId,
        LocalDateTime createdAt
) {

    public static WalletTransaction from(WalletTransactionEntity entity) {
        return new WalletTransaction(
                entity.getId(),
                entity.getType(),
                entity.getAmount(),
                entity.getBalanceAfter(),
                entity.getTitle(),
                entity.getDetail(),
                entity.getReferenceId(),
                entity.getCreatedAt()
        );
    }
}
