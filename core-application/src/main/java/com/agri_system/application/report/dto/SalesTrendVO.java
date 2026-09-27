package com.agri_system.application.report.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 销售趋势VO（支持Excel导出）
 */
@Data
public class SalesTrendVO {

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

    // ---- 零售事实链 Task6：实际/估算/成本缺失三口径隔离，估算绝不并入实际（legacy 字段口径不变：
    //      costAmount = actual + estimated，profit/profitRate 公式不变，未激活 SKU 数值与既有实现一致） ----

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
}




