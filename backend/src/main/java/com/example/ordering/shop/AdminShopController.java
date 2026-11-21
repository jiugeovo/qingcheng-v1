package com.example.ordering.shop;

import com.example.ordering.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN') or hasAuthority('COUPLE_MEMBER')")
public class AdminShopController {

    private final ShopItemRepository shopItemRepository;

    public AdminShopController(ShopItemRepository shopItemRepository) {
        this.shopItemRepository = shopItemRepository;
    }

    @PostMapping({"/api/admin/shop-items", "/api/shop-items"})
    @Transactional
    public ApiResponse<ShopItem> createShopItem(@Valid @RequestBody ShopItemRequest request) {
        ShopItem item = new ShopItem();
        fillShopItem(item, request);
        return ApiResponse.ok("新增产品成功", shopItemRepository.save(item));
    }

    @PutMapping({"/api/admin/shop-items/{id}", "/api/shop-items/{id}"})
    @Transactional
    public ApiResponse<ShopItem> updateShopItem(@PathVariable Long id, @Valid @RequestBody ShopItemRequest request) {
        ShopItem item = shopItemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("产品不存在"));
        fillShopItem(item, request);
        return ApiResponse.ok("更新产品成功", shopItemRepository.save(item));
    }

    @DeleteMapping({"/api/admin/shop-items/{id}", "/api/shop-items/{id}"})
    @Transactional
    public ApiResponse<Void> deleteShopItem(@PathVariable Long id) {
        if (!shopItemRepository.existsById(id)) {
            throw new IllegalArgumentException("产品不存在");
        }
        shopItemRepository.deleteById(id);
        return ApiResponse.ok("删除产品成功", null);
    }

    private void fillShopItem(ShopItem item, ShopItemRequest request) {
        item.setName(request.name().trim());
        item.setDescription(request.description() == null ? null : request.description().trim());
        item.setImageUrl(request.imageUrl() == null ? null : request.imageUrl().trim());
        item.setCategory(request.category() == null || request.category().isBlank() ? "宝宝商店" : request.category().trim());
        if (request.price() == null) {
            throw new IllegalArgumentException("商品价格未设置");
        }
        item.setPrice(request.price());
    }
}
