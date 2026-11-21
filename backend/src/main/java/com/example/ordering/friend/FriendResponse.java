package com.example.ordering.friend;

import java.time.LocalDateTime;

public record FriendResponse(
        Long friendshipId,
        FriendUserResponse user,
        long unreadCount,
        String lastMessage,
        LocalDateTime lastMessageAt,
        LocalDateTime friendsSince
) {
}
