package com.example.ordering.shop;


import java.math.BigDecimal;

public class ShopOrderLineEntity {

    private Long shopItemId;

    private String name;

    private int quantity;

    private BigDecimal unitPrice = BigDecimal.ZERO;

    private BigDecimal subtotal = BigDecimal.ZERO;

    public Long getShopItemId() {
        return shopItemId;
    }

    public void setShopItemId(Long shopItemId) {
        this.shopItemId = shopItemId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice == null ? BigDecimal.ZERO : unitPrice;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal == null ? BigDecimal.ZERO : subtotal;
    }
}
