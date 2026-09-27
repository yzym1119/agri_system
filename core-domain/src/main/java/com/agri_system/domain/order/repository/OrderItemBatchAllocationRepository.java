package com.agri_system.domain.order.repository;

import com.agri_system.domain.order.model.OrderItemBatchAllocation;

import java.util.List;

/** 订单行批次归属事实仓储。 */
public interface OrderItemBatchAllocationRepository {

    OrderItemBatchAllocation save(OrderItemBatchAllocation allocation);

    /** 按订单ID查询（JOIN order_item 取 order_id）。 */
    List<OrderItemBatchAllocation> findByOrderId(Long orderId);

    List<OrderItemBatchAllocation> findByRequestId(Long pickupRequestId);

    List<OrderItemBatchAllocation> findByOrderItemId(Long orderItemId);
}
