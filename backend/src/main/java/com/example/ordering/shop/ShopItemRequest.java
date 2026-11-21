package com.example.ordering.shop;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ShopItemRequest(
        @NotBlank @Size(max = 80) String name,
        @Size(max = 255) String description,
        @Size(max = 600) String imageUrl,
        @Size(max = 20) String category,
        @NotNull @DecimalMin("0.01") BigDecimal price
) {
}
