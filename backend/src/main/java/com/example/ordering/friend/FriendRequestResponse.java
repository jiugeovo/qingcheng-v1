package com.example.ordering.friend;

import java.time.LocalDateTime;

public record FriendRequestResponse(
        Long friendshipId,
        FriendUserResponse user,
        String direction,
        FriendshipStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
