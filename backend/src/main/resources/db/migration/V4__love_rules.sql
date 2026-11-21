create table if not exists love_rules (
    id bigint not null auto_increment,
    content varchar(500) not null,
    sort_order int not null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id),
    key idx_love_rules_sort_order (sort_order)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;
