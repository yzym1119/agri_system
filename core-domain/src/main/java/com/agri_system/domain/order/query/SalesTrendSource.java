package com.agri_system.domain.order.query;

import com.agri_system.domain.order.model.Order;
import com.agri_system.domain.order.model.OrderItem;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 销售趋势的只读输入；沿用订单/物料模型供原金额策略核验，元素不是可写报表账本。 */
public record SalesTrendSource(List<Order> orders, List<OrderItem> items,
                               Map<Long,BigDecimal> refundsByOrder,
                               Map<Long,SalesTrendReturnFacts> returnsByItem,
                               Set<Long> invalidRefundOrders) {
    public SalesTrendSource {
        orders=List.copyOf(orders);items=List.copyOf(items);
        refundsByOrder=Map.copyOf(refundsByOrder);returnsByItem=Map.copyOf(returnsByItem);
        invalidRefundOrders=Set.copyOf(invalidRefundOrders);
    }
}
