package com.example.ordering.capability;
import org.springframework.stereotype.Component;
import java.util.Map;
@Component public final class FriendMessageCapability implements Capability {
    public String id() { return "friend.message"; }
    public String name() { return "好友消息"; }
    public String description() { return "向已添加的好友发送文字消息"; }
    public String category() { return CapabilityCategory.SOCIAL.id(); }
    public String route() { return "/api/friends"; }
    public Map<String, Object> inputSchema() {
        return Map.of("friendUserId", Map.of("type", "integer", "required", true),
                "content", Map.of("type", "string", "required", true, "maxLength", 1000),
                "replyToMessageId", Map.of("type", "integer", "required", false));
    }
}
