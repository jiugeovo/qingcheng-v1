create table if not exists admin_audit_logs (
    id bigint not null auto_increment,
    actor_user_id bigint null,
    actor_username varchar(32) null,
    action varchar(64) not null,
    target_type varchar(64) not null,
    target_id varchar(80) not null,
    before_value varchar(120) null,
    after_value varchar(120) null,
    amount_delta decimal(12,2) null,
    detail varchar(600) null,
    created_at datetime(6) not null,
    primary key (id),
    key idx_admin_audit_logs_actor_created (actor_user_id, created_at),
    key idx_admin_audit_logs_target_created (target_type, target_id, created_at)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;
