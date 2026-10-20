package com.agri_system.application.report.dto;

import lombok.Data;
import java.math.BigDecimal;

/** 销售趋势一次批量读取的已完成售后与原批次回库事实，仅用于查询。 */
@Data
public class SalesTrendReturnFacts {
    private Long itemId;
    private Long orderId;
    private BigDecimal orderRefundAmount;
    private BigDecimal refundAmount;
    private BigDecimal returnedQuantity;
    private BigDecimal returnedBaseQuantity;
    private BigDecimal returnedCost;
}
