package com.agri_system.application.order.service;

import com.agri_system.domain.order.model.Order;
import com.agri_system.domain.order.model.OrderDelivery;
import org.springframework.util.StringUtils;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

/** 查询展示与原订单锁下写入口共享的交付内容摘要；不授予交付权限、不存储请求结果。 */
final class OrderDeliveryConfirmation {
    private OrderDeliveryConfirmation() {}
    static String key(Order order, OrderDelivery delivery,
            List<com.agri_system.domain.order.model.DeliveryItemQuantity> items) {
        if (items == null || items.isEmpty() || items.stream().anyMatch(item -> item.getOrderItemId() == null
                || item.getQuantity() == null || item.getQuantity().signum() <= 0 || !StringUtils.hasText(item.getUnit()))) return null;
        // 仅绑定本次所见内容，不是新业务身份或存储来源；签收时在原订单锁内重算。
        var parts = new ArrayList<String>();
        parts.add(String.valueOf(order.getId())); parts.add(String.valueOf(order.getOrderStatus()));
        parts.add(String.valueOf(delivery.getDeliveryType())); parts.add(String.valueOf(delivery.getDeliveryStatus()));
        parts.add(String.valueOf(delivery.getDeliveryStaffId()));
        parts.add(String.valueOf(delivery.getAssignmentVersion()));
        parts.add(String.valueOf(order.getReceiverName())); parts.add(String.valueOf(order.getReceiverPhone()));
        parts.add(String.valueOf(order.getReceiverAddress()));
        items.stream().sorted(java.util.Comparator.comparing(com.agri_system.domain.order.model.DeliveryItemQuantity::getOrderItemId)
                .thenComparing(com.agri_system.domain.order.model.DeliveryItemQuantity::getUnit)).forEach(item -> {
            parts.add(String.valueOf(item.getOrderItemId())); parts.add(item.getProductName());
            parts.add(item.getQuantity().stripTrailingZeros().toPlainString()); parts.add(item.getUnit());
        });
        // 长度前缀防止商品名或地址里的分隔符造成相同摘要。
        return hash(parts.stream().map(value -> value == null ? "-1:" : value.length() + ":" + value)
                .collect(Collectors.joining()));
    }

    private static String hash(String text) {
        try {return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
}
