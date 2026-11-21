package com.example.ordering.order;

import com.example.ordering.common.ApiResponse;
import com.example.ordering.capability.CapabilityExecutionContext;
import com.example.ordering.capability.CapabilityRegistry;
import com.example.ordering.capability.OrderCreateCapability;
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
public class OrderController {

    private final OrderService orderService;
    private final CapabilityRegistry capabilities;

    public OrderController(OrderService orderService, CapabilityRegistry capabilities) {
        this.orderService = orderService;
        this.capabilities = capabilities;
    }

    @PostMapping("/api/orders")
    public ApiResponse<OrderNotification> createOrder(
            Authentication authentication,
            @Valid @RequestBody CreateOrderRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        CapabilityExecutionContext context = CapabilityExecutionContext.from(authentication);
        Map<String, Object> input = new HashMap<>();
        input.put("items", request.items().stream().map(item -> item == null ? null :
                Map.of("menuItemId", item.menuItemId(), "quantity", item.quantity())).toList());
        if (request.remark() != null) {
            input.put("remark", request.remark());
        }
        if (idempotencyKey != null) input.put("idempotencyKey", idempotencyKey);
        Map<String, Object> result = capabilities.execute(OrderCreateCapability.ID, Map.copyOf(input), context);
        return ApiResponse.ok("订单已提交", OrderNotification.class.cast(result.get("order")));
    }

    @GetMapping("/api/orders/me")
    public ApiResponse<List<OrderNotification>> listMyOrders(Authentication authentication) {
        UserAccount user = (UserAccount) authentication.getPrincipal();
        return ApiResponse.ok(orderService.listMyOrders(user));
    }

    @GetMapping("/api/orders/received")
    @PreAuthorize("hasAuthority('COUPLE_MEMBER')")
    public ApiResponse<List<OrderNotification>> listReceivedOrders(Authentication authentication) {
        return ApiResponse.ok(orderService.listReceivedOrders((UserAccount) authentication.getPrincipal()));
    }

    @PutMapping("/api/orders/{id}/status")
    @PreAuthorize("hasAuthority('COUPLE_MEMBER')")
    public ApiResponse<OrderNotification> act(Authentication authentication, @PathVariable Long id,
                                     @Valid @RequestBody OrderActionRequest request) {
        return ApiResponse.ok(orderService.act(id, request.action(), (UserAccount) authentication.getPrincipal()));
    }

    @GetMapping("/api/admin/orders")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<List<OrderNotification>> listNotifications() {
        return ApiResponse.ok(orderService.listNotifications());
    }

    @PutMapping("/api/admin/orders/{id}/status")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<OrderNotification> updateStatus(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrderStatusRequest request
    ) {
        UserAccount user = (UserAccount) authentication.getPrincipal();
        return ApiResponse.ok("订单状态已更新", orderService.updateStatus(id, request.status(), user));
    }

    @DeleteMapping("/api/admin/orders/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<Void> deleteOrder(Authentication authentication, @PathVariable Long id) {
        UserAccount user = (UserAccount) authentication.getPrincipal();
        orderService.deleteOrder(id, user);
        return ApiResponse.ok("\u8ba2\u5355\u5df2\u5220\u9664", null);
    }

}
