package com.agri_system.application.order.service;

import com.agri_system.domain.after_sales.repository.AfterSalesRepository;
import com.agri_system.domain.after_sales.repository.AfterSalesItemRepository;
import java.math.BigDecimal;
import java.util.List;

/** 详情与正式退款共用的原售后数量/金额事实读取；无写入、缓存或事务边界。 */
final class OrderRefundHistory {
    private OrderRefundHistory() {}
    static java.util.Map<Long, RefundAggregate> load(Long orderId,Long excludedAfterSalesId, AfterSalesRepository afterSalesRepository, AfterSalesItemRepository afterSalesItemRepository) {
        java.util.Map<Long, RefundAggregate> aggregates = new java.util.HashMap<>();
        List<com.agri_system.domain.after_sales.model.AfterSales> afterSalesList = afterSalesRepository.findByOrderId(orderId);
        if (afterSalesList == null || afterSalesList.isEmpty()) {
            return aggregates;
        }

        for (com.agri_system.domain.after_sales.model.AfterSales afterSales : afterSalesList) {
            if (afterSales == null || java.util.Objects.equals(afterSales.getId(),excludedAfterSalesId)
                    || afterSales.getStatus() == com.agri_system.domain.after_sales.model.AfterSales.STATUS_REJECTED) {
                continue;
            }
            List<com.agri_system.domain.after_sales.model.AfterSalesItem> refundItems =
                    afterSalesItemRepository.findByAfterSalesId(afterSales.getId());
            if (refundItems == null || refundItems.isEmpty()) {
                continue;
            }
            for (com.agri_system.domain.after_sales.model.AfterSalesItem refundItem : refundItems) {
                if (refundItem == null || refundItem.getOrderItemId() == null) {
                    continue;
                }
                RefundAggregate aggregate = aggregates.computeIfAbsent(refundItem.getOrderItemId(),
                        key -> new RefundAggregate());
                aggregate.quantity = aggregate.quantity.add(defaultAmount(refundItem.getQuantity()));
                aggregate.amount = aggregate.amount.add(defaultAmount(refundItem.getRefundAmount()));
            }
        }
        return aggregates;
    }

    static final class RefundAggregate {
        BigDecimal quantity = BigDecimal.ZERO;
        BigDecimal amount = BigDecimal.ZERO;
    }

    private static BigDecimal defaultAmount(BigDecimal value) { return value != null ? value : BigDecimal.ZERO; }
}
