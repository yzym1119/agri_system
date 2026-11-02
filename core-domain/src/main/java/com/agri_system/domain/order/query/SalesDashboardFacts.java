package com.agri_system.domain.order.query;

import java.math.BigDecimal;

/** 工作台原成交/退货及交付待办的只读聚合结果，不代表现金流。 */
public record SalesDashboardFacts(BigDecimal gmv, BigDecimal netSales, Long orderCount,
                                  Long pendingAfterSalesCount, Long pendingDeliveryCount) { }
