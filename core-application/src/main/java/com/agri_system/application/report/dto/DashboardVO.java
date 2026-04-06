package com.agri_system.application.report.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 经营驾驶舱（今日）视图对象
 */
@Data
public class DashboardVO {

    /** 今日 GMV（含折扣前总额） */
    private BigDecimal gmv;

    /** 今日净销额（实收金额） */
    private BigDecimal netSales;

    /** 今日订单数 */
    private Long orderCount;

    /** 今日新增赊销额 */
    private BigDecimal newCreditAmount;

    /** 今日新增客户数 */
    private Long newCustomerCount;

    /** 待审核售后数 */
    private Long pendingAfterSalesCount;

    /** 待审核同城配送数 */
    private Long pendingDeliveryCount;

    /** 库存预警数（如库龄/临期等，由 Inventory 维度统计） */
    private Long inventoryAlertCount;
}





