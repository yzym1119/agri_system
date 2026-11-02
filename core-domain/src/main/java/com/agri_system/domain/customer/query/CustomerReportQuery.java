package com.agri_system.domain.customer.query;

import java.time.LocalDateTime;
import java.util.List;

public interface CustomerReportQuery {
    /** 保留共享客户注册表的新增统计语义，客户本身没有门店字段。 */
    Long countCreatedBetween(LocalDateTime start, LocalDateTime end);
    List<CustomerReportRow> rankedCustomers(int limit, Long storeId);
    List<CustomerReportRow> churnCandidates(Long storeId, LocalDateTime deadline);
}
