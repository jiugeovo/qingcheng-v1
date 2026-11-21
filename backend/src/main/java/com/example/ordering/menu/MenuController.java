package com.example.ordering.menu;

import com.example.ordering.common.ApiResponse;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/menu-items")
public class MenuController {

    private final MenuItemRepository menuItemRepository;

    public MenuController(MenuItemRepository menuItemRepository) {
        this.menuItemRepository = menuItemRepository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ApiResponse<List<MenuItem>> listAvailableMenuItems() {
        return ApiResponse.ok(menuItemRepository.findByAvailableTrueOrderByCreatedAtDesc());
    }
}
