ALTER TABLE shop_orders ADD COLUMN display_number BIGINT NULL;
ALTER TABLE order_notifications ADD COLUMN display_number BIGINT NULL;

UPDATE shop_orders
SET display_number = (
    SELECT ranked.sequence_number
    FROM (
        SELECT id, ROW_NUMBER() OVER (ORDER BY created_at, id) AS sequence_number
        FROM shop_orders
    ) ranked
    WHERE ranked.id = shop_orders.id
);

UPDATE order_notifications
SET display_number = (
    SELECT ranked.sequence_number
    FROM (
        SELECT id, ROW_NUMBER() OVER (ORDER BY created_at, id) AS sequence_number
        FROM order_notifications
        WHERE id IN (
            SELECT order_notification_id
            FROM order_notification_items
            GROUP BY order_notification_id
            HAVING COUNT(*) = SUM(CASE WHEN item_kind = 'PIGGY_SHOP' THEN 1 ELSE 0 END)
        )
    ) ranked
    WHERE ranked.id = order_notifications.id
);

CREATE UNIQUE INDEX uk_shop_orders_display_number ON shop_orders(display_number);
CREATE UNIQUE INDEX uk_menu_orders_display_number ON order_notifications(display_number);

CREATE TABLE store_order_sequences (
    store_kind VARCHAR(16) NOT NULL PRIMARY KEY,
    last_number BIGINT NOT NULL
);

INSERT INTO store_order_sequences (store_kind, last_number)
SELECT 'BABY_SHOP', COALESCE(MAX(display_number), 0) FROM shop_orders;

INSERT INTO store_order_sequences (store_kind, last_number)
SELECT 'PIGGY_SHOP', COALESCE(MAX(display_number), 0) FROM order_notifications;
