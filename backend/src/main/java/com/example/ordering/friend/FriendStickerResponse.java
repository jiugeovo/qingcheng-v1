package com.example.ordering.friend;

import java.time.LocalDateTime;

public record FriendStickerResponse(
        Long id,
        String imageUrl,
        LocalDateTime createdAt
) {
    static FriendStickerResponse from(FriendStickerEntity sticker) {
        return new FriendStickerResponse(
                sticker.getId(),
                "/api/friends/media/stickers/" + sticker.getId() + "/content",
                sticker.getCreatedAt()
        );
    }
}
