package com.agri_system.domain.order.query;

import java.time.LocalDateTime;

/** 销售报表只读入口，延续原门店与Flex租户范围；不提供开单或退款能力。 */
public interface SalesReportQuery {
    SalesDashboardFacts dashboardFacts(Long storeId, LocalDateTime start, LocalDateTime end);
    SalesTrendSource trendSource(LocalDateTime start, LocalDateTime end, Long storeId);
}
