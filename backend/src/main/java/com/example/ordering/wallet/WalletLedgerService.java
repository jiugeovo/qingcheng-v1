package com.example.ordering.wallet;

import com.example.ordering.user.UserAccount;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class WalletLedgerService {

    private final WalletTransactionRepository transactionRepository;

    public WalletLedgerService(WalletTransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public void record(
            UserAccount user,
            WalletTransactionType type,
            BigDecimal amount,
            String title,
            String detail,
            String referenceId
    ) {
        WalletTransactionEntity transaction = new WalletTransactionEntity();
        transaction.setUserId(user.getId());
        transaction.setType(type);
        transaction.setAmount(amount);
        transaction.setBalanceAfter(user.getQingchengCoins());
        transaction.setTitle(title);
        transaction.setDetail(detail);
        transaction.setReferenceId(referenceId);
        transactionRepository.save(transaction);
    }
}
