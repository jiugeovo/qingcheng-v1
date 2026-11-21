package com.example.ordering.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(0)
@ConditionalOnProperty(name = "app.legacy-schema-migration.enabled", havingValue = "true")
public class DatabaseMigrationRunner implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;
    private String databaseName;

    public DatabaseMigrationRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        databaseName = resolveDatabaseName();
        addColumnIfMissing("users", "qingcheng_coins", "DECIMAL(12,2) DEFAULT 0");
        addColumnIfMissing("users", "last_check_in_date", "DATE");
        addColumnIfMissing("menu_items", "category", "VARCHAR(20) DEFAULT '炒菜'");
        addColumnIfMissing("shop_items", "category", "VARCHAR(20) DEFAULT '宝宝商店'");
        addColumnIfMissing("shop_items", "price", "DECIMAL(12,2)");
        addColumnIfMissing("shop_orders", "total_amount", "DECIMAL(12,2) DEFAULT 0");
        addColumnIfMissing("shop_order_items", "unit_price", "DECIMAL(12,2) DEFAULT 0");
        addColumnIfMissing("shop_order_items", "subtotal", "DECIMAL(12,2) DEFAULT 0");
        addWalletTransactionsTableIfMissing();
        addPushDevicesTableIfMissing();
        addFriendshipTablesIfMissing();
        addColumnIfMissing("friend_messages", "reply_to_message_id", "BIGINT NULL");
        addColumnIfMissing("friend_messages", "reply_to_sender_id", "BIGINT NULL");
        addColumnIfMissing("friend_messages", "reply_to_type", "VARCHAR(20) NULL");
        addColumnIfMissing("friend_messages", "reply_to_preview", "VARCHAR(300) NULL");
        addIndexIfMissing("friend_messages", "idx_friend_messages_reply", "reply_to_message_id", false);
        normalizeLongTextColumns();
        updateNulls("users", "qingcheng_coins", "0");
        updateBlankStrings("menu_items", "category", "炒菜");
        updateBlankStrings("shop_items", "category", "宝宝商店");
        updateNulls("shop_orders", "total_amount", "0");
        updateNulls("shop_order_items", "unit_price", "0");
        updateNulls("shop_order_items", "subtotal", "0");
    }

    private void addColumnIfMissing(String table, String column, String definition) {
        try {
            jdbcTemplate.execute("alter table " + table + " add column " + column + " " + definition);
        } catch (DataAccessException error) {
            String message = error.getMostSpecificCause().getMessage();
            if (message == null || !isDuplicateColumnMessage(message)) {
                throw error;
            }
        }
    }

    private void addWalletTransactionsTableIfMissing() {
        jdbcTemplate.execute("""
                create table if not exists wallet_transactions (
                    id %s primary key,
                    user_id bigint not null,
                    type varchar(40) not null,
                    amount decimal(12,2) not null,
                    balance_after decimal(12,2) not null,
                    title varchar(120) not null,
                    detail varchar(300),
                    reference_id varchar(80),
                    created_at timestamp not null default current_timestamp
                )
                """.formatted(identityColumn()));
        addIndexIfMissing("wallet_transactions", "idx_wallet_transactions_user_created", "user_id, created_at", false);
    }

    private void addPushDevicesTableIfMissing() {
        jdbcTemplate.execute("""
                create table if not exists push_devices (
                    id %s primary key,
                    user_id bigint not null,
                    provider varchar(20) not null,
                    registration_id varchar(255) not null,
                    platform varchar(30),
                    enabled boolean not null default true,
                    created_at timestamp not null default current_timestamp,
                    updated_at timestamp not null default current_timestamp,
                    last_seen_at timestamp default current_timestamp
                )
                """.formatted(identityColumn()));
        addIndexIfMissing("push_devices", "idx_push_devices_provider_registration", "provider, registration_id", true);
        addIndexIfMissing("push_devices", "idx_push_devices_user_enabled", "user_id, enabled", false);
    }

    private void addFriendshipTablesIfMissing() {
        jdbcTemplate.execute("""
                create table if not exists friendships (
                    id %s primary key,
                    user_low_id bigint not null,
                    user_high_id bigint not null,
                    requester_id bigint not null,
                    addressee_id bigint not null,
                    status varchar(20) not null,
                    created_at timestamp not null default current_timestamp,
                    updated_at timestamp not null default current_timestamp
                )
                """.formatted(identityColumn()));
        addColumnIfMissing("friendships", "requester_cleared_messages_at", "TIMESTAMP NULL");
        addColumnIfMissing("friendships", "addressee_cleared_messages_at", "TIMESTAMP NULL");
        addIndexIfMissing("friendships", "idx_friendships_pair", "user_low_id, user_high_id", true);
        addIndexIfMissing("friendships", "idx_friendships_requester_status", "requester_id, status", false);
        addIndexIfMissing("friendships", "idx_friendships_addressee_status", "addressee_id, status", false);

        jdbcTemplate.execute("""
                create table if not exists friend_messages (
                    id %s primary key,
                    user_low_id bigint not null,
                    user_high_id bigint not null,
                    sender_id bigint not null,
                    receiver_id bigint not null,
                    content %s not null,
                    created_at timestamp not null default current_timestamp,
                    read_at timestamp null
                )
                """.formatted(identityColumn(), textColumn()));
        addIndexIfMissing("friend_messages", "idx_friend_messages_pair_created", "user_low_id, user_high_id, created_at", false);
        addIndexIfMissing("friend_messages", "idx_friend_messages_receiver_read", "receiver_id, read_at", false);
    }

    private void normalizeLongTextColumns() {
        if (!isMysql()) {
            return;
        }
        alterColumnIfPresent("ai_messages", "content", "LONGTEXT NOT NULL");
        alterColumnIfPresent("ai_messages", "image_refs", "LONGTEXT");
        alterColumnIfPresent("friend_messages", "content", "LONGTEXT NOT NULL");
    }

    private void alterColumnIfPresent(String table, String column, String definition) {
        try {
            jdbcTemplate.execute("alter table " + table + " modify " + column + " " + definition);
        } catch (DataAccessException error) {
            String message = error.getMostSpecificCause().getMessage();
            if (message == null || !isMissingTableOrColumnMessage(message)) {
                throw error;
            }
        }
    }

    private void addIndexIfMissing(String table, String indexName, String columns, boolean unique) {
        try {
            String uniqueness = unique ? "unique " : "";
            if (isMysql()) {
                jdbcTemplate.execute("create " + uniqueness + "index " + indexName + " on " + table + "(" + columns + ")");
            } else {
                jdbcTemplate.execute("create " + uniqueness + "index if not exists " + indexName + " on " + table + "(" + columns + ")");
            }
        } catch (DataAccessException error) {
            String message = error.getMostSpecificCause().getMessage();
            if (message == null || !isDuplicateIndexMessage(message)) {
                throw error;
            }
        }
    }

    private void updateNulls(String table, String column, String value) {
        try {
            jdbcTemplate.update("update " + table + " set " + column + " = " + value + " where " + column + " is null");
        } catch (DataAccessException error) {
            String message = error.getMostSpecificCause().getMessage();
            if (message == null || !message.toLowerCase().contains("column")) {
                throw error;
            }
        }
    }

    private void updateBlankStrings(String table, String column, String value) {
        try {
            jdbcTemplate.update(
                    "update " + table + " set " + column + " = ? where " + column + " is null or trim(" + column + ") = ''",
                    value
            );
        } catch (DataAccessException error) {
            String message = error.getMostSpecificCause().getMessage();
            if (message == null || !message.toLowerCase().contains("column")) {
                throw error;
            }
        }
    }

    private String resolveDatabaseName() {
        return jdbcTemplate.execute((ConnectionCallback<String>) connection -> connection.getMetaData().getDatabaseProductName());
    }

    private boolean isMysql() {
        return databaseName != null && databaseName.toLowerCase().contains("mysql");
    }

    private String identityColumn() {
        return isMysql() ? "bigint not null auto_increment" : "bigint generated by default as identity";
    }

    private String textColumn() {
        return isMysql() ? "LONGTEXT" : "CLOB";
    }

    private boolean isDuplicateColumnMessage(String message) {
        String normalized = message.toLowerCase();
        return normalized.contains("duplicate column")
                || normalized.contains("already exists")
                || normalized.contains("duplicate column name");
    }

    private boolean isDuplicateIndexMessage(String message) {
        String normalized = message.toLowerCase();
        return normalized.contains("exists")
                || normalized.contains("duplicate")
                || normalized.contains("already");
    }

    private boolean isMissingTableOrColumnMessage(String message) {
        String normalized = message.toLowerCase();
        return normalized.contains("doesn't exist")
                || normalized.contains("unknown column")
                || normalized.contains("table not found")
                || normalized.contains("column not found");
    }
}
