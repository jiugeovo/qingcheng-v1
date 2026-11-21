package com.example.ordering.shop;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateShopOrderRequest(
        @NotEmpty List<@Valid ShopOrderItemRequest> items,
        String remark
) {
}
