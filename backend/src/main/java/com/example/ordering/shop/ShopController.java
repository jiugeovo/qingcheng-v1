package com.example.ordering.shop;

import com.example.ordering.common.ApiResponse;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/shop-items")
public class ShopController {

    private final ShopItemRepository shopItemRepository;

    public ShopController(ShopItemRepository shopItemRepository) {
        this.shopItemRepository = shopItemRepository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ApiResponse<List<ShopItem>> listShopItems() {
        return ApiResponse.ok(shopItemRepository.findAllByOrderByCreatedAtDesc());
    }
}
