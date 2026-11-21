alter table friend_messages
    add column message_type varchar(20) not null default 'TEXT' after receiver_id,
    add column media_url varchar(500) null after content;

alter table friend_messages
    add constraint chk_friend_messages_type
    check (message_type in ('TEXT', 'IMAGE', 'STICKER'));

create table if not exists friend_stickers (
    id bigint not null auto_increment,
    user_id bigint not null,
    image_url varchar(500) not null,
    created_at datetime(6) not null,
    primary key (id),
    key idx_friend_stickers_user_created (user_id, created_at)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;
