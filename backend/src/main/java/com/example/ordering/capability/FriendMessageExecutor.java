package com.example.ordering.capability;

import com.example.ordering.friend.FriendMessageRequest;
import com.example.ordering.friend.FriendService;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public final class FriendMessageExecutor implements CapabilityExecutor {
    private final FriendService service;
    private final Validator validator;

    public FriendMessageExecutor(FriendService service, Validator validator) {
        this.service = service;
        this.validator = validator;
    }

    @Override public String capabilityId() { return "friend.message"; }

    @Override public Map<String, Object> execute(Map<String, Object> input, CapabilityExecutionContext context) {
        Number replyId = (Number) input.get("replyToMessageId");
        FriendMessageRequest request = new FriendMessageRequest((String) input.get("content"),
                replyId == null ? null : replyId.longValue());
        var violations = validator.validate(request);
        if (!violations.isEmpty()) throw new ConstraintViolationException(violations);
        return Map.of("message", service.sendMessage(context.toUserAccount(),
                ((Number) input.get("friendUserId")).longValue(), request));
    }
}
