package com.example.ordering.friend;

import java.util.List;

public record FriendCenterResponse(
        List<FriendResponse> friends,
        List<FriendRequestResponse> incomingRequests,
        List<FriendRequestResponse> outgoingRequests,
        long unreadCount
) {
}
