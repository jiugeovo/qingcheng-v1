create table if not exists users (
    id bigint not null auto_increment,
    created_at datetime(6) not null,
    enabled bit(1) not null,
    last_check_in_date date null,
    last_login_at datetime(6) null,
    nickname varchar(64) not null,
    password_hash varchar(255) not null,
    phone varchar(20) null,
    qingcheng_coins decimal(12,2) null,
    role enum('ADMIN','SUPER_ADMIN','USER') not null,
    username varchar(32) not null,
    primary key (id),
    unique key uk_users_username (username),
    unique key uk_users_phone (phone)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

create table if not exists auth_tokens (
    token varchar(64) not null,
    created_at datetime(6) not null,
    expires_at datetime(6) not null,
    user_id bigint not null,
    primary key (token),
    key idx_auth_tokens_expires_at (expires_at)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

create table if not exists menu_items (
    id bigint not null auto_increment,
    available bit(1) not null,
    category varchar(20) null,
    created_at datetime(6) not null,
    description varchar(255) null,
    image_url varchar(600) null,
    name varchar(80) not null,
    price decimal(10,2) not null,
    primary key (id),
    key idx_menu_items_available_created_at (available, created_at)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

create table if not exists order_notifications (
    id bigint not null auto_increment,
    created_at datetime(6) not null,
    nickname varchar(64) not null,
    remark varchar(300) null,
    status enum('CANCELLED','COMPLETED','PENDING','PREPARING') not null,
    total_amount decimal(10,2) not null,
    updated_at datetime(6) not null,
    user_id bigint not null,
    username varchar(32) not null,
    primary key (id),
    key idx_order_notifications_created_at (created_at)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

create table if not exists order_notification_items (
    order_notification_id bigint not null,
    menu_item_id bigint not null,
    name varchar(80) not null,
    price decimal(10,2) not null,
    quantity int not null,
    subtotal decimal(10,2) not null,
    line_index int not null,
    primary key (order_notification_id, line_index)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

create table if not exists shop_items (
    id bigint not null auto_increment,
    category varchar(20) null,
    created_at datetime(6) not null,
    description varchar(255) null,
    image_url varchar(600) null,
    name varchar(80) not null,
    price decimal(12,2) null,
    primary key (id),
    key idx_shop_items_created_at (created_at)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

create table if not exists shop_orders (
    id bigint not null auto_increment,
    created_at datetime(6) not null,
    nickname varchar(64) not null,
    remark varchar(300) null,
    status enum('CANCELLED','COMPLETED','PENDING','PREPARING') not null,
    total_amount decimal(12,2) null,
    total_quantity int not null,
    updated_at datetime(6) not null,
    user_id bigint not null,
    username varchar(32) not null,
    primary key (id),
    key idx_shop_orders_created_at (created_at)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

create table if not exists shop_order_items (
    shop_order_id bigint not null,
    name varchar(80) not null,
    quantity int not null,
    shop_item_id bigint not null,
    subtotal decimal(12,2) null,
    unit_price decimal(12,2) null,
    line_index int not null,
    primary key (shop_order_id, line_index)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

create table if not exists wallet_transactions (
    id bigint not null auto_increment,
    amount decimal(12,2) not null,
    balance_after decimal(12,2) not null,
    created_at datetime(6) not null,
    detail varchar(300) null,
    reference_id varchar(80) null,
    title varchar(120) not null,
    type enum(
        'ADMIN_ADJUSTMENT',
        'CHECK_IN',
        'MENU_ORDER_PAYMENT',
        'MENU_ORDER_REWARD',
        'NICKNAME_CHANGE',
        'SHOP_ORDER_PAYMENT',
        'SHOP_ORDER_REWARD'
    ) not null,
    user_id bigint not null,
    primary key (id),
    key idx_wallet_transactions_user_created (user_id, created_at)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

create table if not exists push_devices (
    id bigint not null auto_increment,
    created_at datetime(6) not null,
    enabled bit(1) not null,
    last_seen_at datetime(6) null,
    platform varchar(30) null,
    provider varchar(20) not null,
    registration_id varchar(255) not null,
    updated_at datetime(6) not null,
    user_id bigint not null,
    primary key (id),
    unique key uk_push_devices_registration_id (registration_id),
    unique key idx_push_devices_provider_registration (provider, registration_id),
    key idx_push_devices_user_enabled (user_id, enabled)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

create table if not exists ai_conversations (
    id bigint not null auto_increment,
    created_at datetime(6) not null,
    model varchar(64) not null,
    previous_response_id varchar(128) null,
    title varchar(120) not null,
    updated_at datetime(6) not null,
    user_id bigint not null,
    username varchar(32) not null,
    primary key (id),
    key idx_ai_conversations_user_updated (user_id, updated_at)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

create table if not exists ai_messages (
    id bigint not null auto_increment,
    content longtext not null,
    conversation_id bigint not null,
    created_at datetime(6) not null,
    image_count int not null,
    image_refs longtext null,
    model varchar(64) not null,
    role varchar(16) not null,
    primary key (id),
    key idx_ai_messages_conversation_created (conversation_id, created_at)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

create table if not exists friendships (
    id bigint not null auto_increment,
    addressee_id bigint not null,
    created_at datetime(6) not null,
    requester_id bigint not null,
    status enum('ACCEPTED','PENDING','REJECTED') not null,
    updated_at datetime(6) not null,
    user_high_id bigint not null,
    user_low_id bigint not null,
    primary key (id),
    unique key idx_friendships_pair (user_low_id, user_high_id),
    key idx_friendships_requester_status (requester_id, status),
    key idx_friendships_addressee_status (addressee_id, status)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

create table if not exists friend_messages (
    id bigint not null auto_increment,
    content longtext not null,
    created_at datetime(6) not null,
    read_at datetime(6) null,
    receiver_id bigint not null,
    sender_id bigint not null,
    user_high_id bigint not null,
    user_low_id bigint not null,
    primary key (id),
    key idx_friend_messages_pair_created (user_low_id, user_high_id, created_at),
    key idx_friend_messages_receiver_read (receiver_id, read_at)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;
