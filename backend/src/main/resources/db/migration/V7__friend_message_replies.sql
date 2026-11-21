alter table friend_messages
    add column reply_to_message_id bigint null after media_url,
    add column reply_to_sender_id bigint null after reply_to_message_id,
    add column reply_to_type varchar(20) null after reply_to_sender_id,
    add column reply_to_preview varchar(300) null after reply_to_type;

create index idx_friend_messages_reply
    on friend_messages(reply_to_message_id);
