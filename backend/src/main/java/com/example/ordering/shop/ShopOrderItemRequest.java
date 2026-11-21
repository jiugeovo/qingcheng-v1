package com.example.ordering.shop;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ShopOrderItemRequest(
        @NotNull Long shopItemId,
        @Min(1) int quantity
) {
}
