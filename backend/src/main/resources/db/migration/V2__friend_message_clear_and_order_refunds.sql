set @column_exists := (
    select count(*)
    from information_schema.columns
    where table_schema = database()
      and table_name = 'friendships'
      and column_name = 'requester_cleared_messages_at'
);
set @statement := if(
    @column_exists = 0,
    'alter table friendships add column requester_cleared_messages_at datetime(6) null',
    'select 1'
);
prepare migration_statement from @statement;
execute migration_statement;
deallocate prepare migration_statement;

set @column_exists := (
    select count(*)
    from information_schema.columns
    where table_schema = database()
      and table_name = 'friendships'
      and column_name = 'addressee_cleared_messages_at'
);
set @statement := if(
    @column_exists = 0,
    'alter table friendships add column addressee_cleared_messages_at datetime(6) null',
    'select 1'
);
prepare migration_statement from @statement;
execute migration_statement;
deallocate prepare migration_statement;

alter table wallet_transactions modify type enum(
    'ADMIN_ADJUSTMENT',
    'CHECK_IN',
    'MENU_ORDER_PAYMENT',
    'MENU_ORDER_REFUND',
    'MENU_ORDER_REWARD',
    'NICKNAME_CHANGE',
    'SHOP_ORDER_PAYMENT',
    'SHOP_ORDER_REFUND',
    'SHOP_ORDER_REWARD'
) not null;
