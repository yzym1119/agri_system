package com.agri_system.domain.customer.query;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 客户分析所需的只读资料，不携带钱包、身份证或其他客户档案字段。 */
public record CustomerReportRow(Long customerId, String name, String phone,
                                BigDecimal totalOrderAmount, LocalDateTime lastOrderTime) { }
