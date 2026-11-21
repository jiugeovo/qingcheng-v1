package com.example.ordering.friend;

import java.time.LocalDateTime;

public record FriendMessageResponse(
        Long id,
        Long senderId,
        Long receiverId,
        String content,
        FriendMessageType type,
        String mediaUrl,
        Long replyToMessageId,
        Long replyToSenderId,
        FriendMessageType replyToType,
        String replyToPreview,
        LocalDateTime createdAt,
        LocalDateTime readAt,
        boolean mine
) {
}
