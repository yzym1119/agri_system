package com.agri_system.domain.order.repository;

import com.agri_system.domain.order.model.OrderItemPriceAudit;

import java.util.List;

/** 订单行价格审计事实仓储（追加写）。 */
public interface OrderItemPriceAuditRepository {

    OrderItemPriceAudit save(OrderItemPriceAudit audit);

    List<OrderItemPriceAudit> findByOrderItemId(Long orderItemId);
}
