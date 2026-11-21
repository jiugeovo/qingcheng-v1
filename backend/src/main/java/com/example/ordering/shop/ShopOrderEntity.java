package com.example.ordering.shop;

import com.example.ordering.order.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ShopOrderEntity {

    private Long id;

    private Long userId;
    private Long handlerUserId;
    private Long displayNumber;

    private String username;

    private String nickname;

    private List<ShopOrderLineEntity> items = new ArrayList<>();

    private int totalQuantity;

    private BigDecimal totalAmount = BigDecimal.ZERO;

    private String remark;

    private OrderStatus status = OrderStatus.PENDING;

    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt = LocalDateTime.now();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getHandlerUserId() {
        return handlerUserId;
    }

    public void setHandlerUserId(Long handlerUserId) {
        this.handlerUserId = handlerUserId;
    }

    public Long getDisplayNumber() {
        return displayNumber;
    }

    public void setDisplayNumber(Long displayNumber) {
        this.displayNumber = displayNumber;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public List<ShopOrderLineEntity> getItems() {
        return items;
    }

    public void setItems(List<ShopOrderLineEntity> items) {
        this.items = items;
    }

    public int getTotalQuantity() {
        return totalQuantity;
    }

    public void setTotalQuantity(int totalQuantity) {
        this.totalQuantity = totalQuantity;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount == null ? BigDecimal.ZERO : totalAmount;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
