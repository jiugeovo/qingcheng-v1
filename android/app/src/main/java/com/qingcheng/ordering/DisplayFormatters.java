package com.qingcheng.ordering;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

final class DisplayFormatters {
    private DisplayFormatters() {
    }

    static String orderLines(JSONArray items) {
        if (items == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(item.optString("name", "商品"))
                    .append(" x")
                    .append(item.optInt("quantity", 1))
                    .append(" = ")
                    .append(item.optString("subtotal", "0"));
        }
        return builder.toString();
    }

    static String coinAmount(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "0.00";
        }
        try {
            return String.format(Locale.US, "%.2f", Double.parseDouble(value));
        } catch (Exception ignored) {
            return value;
        }
    }

    static String shortDate(String value) {
        if (value == null || value.length() < 10) {
            return "";
        }
        return value.substring(5, 10);
    }

    static String signedAmount(double amount) {
        return (amount > 0 ? "+" : "") + String.format(Locale.US, "%.2f", amount);
    }

    static String shortTime(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String normalized = value.replace("T", " ");
        return normalized.substring(0, Math.min(16, normalized.length()));
    }

    static String walletTransactionType(String type) {
        return switch (type) {
            case "CHECK_IN" -> "签到奖励";
            case "MENU_ORDER_PAYMENT" -> "点菜消费";
            case "SHOP_ORDER_PAYMENT" -> "宝宝商店消费";
            case "MENU_ORDER_REWARD" -> "点菜完成奖励";
            case "SHOP_ORDER_REWARD" -> "商店完成奖励";
            case "NICKNAME_CHANGE" -> "修改昵称";
            case "ADMIN_ADJUSTMENT" -> "管理员调整";
            default -> "青程币变动";
        };
    }

    static String orderType(JSONObject order, boolean menu) {
        return switch (order.optString("orderKind")) {
            case "PIGGY_SHOP" -> "猪猪商店订单";
            case "MIXED_MENU" -> "点菜和猪猪商店订单";
            case "LEGACY_MENU" -> "点菜／猪猪商店旧订单";
            default -> menu ? "点菜订单" : "宝宝商店订单";
        };
    }

    static String storeOrderLabel(JSONObject order, boolean menu) {
        String kind = order.optString("orderKind");
        String store = "PIGGY_SHOP".equals(kind) ? "猪猪商店" : (!menu || "BABY_SHOP".equals(kind) ? "宝宝商店" : "");
        if (!store.isEmpty()) {
            String number = order.isNull("displayNumber") ? "" : order.optString("displayNumber", "").trim();
            return number.isEmpty() ? store + "订单" : store + " · " + number;
        }
        return "LEGACY_MENU".equals(kind) || "MIXED_MENU".equals(kind) ? orderType(order, true) : "";
    }

    static String orderStatus(String status) {
        return switch (status) {
            case "PENDING" -> "待处理";
            case "PREPARING" -> "处理中";
            case "COMPLETED" -> "已完成";
            case "CANCELLED" -> "已取消";
            default -> status;
        };
    }

    static String role(String role) {
        return switch (role) {
            case "SUPER_ADMIN" -> "超级管理员";
            case "ADMIN" -> "管理员";
            default -> "用户";
        };
    }

}
