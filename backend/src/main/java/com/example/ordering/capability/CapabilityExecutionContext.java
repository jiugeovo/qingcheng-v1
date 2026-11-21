package com.example.ordering.capability;

import com.example.ordering.user.UserRole;
import com.example.ordering.user.UserAccount;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

/** Immutable authenticated identity; never stores the mutable account or its credentials. */
public record CapabilityExecutionContext(long userId, String username, UserRole role, String clientIp, String nickname) {
    public CapabilityExecutionContext {
        if (userId <= 0) {
            throw new IllegalArgumentException("用户上下文无效");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("用户上下文无效");
        }
        if (role == null) {
            throw new IllegalArgumentException("用户角色无效");
        }
    }

    public CapabilityExecutionContext(long userId, String username, UserRole role) {
        this(userId, username, role, null);
    }

    public CapabilityExecutionContext(long userId, String username, UserRole role, String clientIp) {
        this(userId, username, role, clientIp, null);
    }

    public CapabilityExecutionContext(long userId, String username) {
        this(userId, username, UserRole.USER);
    }

    public static CapabilityExecutionContext from(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof UserAccount user)
                || !user.isEnabled() || user.getId() == null || user.getId() <= 0
                || user.getUsername() == null || user.getUsername().isBlank() || user.getRole() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        return new CapabilityExecutionContext(user.getId(), user.getUsername(), user.getRole(), null, user.getNickname());
    }

    /** Called only after the transport's trusted-proxy policy has resolved the address. */
    public CapabilityExecutionContext withClientIp(String address) {
        return new CapabilityExecutionContext(userId, username, role, address, nickname);
    }

    /** Adapter for existing services that reload account state by authenticated ID. */
    public UserAccount toUserAccount() {
        UserAccount identity = new UserAccount();
        identity.setId(userId);
        identity.setUsername(username);
        identity.setRole(role);
        identity.setNickname(nickname);
        return identity;
    }
}
