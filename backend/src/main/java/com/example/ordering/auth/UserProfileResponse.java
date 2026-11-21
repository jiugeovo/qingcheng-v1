package com.example.ordering.auth;

import com.example.ordering.couple.CoupleAccess;
import com.example.ordering.user.UserAccount;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.example.ordering.user.UserRole;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record UserProfileResponse(
        Long id,
        String username,
        String nickname,
        String phone,
        UserRole role,
        boolean enabled,
        BigDecimal qingchengCoins,
        LocalDate lastCheckInDate,
        LocalDateTime createdAt,
        LocalDateTime lastLoginAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) CoupleAccess.Profile couple
) {

    public static UserProfileResponse from(UserAccount user) {
        return from(user, null);
    }

    public static UserProfileResponse from(UserAccount user, CoupleAccess.Profile couple) {
        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getPhone(),
                user.getRole(),
                user.isEnabled(),
                user.getQingchengCoins(),
                user.getLastCheckInDate(),
                user.getCreatedAt(),
                user.getLastLoginAt(),
                couple
        );
    }
}
