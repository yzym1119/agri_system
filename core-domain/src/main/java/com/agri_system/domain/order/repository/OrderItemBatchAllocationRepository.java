package com.agri_system.domain.order.repository;

import com.agri_system.domain.order.model.OrderItemBatchAllocation;

import java.math.BigDecimal;
import java.util.List;

/** 订单行批次归属事实仓储。 */
public interface OrderItemBatchAllocationRepository {

    OrderItemBatchAllocation save(OrderItemBatchAllocation allocation);

    /** 按订单ID查询（JOIN order_item 取 order_id）。 */
    List<OrderItemBatchAllocation> findByOrderId(Long orderId);

    List<OrderItemBatchAllocation> findByRequestId(Long pickupRequestId);

    List<OrderItemBatchAllocation> findByOrderItemId(Long orderItemId);

    /**
     * 观察窗口内按商品的批次成本口径毛利汇总（单次分组查询；无归属的商品不返回）。
     *
     * @param days    观察窗口天数
     * @param storeId 门店ID（null=不按门店过滤）
     */
    List<WindowMargin> sumWindowMarginByProduct(int days, Long storeId);

    /** 观察窗口内单个商品的毛利汇总：销售额、批次成本、订单数。 */
    record WindowMargin(Long productId, BigDecimal revenue, BigDecimal totalCost, Integer orderCount) {
    }
}
