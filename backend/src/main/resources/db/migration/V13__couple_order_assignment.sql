alter table order_notifications add column handler_user_id bigint null;
alter table shop_orders add column handler_user_id bigint null;
alter table order_notification_items add column item_kind varchar(16) null;

create index idx_menu_orders_handler_status on order_notifications(handler_user_id, status, created_at);
create index idx_shop_orders_handler_status on shop_orders(handler_user_id, status, created_at);
