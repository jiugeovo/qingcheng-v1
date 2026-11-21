package com.example.ordering.friend;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FriendMessageRequest(
        @NotBlank(message = "消息不能为空")
        @Size(max = 1000, message = "消息最多 1000 个字")
        String content,
        Long replyToMessageId
) {
}
