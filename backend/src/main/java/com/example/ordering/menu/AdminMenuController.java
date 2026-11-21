package com.example.ordering.menu;

import com.example.ordering.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN') or hasAuthority('COUPLE_MEMBER')")
public class AdminMenuController {
    private static final List<String> CATEGORIES = List.of("炖汤", "炒菜", "炖菜", "主食", "甜品", "炸货", "猪猪商店");

    private final MenuItemRepository menuItemRepository;

    public AdminMenuController(MenuItemRepository menuItemRepository) {
        this.menuItemRepository = menuItemRepository;
    }

    @GetMapping({"/api/admin/menu-items", "/api/menu-items/manage"})
    @Transactional(readOnly = true)
    public ApiResponse<List<MenuItem>> listAllMenuItems() {
        return ApiResponse.ok(menuItemRepository.findAll());
    }

    @PostMapping({"/api/admin/menu-items", "/api/menu-items"})
    @Transactional
    public ApiResponse<MenuItem> createMenuItem(@Valid @RequestBody MenuItemRequest request) {
        MenuItem item = new MenuItem();
        fillMenuItem(item, request);
        return ApiResponse.ok("新增菜品成功", menuItemRepository.save(item));
    }

    @PutMapping({"/api/admin/menu-items/{id}", "/api/menu-items/{id}"})
    @Transactional
    public ApiResponse<MenuItem> updateMenuItem(@PathVariable Long id, @Valid @RequestBody MenuItemRequest request) {
        MenuItem item = menuItemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("菜品不存在"));
        fillMenuItem(item, request);
        return ApiResponse.ok("更新菜品成功", menuItemRepository.save(item));
    }

    @DeleteMapping({"/api/admin/menu-items/{id}", "/api/menu-items/{id}"})
    @Transactional
    public ApiResponse<Void> deleteMenuItem(@PathVariable Long id) {
        if (!menuItemRepository.existsById(id)) {
            throw new IllegalArgumentException("菜品不存在");
        }
        menuItemRepository.deleteById(id);
        return ApiResponse.ok("删除菜品成功", null);
    }

    private void fillMenuItem(MenuItem item, MenuItemRequest request) {
        item.setName(request.name().trim());
        item.setDescription(request.description() == null ? null : request.description().trim());
        item.setImageUrl(request.imageUrl() == null ? null : request.imageUrl().trim());
        item.setCategory(normalizeCategory(request.category()));
        item.setPrice(request.price());
        item.setAvailable(request.available() == null || request.available());
    }

    private String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return "炒菜";
        }
        return category.trim();
    }
}
