package com.example.ordering.config;

import com.example.ordering.menu.MenuItem;
import com.example.ordering.menu.MenuItemRepository;
import com.example.ordering.shop.ShopItem;
import com.example.ordering.shop.ShopItemRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Set;

@Component
@ConditionalOnProperty(name = "app.couple.mode", havingValue = "LEGACY", matchIfMissing = true)
@Order(2)
public class DemoDataInitializer implements CommandLineRunner {

    private final MenuItemRepository menuItemRepository;
    private final ShopItemRepository shopItemRepository;

    public DemoDataInitializer(MenuItemRepository menuItemRepository, ShopItemRepository shopItemRepository) {
        this.menuItemRepository = menuItemRepository;
        this.shopItemRepository = shopItemRepository;
    }

    @Override
    public void run(String... args) {
        normalizeMenuImages();
        ensureDefaultMenuItems();
        ensureDefaultShopItems();
    }

    private void normalizeMenuImages() {
        menuItemRepository.findAll().forEach(item -> {
            if (item.getImageUrl() == null || item.getImageUrl().isBlank()) {
                item.setImageUrl(resolveImageUrl(item.getName()));
                menuItemRepository.save(item);
            } else if (item.getImageUrl().startsWith("/images/")) {
                item.setImageUrl(item.getImageUrl().substring(1));
                menuItemRepository.save(item);
            }
        });
    }

    private void ensureDefaultMenuItems() {
        if (!menuItemRepository.existsByName("土豆红烧肉")) {
            createMenuItem("土豆红烧肉", "软糯土豆配红烧肉，偏家常、很下饭。", "32.00", "images/beef.svg");
        }
        ensureMenuItem("初级不生气卡", "", "20.00", "uploads/shop/use_item_not_angry_card.png", "猪猪商店");
        ensureMenuItem("中级不生气卡", "", "50.00", "uploads/shop/use_item_not_angry_card_2.png", "猪猪商店");
        ensureMenuItem("高级不生气卡", "", "80.00", "uploads/shop/use_item_not_angry_card_3.png", "猪猪商店");
    }

    private void ensureDefaultShopItems() {
        removePiggyCardsFromShopItems();
        removeLegacyDefaultShopItems();
        ensureShopItem("羊肉卷饼", "", "uploads/shop/use_item_lamb_burrito.png", "宝宝商店", "200.00");
        ensureShopItem("原味乐事薯片", "", "uploads/shop/use_item_original_lay_potato_chips.png", "宝宝商店", "53.00");
        ensureShopItem("麦乐鸡块", "", "uploads/shop/use_item_chicken_mcnuggets.png", "宝宝商店", "100.00");
        ensureShopItem("娃哈哈AD钙奶", "", "uploads/shop/use_item_wahaha_ad_calcium_milk.png", "宝宝商店", "20.00");
        ensureShopItem("芋圆无糖奶绿", "", "uploads/shop/use_item_taro_round_sugar_free_milk_green.png", "宝宝商店", "140.00");
        ensureShopItem("小酒", "", "uploads/shop/use_item_alcohol.png", "宝宝商店", "300.00");
    }

    private void createMenuItem(String name, String description, String price, String imageUrl) {
        MenuItem item = new MenuItem();
        item.setName(name);
        item.setDescription(description);
        item.setImageUrl(imageUrl);
        item.setPrice(new BigDecimal(price));
        item.setAvailable(true);
        menuItemRepository.save(item);
    }

    private void ensureMenuItem(String name, String description, String price, String imageUrl, String category) {
        MenuItem item = menuItemRepository.findByName(name).orElseGet(MenuItem::new);
        item.setName(name);
        item.setDescription(description);
        item.setImageUrl(imageUrl);
        item.setCategory(category);
        item.setPrice(new BigDecimal(price));
        item.setAvailable(true);
        menuItemRepository.save(item);
    }

    private void createShopItem(String name, String description, String imageUrl) {
        ShopItem item = new ShopItem();
        item.setName(name);
        item.setDescription(description);
        item.setImageUrl(imageUrl);
        shopItemRepository.save(item);
    }

    private void ensureShopItem(String name, String description, String imageUrl, String category, String price) {
        ShopItem item = shopItemRepository.findByName(name).orElseGet(ShopItem::new);
        item.setName(name);
        item.setDescription(description);
        item.setImageUrl(imageUrl);
        item.setCategory(category);
        item.setPrice(new BigDecimal(price));
        shopItemRepository.save(item);
    }

    private void removePiggyCardsFromShopItems() {
        Set<String> piggyCards = Set.of("初级不生气卡", "中级不生气卡", "高级不生气卡");
        shopItemRepository.findAll().stream()
                .filter(item -> piggyCards.contains(item.getName()))
                .forEach(shopItemRepository::delete);
    }

    private void removeLegacyDefaultShopItems() {
        shopItemRepository.findByName("乐事薯片")
                .filter(item -> "images/custom-dish.svg".equals(item.getImageUrl()))
                .filter(item -> item.getDescription() != null && item.getDescription().contains("默认零食产品"))
                .ifPresent(shopItemRepository::delete);
    }

    private String resolveImageUrl(String name) {
        if (name == null) {
            return "images/custom-dish.svg";
        }
        if (name.contains("土豆") || name.contains("红烧")) {
            return "images/beef.svg";
        }
        if (name.contains("薯片") || name.contains("乐事")) {
            return "images/custom-dish.svg";
        }
        if (name.contains("番茄")) {
            return "images/tomato-egg.svg";
        }
        if (name.contains("汤")) {
            return "images/soup.svg";
        }
        return "images/custom-dish.svg";
    }
}
