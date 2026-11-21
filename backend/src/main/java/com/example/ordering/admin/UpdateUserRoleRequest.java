package com.example.ordering.admin;

import com.example.ordering.user.UserRole;
import jakarta.validation.constraints.NotNull;

public record UpdateUserRoleRequest(@NotNull UserRole role) {
}
