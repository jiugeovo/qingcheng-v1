create table if not exists anniversaries (
    id bigint not null auto_increment,
    user_id bigint not null,
    title varchar(100) not null,
    anniversary_date date not null,
    note varchar(1000) not null,
    repeat_yearly boolean not null default true,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id),
    key idx_anniversaries_user_date (user_id, anniversary_date, id),
    constraint fk_anniversaries_user foreign key (user_id) references users (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;
