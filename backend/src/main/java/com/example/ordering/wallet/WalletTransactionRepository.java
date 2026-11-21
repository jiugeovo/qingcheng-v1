package com.example.ordering.wallet;

import com.example.ordering.common.JdbcEntityMappers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

@Repository
public class WalletTransactionRepository {

    private final JdbcTemplate jdbc;

    public WalletTransactionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public WalletTransactionEntity save(WalletTransactionEntity transaction) {
        if (transaction.getId() == null) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement ps = connection.prepareStatement("""
                                insert into wallet_transactions
                                (user_id, type, amount, balance_after, title, detail, reference_id, created_at)
                                values (?, ?, ?, ?, ?, ?, ?, ?)
                                """,
                        Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, transaction.getUserId());
                ps.setString(2, transaction.getType().name());
                ps.setBigDecimal(3, transaction.getAmount());
                ps.setBigDecimal(4, transaction.getBalanceAfter());
                ps.setString(5, transaction.getTitle());
                ps.setString(6, transaction.getDetail());
                ps.setString(7, transaction.getReferenceId());
                ps.setObject(8, transaction.getCreatedAt());
                return ps;
            }, keyHolder);
            transaction.setId(keyHolder.getKey().longValue());
        }
        return transaction;
    }

    public List<WalletTransactionEntity> findTop80ByUserIdOrderByCreatedAtDesc(Long userId) {
        return jdbc.query("""
                        select * from wallet_transactions
                        where user_id = ?
                        order by created_at desc
                        limit 80
                        """,
                JdbcEntityMappers.WALLET_TRANSACTION,
                userId);
    }

    public List<WalletTransactionEntity> findTop120ByUserIdAndTypeOrderByCreatedAtDesc(Long userId, WalletTransactionType type) {
        return jdbc.query("""
                        select * from wallet_transactions
                        where user_id = ? and type = ?
                        order by created_at desc
                        limit 120
                        """,
                JdbcEntityMappers.WALLET_TRANSACTION,
                userId,
                type.name());
    }
}
