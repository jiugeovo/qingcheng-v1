-- Receipts deliberately have no order foreign key: deleting an order must not allow a replay to charge again.
create table order_idempotency_receipts (
    user_id bigint not null,
    operation varchar(32) character set ascii collate ascii_bin not null,
    idempotency_key varchar(128) character set ascii collate ascii_bin not null,
    request_hash char(64) character set ascii collate ascii_bin not null,
    response_json longtext not null,
    created_at datetime(6) not null,
    expires_at datetime(6) not null,
    primary key (user_id, operation, idempotency_key),
    key idx_order_receipts_expiry (expires_at)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;
