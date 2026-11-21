package com.example.ordering.auth;

public record LoginResponse(String token, UserProfileResponse user) {
}
