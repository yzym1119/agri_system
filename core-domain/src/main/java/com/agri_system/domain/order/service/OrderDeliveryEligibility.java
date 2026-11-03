package com.agri_system.domain.order.service;

import com.agri_system.domain.after_sales.repository.AfterSalesRepository;
import com.agri_system.domain.order.model.Order;
import com.agri_system.domain.order.model.OrderDelivery;
import com.agri_system.domain.order.repository.OrderRepository;
import java.util.Objects;

/** 查询与原锁下写入口共享的交付判断；自身不锁单、不缓存，也不代替写入口重新核对。 */
public final class OrderDeliveryEligibility {
    private OrderDeliveryEligibility() {}

    public static boolean hasProcessingAfterSales(Long orderId, AfterSalesRepository afterSales) {
        return afterSales.findByOrderId(orderId).stream()
                .anyMatch(fact -> Objects.equals(fact.getStatus(),10) || Objects.equals(fact.getStatus(),40));
    }

    public static boolean canDeliver(Order order, OrderDelivery delivery, boolean shipping,
                                     AfterSalesRepository afterSales, OrderRepository orders) {
        if (delivery == null || !Objects.equals(delivery.getDeliveryStatus(),shipping?2:3)) return false;
        Integer type=delivery.getDeliveryType();
        if (!Integer.valueOf(2).equals(type) && !Integer.valueOf(3).equals(type)) return false;
        Integer status=order.getOrderStatus();
        return (Objects.equals(status,shipping?4:5) || Objects.equals(status,9))
                && !hasProcessingAfterSales(order.getId(),afterSales)
                && orders.hasRemainingDeliveryQuantity(order.getId());
    }
}
