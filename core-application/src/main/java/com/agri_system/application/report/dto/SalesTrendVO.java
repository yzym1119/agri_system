package com.agri_system.application.report.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.ExcelIgnore;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 销售趋势VO（支持Excel导出）：按原订单创建日归集净应结算减累计完成退货，包含跨期退货。
 */
@Data
public class SalesTrendVO {

    /** 历史事实无法核对或成本缺失时的明确提示；金额不猜补，成本及毛利应展示待核对。 */
    @ExcelIgnore
    private java.util.List<String> costWarnings = new java.util.ArrayList<>();

    @ExcelProperty("日期")
    private LocalDate date;

    @ExcelProperty("销售额")
    private BigDecimal saleAmount;

    @ExcelProperty("成本额")
    private BigDecimal costAmount;

    @ExcelProperty("毛利额")
    private BigDecimal profit;

    @ExcelProperty("毛利率(%)")
    private BigDecimal profitRate;

    // 实际/估算/成本缺失严格隔离；成本及销售均冲减原订单已完成退货。
    // costAmount为已知实际与估算之和；costWarnings非空时总成本和总毛利待核对。

    /** 实际成本口径销售额（元）：全部批次归属证据为扫描/确认/规则归属。 */
    @ExcelProperty("实际成本销售额")
    private BigDecimal actualSaleAmount;

    /** 实际成本额（元）：Σ 批次归属成本。 */
    @ExcelProperty("实际成本额")
    private BigDecimal actualCostAmount;

    /** 实际毛利额（元）= 实际成本销售额 - 实际成本额。 */
    @ExcelProperty("实际毛利额")
    private BigDecimal actualProfit;

    /** 估算成本口径销售额（元）：历史快照估算或证据不完整，绝不并入实际口径。 */
    @ExcelProperty("估算成本销售额")
    private BigDecimal estimatedSaleAmount;

    /** 估算成本额（元）：历史 cost_price_snapshot × 数量。 */
    @ExcelProperty("估算成本额")
    private BigDecimal estimatedCostAmount;

    /** 估算毛利额（元）= 估算成本销售额 - 估算成本额，仅表示参考值。 */
    @ExcelProperty("估算毛利额")
    private BigDecimal estimatedProfit;

    /** 成本缺失销售额（元）：既无实际成本也无历史快照，无法计算任何成本。 */
    @ExcelProperty("成本缺失销售额")
    private BigDecimal costUnavailableSaleAmount;

    /** 追加列保留原有导出列序。 */
    @ExcelProperty("成本核对状态")
    private String costStatus;
}




