package com.example.ordering.shop;

import com.example.ordering.common.ApiResponse;
import com.example.ordering.capability.CapabilityExecutionContext;
import com.example.ordering.capability.CapabilityRegistry;
import com.example.ordering.capability.ShopOrderCreateCapability;
import com.example.ordering.order.OrderStatus;
import com.example.ordering.order.OrderActionRequest;
import com.example.ordering.order.UpdateOrderStatusRequest;
import com.example.ordering.user.UserAccount;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

@RestController
public class ShopOrderController {

    private final ShopOrderService shopOrderService;
    private final CapabilityRegistry capabilities;

    public ShopOrderController(ShopOrderService shopOrderService, CapabilityRegistry capabilities) {
        this.shopOrderService = shopOrderService;
        this.capabilities = capabilities;
    }

    @PostMapping("/api/shop-orders")
    public ApiResponse<ShopOrder> createOrder(
            Authentication authentication,
            @Valid @RequestBody CreateShopOrderRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        CapabilityExecutionContext context = CapabilityExecutionContext.from(authentication);
        Map<String, Object> input = new HashMap<>();
        input.put("items", request.items().stream().map(item -> item == null ? null :
                Map.of("shopItemId", item.shopItemId(), "quantity", item.quantity())).toList());
        if (request.remark() != null) {
            input.put("remark", request.remark());
        }
        if (idempotencyKey != null) input.put("idempotencyKey", idempotencyKey);
        Map<String, Object> result = capabilities.execute(ShopOrderCreateCapability.ID, Map.copyOf(input), context);
        return ApiResponse.ok("Shop order created", ShopOrder.class.cast(result.get("order")));
    }

    @GetMapping("/api/shop-orders/me")
    public ApiResponse<List<ShopOrder>> listMyOrders(Authentication authentication) {
        UserAccount user = (UserAccount) authentication.getPrincipal();
        return ApiResponse.ok(shopOrderService.listMyOrders(user));
    }

    @GetMapping("/api/shop-orders/received")
    @PreAuthorize("hasAuthority('COUPLE_MEMBER')")
    public ApiResponse<List<ShopOrder>> listReceivedOrders(Authentication authentication) {
        return ApiResponse.ok(shopOrderService.listReceivedOrders((UserAccount) authentication.getPrincipal()));
    }

    @PutMapping("/api/shop-orders/{id}/status")
    @PreAuthorize("hasAuthority('COUPLE_MEMBER')")
    public ApiResponse<ShopOrder> act(Authentication authentication, @PathVariable Long id,
                                     @Valid @RequestBody OrderActionRequest request) {
        return ApiResponse.ok(shopOrderService.act(id, request.action(), (UserAccount) authentication.getPrincipal()));
    }

    @GetMapping("/api/admin/shop-orders")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<List<ShopOrder>> listOrders() {
        return ApiResponse.ok(shopOrderService.listOrders());
    }

    @PutMapping("/api/admin/shop-orders/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<ShopOrder> updateStatus(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrderStatusRequest request
    ) {
        UserAccount user = (UserAccount) authentication.getPrincipal();
        OrderStatus status = request.status();
        return ApiResponse.ok("Shop order status updated", shopOrderService.updateStatus(id, status, user));
    }

    @DeleteMapping("/api/admin/shop-orders/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<Void> deleteOrder(Authentication authentication, @PathVariable Long id) {
        UserAccount user = (UserAccount) authentication.getPrincipal();
        shopOrderService.deleteOrder(id, user);
        return ApiResponse.ok("\u5b9d\u5b9d\u5546\u5e97\u8ba2\u5355\u5df2\u5220\u9664", null);
    }

}
