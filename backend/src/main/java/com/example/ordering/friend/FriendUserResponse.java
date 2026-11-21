package com.example.ordering.friend;

import com.example.ordering.user.UserAccount;
import com.example.ordering.user.UserRole;

public record FriendUserResponse(
        Long id,
        String username,
        String nickname,
        UserRole role
) {

    public static FriendUserResponse from(UserAccount user) {
        return new FriendUserResponse(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getRole()
        );
    }
}
