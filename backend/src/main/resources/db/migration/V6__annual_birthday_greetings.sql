create table if not exists birthday_greeting_deliveries (
    id bigint not null auto_increment,
    user_id bigint not null,
    greeting_year int not null,
    status varchar(20) not null,
    greeting_content longtext not null,
    conversation_id bigint null,
    model varchar(64) null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id),
    unique key uk_birthday_greeting_user_year (user_id, greeting_year),
    key idx_birthday_greeting_status_updated (status, updated_at)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;
