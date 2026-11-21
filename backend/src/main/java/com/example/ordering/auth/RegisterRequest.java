package com.example.ordering.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank
        @Size(min = 3, max = 32)
        @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "只能包含字母、数字和下划线")
        String username,

        @NotBlank
        @Size(min = 6, max = 64)
        String password,

        @NotBlank
        @Size(max = 64)
        String nickname,

        @Size(max = 20)
        String phone
) {
}
